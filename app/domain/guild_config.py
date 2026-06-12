from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True, frozen=True)
class GuildConfig:
    guild_id: str
    locale: str
    active_modes: str

    def get_active_mode_list(self) -> list[str]:
        if not self.active_modes:
            return []
        return [value for value in self.active_modes.split(",") if value]

    def is_mode_active(self, mode: str) -> bool:
        return mode in self.get_active_mode_list()
