from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(slots=True, frozen=True)
class Messages:
    manager: "MessageManager"
    locale: str

    def get(self, key: str, *replacements: Any) -> str:
        return self.manager.get(self.locale, key, *replacements)


class MessageManager:
    def __init__(self, default_locale: str) -> None:
        self.default_locale = default_locale
        self._locales: dict[str, dict[str, Any]] = {}
        self._load_locale("en")
        self._load_locale("fr")

    def _load_locale(self, locale: str) -> None:
        path = Path(__file__).resolve().parent.parent / "locales" / f"{locale}.json"
        if not path.exists():
            print(f"Warning: locale file not found: {path}")
            return
        self._locales[locale] = json.loads(path.read_text(encoding="utf-8"))

    def get(self, locale: str, key: str, *replacements: Any) -> str:
        messages = self._locales.get(locale) or self._locales.get(self.default_locale)
        if messages is None:
            return key

        current: Any = messages
        for part in key.split("."):
            if not isinstance(current, dict) or part not in current:
                return key
            current = current[part]

        if not isinstance(current, str):
            return key

        result = current
        for i in range(0, len(replacements) - 1, 2):
            result = result.replace("{" + str(replacements[i]) + "}", str(replacements[i + 1]))
        return result

    def for_locale(self, locale: str) -> Messages:
        return Messages(self, locale)
