from app.i18n.message_manager import MessageManager


def test_message_replacement_and_lookup() -> None:
    manager = MessageManager(default_locale="fr")

    result = manager.get("fr", "quiz.winner_description", "user", "@Max", "name", "Iron Plate", "time", 12)

    assert "@Max" in result
    assert "Iron Plate" in result
    assert "12" in result


def test_message_fallback_to_default_locale() -> None:
    manager = MessageManager(default_locale="fr")

    result = manager.get("de", "error.title")

    assert result == "Erreur"


def test_message_missing_key_returns_key() -> None:
    manager = MessageManager(default_locale="fr")

    result = manager.get("fr", "unknown.path")

    assert result == "unknown.path"
