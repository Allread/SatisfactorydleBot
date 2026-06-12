import logging

from app.api.satisfactorydle_api import SatisfactorydleAPI
from app.bot.client import run_bot
from app.commands.entity_cache import EntityCache
from app.commands.handlers import CommandDependencies
from app.config import Settings
from app.i18n.message_manager import MessageManager
from app.quiz.quiz_manager import QuizManager
from app.storage.guild_config_manager import GuildConfigManager
from app.storage.sqlite import init_database


def run() -> None:
    """Application entry point."""
    settings = Settings.from_env()
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    if not settings.discord_token:
        raise RuntimeError("DISCORD_TOKEN is required")
    if not settings.api_token:
        raise RuntimeError("API_TOKEN is required")

    import asyncio

    asyncio.run(init_database(settings.sqlite_path))
    guild_config_manager = GuildConfigManager(settings.sqlite_path, settings.default_locale)
    asyncio.run(guild_config_manager.load_all())

    api = SatisfactorydleAPI(base_url=settings.api_url, api_token=settings.api_token)
    message_manager = MessageManager(settings.default_locale)
    entity_cache = EntityCache(api)
    quiz_manager = QuizManager(api)

    deps = CommandDependencies(
        api=api,
        guild_config_manager=guild_config_manager,
        message_manager=message_manager,
        entity_cache=entity_cache,
        quiz_manager=quiz_manager,
        default_locale=settings.default_locale,
    )

    run_bot(settings.discord_token, deps)


if __name__ == "__main__":
    run()
