from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import discord
from discord import app_commands

from app.api.satisfactorydle_api import ApiException, SatisfactorydleAPI
from app.commands.embed_helper import (
    COLOR_ERROR,
    COLOR_INFO,
    COLOR_SUCCESS,
    COLOR_WARNING,
    add_answer_fields,
    add_field_if_present,
    add_guess_history,
    add_hint_fields,
    apply_footer,
    build_error_embed,
    has_value,
)
from app.commands.entity_cache import EntityCache
from app.domain.game_mode import GameMode
from app.i18n.message_manager import MessageManager, Messages
from app.quiz.quiz_manager import QuizManager
from app.storage.guild_config_manager import GuildConfigManager
from app.utils.string_utils import normalize


@dataclass(slots=True)
class CommandDependencies:
    api: SatisfactorydleAPI
    guild_config_manager: GuildConfigManager
    message_manager: MessageManager
    entity_cache: EntityCache
    quiz_manager: QuizManager
    default_locale: str


def register_sfdle_commands(tree: app_commands.CommandTree, deps: CommandDependencies) -> None:
    mode_choices = [app_commands.Choice(name=mode.display, value=mode.value) for mode in GameMode]

    sfdle_group = app_commands.Group(name="sfdle", description="Play Satisfactorydle.net")

    @sfdle_group.command(name="start", description="Start the daily challenge and see yesterday's answer")
    @app_commands.describe(mode="Game mode")
    @app_commands.choices(mode=mode_choices)
    async def start(interaction: discord.Interaction, mode: app_commands.Choice[str]) -> None:
        messages = await resolve_messages(interaction, deps)
        if await is_mode_disabled(interaction, mode.value, deps, messages):
            return

        await interaction.response.defer(ephemeral=True)
        try:
            daily = await deps.api.get_daily(mode.value, messages.locale)
            deps.entity_cache.store(deps.entity_cache.cache_key(mode.value, messages.locale), daily.get("entities", []))

            mode_display = GameMode.from_key(mode.value).display
            embed = discord.Embed(color=COLOR_INFO, title=messages.get("start.title", "mode", mode_display))

            try:
                yesterday = await deps.api.get_yesterday(mode.value, messages.locale)
                answer = yesterday["answer"]
                game_id = int(yesterday["game_id"])
                name = str(answer["name"])

                text = f"**{name}**"
                if has_value(answer, "description"):
                    text += f"\n{answer['description']}"
                embed.add_field(name=messages.get("start.yesterday_field", "id", game_id), value=text, inline=False)

                if has_value(answer, "image_url"):
                    embed.set_thumbnail(url=str(answer["image_url"]))
            except ApiException as exception:
                if exception.status_code == 404:
                    embed.add_field(
                        name=messages.get("start.yesterday_field", "id", "?"),
                        value=messages.get("start.yesterday_none"),
                        inline=False,
                    )
                else:
                    raise

            game_id = int(daily["game_id"])
            clue = daily["clue"]

            today_text = messages.get("start.today_text", "id", game_id)
            if has_value(clue, "description"):
                today_text += messages.get("start.clue_prefix", "clue", clue["description"])

            embed.add_field(name=messages.get("start.today_field"), value=today_text, inline=False)
            if has_value(clue, "image_url"):
                embed.set_image(url=str(clue["image_url"]))

            apply_footer(embed, messages.get("start.footer"))
            await interaction.edit_original_response(embed=embed)
        except ApiException as exception:
            await interaction.edit_original_response(embed=build_error_embed(messages, str(exception)))
        except Exception as exception:  # noqa: BLE001
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.unexpected", "message", str(exception)))
            )

    @sfdle_group.command(name="guess", description="Make a guess")
    @app_commands.describe(mode="Game mode", entity="The entity to guess")
    @app_commands.choices(mode=mode_choices)
    async def guess(interaction: discord.Interaction, mode: app_commands.Choice[str], entity: str) -> None:
        messages = await resolve_messages(interaction, deps)
        if await is_mode_disabled(interaction, mode.value, deps, messages):
            return

        await interaction.response.defer(ephemeral=True)

        key = deps.entity_cache.cache_key(mode.value, messages.locale)
        await deps.entity_cache.ensure_cache(mode.value, messages.locale)

        entities = deps.entity_cache.get(key) or []
        normalized_entity = normalize(entity)
        selected = next((entry for entry in entities if normalize(entry.name) == normalized_entity), None)

        if selected is None:
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.entity_not_found", "name", entity))
            )
            return

        user_id = str(interaction.user.id)

        try:
            result = await deps.api.guess(mode.value, user_id, selected.id, messages.locale)
            correct = bool(result["correct"])
            total_guesses = int(result["total_guesses"])
            state = await deps.api.get_state(mode.value, user_id, messages.locale)

            if correct:
                answer = result["answer"]
                guess_word = messages.get("common.guesses") if total_guesses > 1 else messages.get("common.guess")
                embed = discord.Embed(
                    color=COLOR_SUCCESS,
                    title=messages.get("guess.correct_title"),
                    description=messages.get(
                        "guess.correct_description",
                        "name",
                        answer["name"],
                        "count",
                        total_guesses,
                        "guess_word",
                        guess_word,
                    ),
                )
                guesses = state.get("guesses")
                if isinstance(guesses, list):
                    add_guess_history(embed, guesses, mode.value, messages)

                add_answer_fields(embed, answer, mode.value, messages)
                if has_value(answer, "image_url"):
                    embed.set_thumbnail(url=str(answer["image_url"]))

                apply_footer(embed, messages.get("guess.correct_footer"))
                await interaction.edit_original_response(embed=embed)
            else:
                guess_number = int(result["guess_number"])
                embed = discord.Embed(
                    color=COLOR_ERROR,
                    title=messages.get("guess.wrong_title", "name", entity),
                    description=messages.get("guess.wrong_description", "number", guess_number),
                )
                guesses = state.get("guesses")
                if isinstance(guesses, list):
                    add_guess_history(embed, guesses, mode.value, messages)

                hints = result.get("hints")
                if isinstance(hints, dict):
                    add_hint_fields(embed, hints, messages)

                apply_footer(embed, messages.get("guess.wrong_footer"))
                await interaction.edit_original_response(embed=embed)
        except ApiException as exception:
            if exception.status_code == 409:
                body = exception.body
                won = bool(body.get("won", False))
                embed = discord.Embed(
                    color=COLOR_WARNING,
                    title=messages.get("guess.already_won_title") if won else messages.get("guess.duplicate_title"),
                    description=str(exception),
                )
                if won and "total_guesses" in body:
                    embed.add_field(
                        name=messages.get("field.total_guesses"),
                        value=str(body["total_guesses"]),
                        inline=True,
                    )
                apply_footer(embed, None)
                await interaction.edit_original_response(embed=embed)
            else:
                await interaction.edit_original_response(embed=build_error_embed(messages, str(exception)))
        except Exception as exception:  # noqa: BLE001
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.unexpected", "message", str(exception)))
            )

    @guess.autocomplete("entity")
    async def guess_autocomplete(
        interaction: discord.Interaction, current: str
    ) -> list[app_commands.Choice[str]]:
        locale = await resolve_locale(interaction, deps)
        namespace_mode = getattr(interaction.namespace, "mode", "item")
        if isinstance(namespace_mode, app_commands.Choice):
            mode = str(namespace_mode.value)
        else:
            mode = str(namespace_mode or "item")

        key = deps.entity_cache.cache_key(mode, locale)
        await deps.entity_cache.ensure_cache_async(mode, locale)

        entities = deps.entity_cache.get(key)
        if entities is None:
            return []

        typed = normalize(current) or ""
        filtered = [entry for entry in entities if typed in (normalize(entry.name) or "")]
        filtered.sort(
            key=lambda entry: (
                0 if (normalize(entry.name) or "").startswith(typed) else 1,
                entry.name.lower(),
            )
        )

        return [app_commands.Choice(name=entry.name, value=entry.name) for entry in filtered[:25]]

    @sfdle_group.command(name="score", description="View your current score and guesses")
    @app_commands.describe(mode="Game mode")
    @app_commands.choices(mode=mode_choices)
    async def score(interaction: discord.Interaction, mode: app_commands.Choice[str]) -> None:
        messages = await resolve_messages(interaction, deps)
        if await is_mode_disabled(interaction, mode.value, deps, messages):
            return

        await interaction.response.defer(ephemeral=True)
        user_id = str(interaction.user.id)
        mode_display = GameMode.from_key(mode.value).display

        try:
            state = await deps.api.get_state(mode.value, user_id, messages.locale)
            won = bool(state["won"])
            total_guesses = int(state["total_guesses"])
            game_id = int(state["game_id"])

            embed = discord.Embed(
                color=COLOR_SUCCESS if won else COLOR_INFO,
                title=messages.get("score.title", "mode", mode_display, "id", game_id),
            )
            embed.add_field(
                name=messages.get("score.status"),
                value=messages.get("score.won") if won else messages.get("score.in_progress"),
                inline=True,
            )
            embed.add_field(name=messages.get("score.guesses_field"), value=str(total_guesses), inline=True)

            guesses = state.get("guesses")
            if isinstance(guesses, list) and guesses:
                rows = []
                for index, entry in enumerate(guesses, start=1):
                    row = f"{index}. {'✅' if bool(entry.get('correct', False)) else '❌'} {entry.get('name', '?')}"
                    rows.append(row)
                embed.add_field(name=messages.get("score.your_guesses"), value="\n".join(rows), inline=False)

            hints = state.get("hints")
            if isinstance(hints, dict):
                add_hint_fields(embed, hints, messages)

            if won and isinstance(state.get("answer"), dict):
                answer = state["answer"]
                if has_value(answer, "image_url"):
                    embed.set_thumbnail(url=str(answer["image_url"]))

            apply_footer(embed, messages.get("score.footer_won") if won else messages.get("score.footer_playing"))
            await interaction.edit_original_response(embed=embed)
        except ApiException as exception:
            if exception.status_code == 404:
                embed = discord.Embed(
                    color=COLOR_INFO,
                    title=messages.get("score.title_no_game", "mode", mode_display),
                    description=messages.get("score.no_game"),
                )
                apply_footer(embed, None)
                await interaction.edit_original_response(embed=embed)
            else:
                await interaction.edit_original_response(embed=build_error_embed(messages, str(exception)))
        except Exception as exception:  # noqa: BLE001
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.unexpected", "message", str(exception)))
            )

    @sfdle_group.command(name="quiz", description="Start a timed quiz - guess the item in 60 seconds!")
    async def quiz(interaction: discord.Interaction) -> None:
        messages = await resolve_messages(interaction, deps)
        channel = interaction.channel
        if channel is None:
            await interaction.response.send_message(
                embed=build_error_embed(messages, messages.get("error.unexpected", "message", "Channel unavailable")),
                ephemeral=True,
            )
            return

        channel_id = int(channel.id)
        if deps.quiz_manager.has_active_quiz(channel_id):
            await interaction.response.defer(ephemeral=True)
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("quiz.already_active"))
            )
            return

        await interaction.response.defer(ephemeral=False)

        guild_id = str(interaction.guild.id) if interaction.guild is not None else "DM"
        user_id = str(interaction.user.id)

        try:
            result = await deps.api.quiz_start(guild_id, str(channel_id), user_id, messages.locale)
            quiz_id = int(result["quiz_id"])
            entity = result["entity"]

            await deps.quiz_manager.start_quiz(
                guild_id=guild_id,
                channel=channel,
                channel_id=channel_id,
                starter_user_id=user_id,
                locale=messages.locale,
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
            await interaction.edit_original_response(embed=embed)
        except ApiException as exception:
            await interaction.edit_original_response(embed=build_error_embed(messages, str(exception)))
        except Exception as exception:  # noqa: BLE001
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.unexpected", "message", str(exception)))
            )

    config_group = app_commands.Group(name="config", description="Configure the bot for this server", parent=sfdle_group)

    @config_group.command(name="language", description="Set the language")
    @app_commands.describe(locale="Language")
    @app_commands.choices(
        locale=[
            app_commands.Choice(name="Français", value="fr"),
            app_commands.Choice(name="English", value="en"),
        ]
    )
    async def config_language(interaction: discord.Interaction, locale: app_commands.Choice[str]) -> None:
        messages = await resolve_messages(interaction, deps)
        await interaction.response.defer(ephemeral=True)

        if not has_manage_server_permission(interaction):
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.no_permission"))
            )
            return

        guild = interaction.guild
        if guild is None:
            await interaction.edit_original_response(embed=build_error_embed(messages, messages.get("error.unexpected", "message", "Guild only command")))
            return

        await deps.guild_config_manager.set_locale(str(guild.id), locale.value)
        updated_messages = deps.message_manager.for_locale(locale.value)

        embed = discord.Embed(
            color=COLOR_SUCCESS,
            title=updated_messages.get("config.updated_title"),
            description=updated_messages.get("config.language_set", "locale", locale.value),
        )
        apply_footer(embed, None)
        await interaction.edit_original_response(embed=embed)

    @config_group.command(name="mode", description="Enable or disable a game mode")
    @app_commands.describe(mode="Game mode", enabled="Enable or disable")
    @app_commands.choices(mode=mode_choices)
    async def config_mode(interaction: discord.Interaction, mode: app_commands.Choice[str], enabled: bool) -> None:
        messages = await resolve_messages(interaction, deps)
        await interaction.response.defer(ephemeral=True)

        if not has_manage_server_permission(interaction):
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.no_permission"))
            )
            return

        guild = interaction.guild
        if guild is None:
            await interaction.edit_original_response(embed=build_error_embed(messages, messages.get("error.unexpected", "message", "Guild only command")))
            return

        await deps.guild_config_manager.set_mode_enabled(str(guild.id), mode.value, enabled)
        mode_display = GameMode.from_key(mode.value).display
        description = (
            messages.get("config.mode_enabled", "mode", mode_display)
            if enabled
            else messages.get("config.mode_disabled", "mode", mode_display)
        )

        embed = discord.Embed(
            color=COLOR_SUCCESS,
            title=messages.get("config.updated_title"),
            description=description,
        )
        apply_footer(embed, None)
        await interaction.edit_original_response(embed=embed)

    @config_group.command(name="show", description="Show current configuration")
    async def config_show(interaction: discord.Interaction) -> None:
        messages = await resolve_messages(interaction, deps)
        await interaction.response.defer(ephemeral=True)

        if not has_manage_server_permission(interaction):
            await interaction.edit_original_response(
                embed=build_error_embed(messages, messages.get("error.no_permission"))
            )
            return

        guild = interaction.guild
        if guild is None:
            await interaction.edit_original_response(embed=build_error_embed(messages, messages.get("error.unexpected", "message", "Guild only command")))
            return

        config = await deps.guild_config_manager.get_config(str(guild.id))

        modes = []
        for mode in GameMode:
            active = config.is_mode_active(mode.value)
            modes.append(f"{'✅' if active else '❌'} {mode.display}")

        embed = discord.Embed(color=COLOR_INFO, title=messages.get("config.show_title"))
        embed.add_field(name=messages.get("config.language_field"), value=config.locale, inline=True)
        embed.add_field(name=messages.get("config.modes_field"), value="\n".join(modes), inline=False)
        apply_footer(embed, None)
        await interaction.edit_original_response(embed=embed)

    tree.add_command(sfdle_group)


async def resolve_locale(interaction: discord.Interaction, deps: CommandDependencies) -> str:
    if interaction.guild is None:
        return deps.default_locale

    config = await deps.guild_config_manager.get_config(str(interaction.guild.id))
    return config.locale


async def resolve_messages(interaction: discord.Interaction, deps: CommandDependencies) -> Messages:
    locale = await resolve_locale(interaction, deps)
    return deps.message_manager.for_locale(locale)


def has_manage_server_permission(interaction: discord.Interaction) -> bool:
    if interaction.guild is None:
        return False

    permissions = getattr(interaction.user, "guild_permissions", None)
    return bool(permissions and permissions.manage_guild)


async def is_mode_disabled(
    interaction: discord.Interaction,
    mode: str,
    deps: CommandDependencies,
    messages: Messages,
) -> bool:
    if interaction.guild is None:
        return False

    config = await deps.guild_config_manager.get_config(str(interaction.guild.id))
    if config.is_mode_active(mode):
        return False

    await interaction.response.defer(ephemeral=True)
    mode_display = GameMode.from_key(mode).display
    await interaction.edit_original_response(
        embed=build_error_embed(messages, messages.get("error.mode_disabled", "mode", mode_display))
    )
    return True
