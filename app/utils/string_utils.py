from __future__ import annotations

import unicodedata


def normalize(value: str | None) -> str | None:
    if value is None:
        return None
    decomposed = unicodedata.normalize("NFD", value)
    without_accents = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    return without_accents.lower()
