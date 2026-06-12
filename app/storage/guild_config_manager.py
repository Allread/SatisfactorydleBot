from __future__ import annotations

import aiosqlite

from app.domain.game_mode import GameMode
from app.domain.guild_config import GuildConfig


class GuildConfigManager:
    ALL_MODES = ",".join(mode.value for mode in GameMode)

    def __init__(self, sqlite_path: str, default_locale: str) -> None:
        self.sqlite_path = sqlite_path
        self.default_locale = default_locale
        self._cache: dict[str, GuildConfig] = {}

    async def load_all(self) -> None:
        async with aiosqlite.connect(self.sqlite_path) as db:
            async with db.execute("SELECT guild_id, locale, active_modes FROM guild_configs") as cursor:
                rows = await cursor.fetchall()

        for guild_id, locale, active_modes in rows:
            self._cache[guild_id] = GuildConfig(guild_id=guild_id, locale=locale, active_modes=active_modes)

    async def get_config(self, guild_id: str) -> GuildConfig:
        cached = self._cache.get(guild_id)
        if cached is not None:
            return cached

        config = GuildConfig(guild_id=guild_id, locale=self.default_locale, active_modes=self.ALL_MODES)
        self._cache[guild_id] = config
        await self._save(config)
        return config

    async def set_locale(self, guild_id: str, locale: str) -> None:
        current = await self.get_config(guild_id)
        updated = GuildConfig(guild_id=guild_id, locale=locale, active_modes=current.active_modes)
        self._cache[guild_id] = updated
        await self._save(updated)

    async def set_mode_enabled(self, guild_id: str, mode: str, enabled: bool) -> None:
        current = await self.get_config(guild_id)
        modes = current.get_active_mode_list()
        if enabled and mode not in modes:
            modes.append(mode)
        elif not enabled:
            modes = [value for value in modes if value != mode]

        updated = GuildConfig(guild_id=guild_id, locale=current.locale, active_modes=",".join(modes))
        self._cache[guild_id] = updated
        await self._save(updated)

    async def _save(self, config: GuildConfig) -> None:
        query = """
        INSERT INTO guild_configs(guild_id, locale, active_modes)
        VALUES (?, ?, ?)
        ON CONFLICT(guild_id) DO UPDATE SET
            locale = excluded.locale,
            active_modes = excluded.active_modes
        """
        async with aiosqlite.connect(self.sqlite_path) as db:
            await db.execute(query, (config.guild_id, config.locale, config.active_modes))
            await db.commit()
