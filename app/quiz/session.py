from __future__ import annotations

from dataclasses import dataclass, field
from time import time
from typing import Any


@dataclass(slots=True)
class QuizSession:
    answer: str
    entity: dict[str, Any]
    quiz_id: int
    image_url: str
    guild_id: str
    starter_user_id: str
    locale: str
    start_time: float = field(default_factory=time)
