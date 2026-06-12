import asyncio

import pytest

from app.quiz.quiz_manager import QuizManager


class FakeMessages:
    locale = "fr"

    def get(self, key: str, *replacements):
        result = key
        for i in range(0, len(replacements) - 1, 2):
            result += f" {replacements[i]}={replacements[i + 1]}"
        return result


class FakeChannel:
    def __init__(self) -> None:
        self.sent = []
        self.id = 42

    async def send(self, embed):
        self.sent.append(embed)


class FakeUser:
    def __init__(self, user_id: int, name: str) -> None:
        self.id = user_id
        self.name = name
        self.mention = f"<@{user_id}>"


class FakeApi:
    def __init__(self) -> None:
        self.completed = []
        self.started = 0

    async def quiz_complete(self, quiz_id: int, won: bool, winner_user_id: str | None):
        self.completed.append((quiz_id, won, winner_user_id))

    async def quiz_start(self, guild_id: str, channel_id: str, user_id: str, locale: str):
        self.started += 1
        return {
            "quiz_id": 100 + self.started,
            "entity": {
                "name": f"Auto{self.started}",
                "image_url": "https://img",
                "category": "Item",
                "tier": 1,
                "form": "Solid",
            },
        }


@pytest.mark.asyncio
async def test_quiz_timeout_stops_session() -> None:
    api = FakeApi()
    manager = QuizManager(api)  # type: ignore[arg-type]
    manager.QUIZ_TIMEOUT_SECONDS = 0.05
    manager.QUIZ_HINT_SECONDS = 60

    channel = FakeChannel()
    messages = FakeMessages()

    await manager.start_quiz(
        guild_id="1",
        channel=channel,
        channel_id=channel.id,
        starter_user_id="10",
        locale="fr",
        quiz_id=1,
        entity={"name": "Rotor", "image_url": "https://img"},
        messages=messages,
    )

    assert manager.has_active_quiz(channel.id) is True

    await asyncio.sleep(0.1)

    assert manager.has_active_quiz(channel.id) is False
    assert api.completed[-1] == (1, False, None)


@pytest.mark.asyncio
async def test_quiz_correct_answer_auto_restarts() -> None:
    api = FakeApi()
    manager = QuizManager(api)  # type: ignore[arg-type]
    manager.QUIZ_TIMEOUT_SECONDS = 60
    manager.QUIZ_HINT_SECONDS = 60

    channel = FakeChannel()
    messages = FakeMessages()

    await manager.start_quiz(
        guild_id="1",
        channel=channel,
        channel_id=channel.id,
        starter_user_id="10",
        locale="fr",
        quiz_id=2,
        entity={"name": "Smart Plating", "image_url": "https://img"},
        messages=messages,
    )

    winner = FakeUser(99, "Max")
    await manager.handle_message(channel.id, "smart plating", winner, channel, messages)

    assert api.completed[-1] == (2, True, "99")
    assert api.started == 1
    assert manager.has_active_quiz(channel.id) is True

    await manager.shutdown()
