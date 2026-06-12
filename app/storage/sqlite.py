from __future__ import annotations

import aiosqlite


CREATE_GUILD_CONFIGS_SQL = """
CREATE TABLE IF NOT EXISTS guild_configs (
    guild_id TEXT PRIMARY KEY,
    locale TEXT NOT NULL,
    active_modes TEXT NOT NULL
);
"""


async def init_database(sqlite_path: str) -> None:
    async with aiosqlite.connect(sqlite_path) as db:
        await db.execute(CREATE_GUILD_CONFIGS_SQL)
        await db.commit()
