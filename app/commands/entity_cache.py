from __future__ import annotations

from dataclasses import dataclass

from app.api.satisfactorydle_api import SatisfactorydleAPI


@dataclass(slots=True, frozen=True)
class EntityEntry:
    id: int
    name: str


class EntityCache:
    def __init__(self, api: SatisfactorydleAPI) -> None:
        self.api = api
        self._cache: dict[str, list[EntityEntry]] = {}

    @staticmethod
    def cache_key(mode: str, locale: str) -> str:
        return f"{mode}:{locale}"

    def get(self, key: str) -> list[EntityEntry] | None:
        return self._cache.get(key)

    def store(self, key: str, entities_array: list[dict]) -> None:
        entries = [EntityEntry(id=int(entry["id"]), name=str(entry["name"])) for entry in entities_array]
        self._cache[key] = entries

    async def ensure_cache(self, mode: str, locale: str) -> None:
        key = self.cache_key(mode, locale)
        if key not in self._cache:
            daily = await self.api.get_daily(mode, locale)
            self.store(key, daily.get("entities", []))

    async def ensure_cache_async(self, mode: str, locale: str) -> None:
        key = self.cache_key(mode, locale)
        if key in self._cache:
            return
        try:
            daily = await self.api.get_daily(mode, locale)
            self.store(key, daily.get("entities", []))
        except Exception:  # noqa: BLE001
            return
