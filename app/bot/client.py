from __future__ import annotations

import logging

import discord
from discord import app_commands

from app.commands.handlers import CommandDependencies, register_sfdle_commands


LOGGER = logging.getLogger(__name__)


class SatisfactorydleBot(discord.Client):
    def __init__(self, deps: CommandDependencies) -> None:
        intents = discord.Intents.none()
        intents.guild_messages = True
        intents.message_content = True
        super().__init__(intents=intents)
        self.tree = app_commands.CommandTree(self)
        self.deps = deps

    async def setup_hook(self) -> None:
        register_sfdle_commands(self.tree, self.deps)
        synced = await self.tree.sync()
        LOGGER.info("Slash commands synced: %s", len(synced))

    async def on_ready(self) -> None:
        if self.user is None:
            return
        LOGGER.info("Bot is ready! Logged in as %s", self.user)

    async def on_message(self, message: discord.Message) -> None:
        if message.author.bot:
            return
        if message.guild is None:
            return

        channel_id = int(message.channel.id)
        if not self.deps.quiz_manager.has_active_quiz(channel_id):
            return

        config = await self.deps.guild_config_manager.get_config(str(message.guild.id))
        messages = self.deps.message_manager.for_locale(config.locale)

        await self.deps.quiz_manager.handle_message(
            channel_id=channel_id,
            content=message.content.strip(),
            author=message.author,
            channel=message.channel,
            messages=messages,
        )


def run_bot(token: str, deps: CommandDependencies) -> None:
    bot = SatisfactorydleBot(deps=deps)
    bot.run(token)
