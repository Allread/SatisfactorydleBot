from pathlib import Path

import pytest

from app.storage.guild_config_manager import GuildConfigManager
from app.storage.sqlite import init_database


@pytest.mark.asyncio
async def test_guild_config_default_and_update(tmp_path: Path) -> None:
    db_path = tmp_path / "bot.db"
    await init_database(str(db_path))

    manager = GuildConfigManager(str(db_path), default_locale="fr")
    await manager.load_all()

    config = await manager.get_config("123")
    assert config.guild_id == "123"
    assert config.locale == "fr"
    assert config.is_mode_active("item") is True

    await manager.set_locale("123", "en")
    await manager.set_mode_enabled("123", "item", False)

    updated = await manager.get_config("123")
    assert updated.locale == "en"
    assert updated.is_mode_active("item") is False
