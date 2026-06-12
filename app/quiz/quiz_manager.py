from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from time import time
from typing import Any

import discord

from app.api.satisfactorydle_api import SatisfactorydleAPI
from app.commands.embed_helper import (
    COLOR_ERROR,
    COLOR_INFO,
    COLOR_SUCCESS,
    COLOR_WARNING,
    add_field_if_present,
    apply_footer,
    has_value,
)
from app.i18n.message_manager import Messages
from app.quiz.session import QuizSession
from app.utils.string_utils import normalize


LOGGER = logging.getLogger(__name__)


@dataclass(slots=True)
class _QuizRuntime:
    session: QuizSession
    timeout_task: asyncio.Task[None]
    hint_task: asyncio.Task[None]


class QuizManager:
    QUIZ_TIMEOUT_SECONDS = 60
    QUIZ_HINT_SECONDS = 30

    def __init__(self, api: SatisfactorydleAPI) -> None:
        self.api = api
        self._active_quizzes: dict[int, _QuizRuntime] = {}
        self._channel_locks: dict[int, asyncio.Lock] = {}

    def has_active_quiz(self, channel_id: int) -> bool:
        return channel_id in self._active_quizzes

    async def start_quiz(
        self,
        guild_id: str,
        channel: discord.abc.Messageable,
        channel_id: int,
        starter_user_id: str,
        locale: str,
        quiz_id: int,
        entity: dict[str, Any],
        messages: Messages,
    ) -> None:
        if channel_id in self._active_quizzes:
            LOGGER.warning("[Quiz] start_quiz called while quiz already active in channel %s", channel_id)
            return

        answer = str(entity["name"])
        image_url = str(entity.get("image_url", ""))

        session = QuizSession(
            answer=answer,
            entity=entity,
            quiz_id=quiz_id,
            image_url=image_url,
            guild_id=guild_id,
            starter_user_id=starter_user_id,
            locale=locale,
        )

        timeout_task = asyncio.create_task(self._timeout_after(channel_id, channel, messages))
        hint_task = asyncio.create_task(self._hint_after(channel_id, channel, messages))

        self._active_quizzes[channel_id] = _QuizRuntime(session=session, timeout_task=timeout_task, hint_task=hint_task)
        LOGGER.info("[Quiz] Started in channel %s - answer: %s", channel_id, answer)

    async def handle_message(
        self,
        channel_id: int,
        content: str,
        author: discord.User | discord.Member,
        channel: discord.abc.Messageable,
        messages: Messages,
    ) -> None:
        if not content.strip():
            return

        runtime = self._active_quizzes.get(channel_id)
        if runtime is None:
            return

        if normalize(content) == normalize(runtime.session.answer):
            runtime.timeout_task.cancel()
            runtime.hint_task.cancel()
            await self._end_quiz(channel_id, channel, author, messages)

    async def _hint_after(self, channel_id: int, channel: discord.abc.Messageable, messages: Messages) -> None:
        try:
            await asyncio.sleep(self.QUIZ_HINT_SECONDS)
            await self._send_hint(channel_id, channel, messages)
        except asyncio.CancelledError:
            return

    async def _timeout_after(self, channel_id: int, channel: discord.abc.Messageable, messages: Messages) -> None:
        try:
            await asyncio.sleep(self.QUIZ_TIMEOUT_SECONDS)
            await self._end_quiz(channel_id, channel, None, messages)
        except asyncio.CancelledError:
            return

    async def _send_hint(self, channel_id: int, channel: discord.abc.Messageable, messages: Messages) -> None:
        runtime = self._active_quizzes.get(channel_id)
        if runtime is None:
            return

        entity = runtime.session.entity
        if not has_value(entity, "description"):
            return

        desc = str(entity["description"])
        if len(desc) > 200:
            desc = desc[:200] + "..."

        embed = discord.Embed(color=COLOR_WARNING, title=messages.get("quiz.hint_title"))
        embed.add_field(name=messages.get("field.description"), value=desc, inline=False)
        apply_footer(embed, messages.get("quiz.hint_footer"))
        await channel.send(embed=embed)

    async def _end_quiz(
        self,
        channel_id: int,
        channel: discord.abc.Messageable,
        winner: discord.User | discord.Member | None,
        messages: Messages,
    ) -> None:
        lock = self._channel_locks.setdefault(channel_id, asyncio.Lock())
        async with lock:
            runtime = self._active_quizzes.pop(channel_id, None)
            if runtime is None:
                return

            runtime.timeout_task.cancel()
            runtime.hint_task.cancel()

            session = runtime.session
            entity = session.entity
            name = str(entity["name"])
            elapsed = int(time() - session.start_time)

            if winner is not None:
                LOGGER.info(
                    "[Quiz] Channel %s - %s found \"%s\" in %ss",
                    channel_id,
                    winner.name,
                    name,
                    elapsed,
                )
                await self.api.quiz_complete(session.quiz_id, True, str(winner.id))
            else:
                LOGGER.info("[Quiz] Channel %s - timeout, answer was \"%s\"", channel_id, name)
                await self.api.quiz_complete(session.quiz_id, False, None)

            if winner is not None:
                embed = discord.Embed(
                    color=COLOR_SUCCESS,
                    title=messages.get("quiz.winner_title"),
                    description=messages.get(
                        "quiz.winner_description",
                        "user",
                        winner.mention,
                        "name",
                        name,
                        "time",
                        elapsed,
                    ),
                )
            else:
                embed = discord.Embed(
                    color=COLOR_ERROR,
                    title=messages.get("quiz.timeout_title"),
                    description=messages.get("quiz.timeout_description", "name", name),
                )

            if session.image_url:
                embed.set_thumbnail(url=session.image_url)

            apply_footer(embed, None)
            await channel.send(embed=embed)

            if winner is not None:
                await self._start_next_quiz(session, channel_id, channel, messages)

    async def _start_next_quiz(
        self,
        previous_session: QuizSession,
        channel_id: int,
        channel: discord.abc.Messageable,
        messages: Messages,
    ) -> None:
        try:
            result = await self.api.quiz_start(
                previous_session.guild_id,
                str(channel_id),
                previous_session.starter_user_id,
                previous_session.locale,
            )
            quiz_id = int(result["quiz_id"])
            entity = result["entity"]

            await self.start_quiz(
                guild_id=previous_session.guild_id,
                channel=channel,
                channel_id=channel_id,
                starter_user_id=previous_session.starter_user_id,
                locale=previous_session.locale,
                quiz_id=quiz_id,
                entity=entity,
                messages=messages,
            )

            embed = discord.Embed(
                color=COLOR_INFO,
                title=messages.get("quiz.title"),
                description=messages.get("quiz.description"),
            )
            add_field_if_present(embed, messages.get("field.category"), entity, "category", True)
            add_field_if_present(embed, messages.get("field.tier"), entity, "tier", True)
            add_field_if_present(embed, messages.get("field.form"), entity, "form", True)

            apply_footer(embed, messages.get("quiz.footer"))
            await channel.send(embed=embed)
        except Exception as exc:  # noqa: BLE001
            LOGGER.error("[Quiz] Failed to start next quiz in channel %s: %s", channel_id, exc)

    async def shutdown(self) -> None:
        for runtime in self._active_quizzes.values():
            runtime.timeout_task.cancel()
            runtime.hint_task.cancel()
        self._active_quizzes.clear()
