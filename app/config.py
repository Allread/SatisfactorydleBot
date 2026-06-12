from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(slots=True)
class Settings:
    discord_token: str
    api_url: str
    api_token: str
    default_locale: str = "fr"
    sqlite_path: str = "./sfdle.db"
    log_level: str = "INFO"

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            discord_token=os.getenv("DISCORD_TOKEN", ""),
            api_url=os.getenv("API_URL", "https://satisfactorydle.net").rstrip("/"),
            api_token=os.getenv("API_TOKEN", ""),
            default_locale=os.getenv("DEFAULT_LOCALE", "fr"),
            sqlite_path=os.getenv("SQLITE_PATH", "./sfdle.db"),
            log_level=os.getenv("LOG_LEVEL", "INFO"),
        )
