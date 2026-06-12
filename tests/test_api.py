import httpx
import pytest

from app.api.satisfactorydle_api import ApiException, SatisfactorydleAPI


def test_build_url() -> None:
    api = SatisfactorydleAPI(base_url="https://example.com/", api_token="token")

    url = api._url("/api/discord/item/daily", "fr")

    assert url == "https://example.com/api/discord/item/daily?locale=fr"


def test_handle_response_success() -> None:
    request = httpx.Request("GET", "https://example.com")
    response = httpx.Response(200, json={"ok": True}, request=request)

    body = SatisfactorydleAPI._handle_response(response)

    assert body["ok"] is True


def test_handle_response_error() -> None:
    request = httpx.Request("GET", "https://example.com")
    response = httpx.Response(404, json={"error": "Not found"}, request=request)

    with pytest.raises(ApiException) as exc:
        SatisfactorydleAPI._handle_response(response)

    assert exc.value.status_code == 404
    assert str(exc.value) == "Not found"
