from __future__ import annotations

from dataclasses import dataclass
import logging
from typing import Any

import httpx


LOGGER = logging.getLogger(__name__)


class ApiException(Exception):
    def __init__(self, status_code: int, message: str, body: dict[str, Any]) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.body = body


@dataclass(slots=True)
class SatisfactorydleAPI:
    base_url: str
    api_token: str
    timeout_seconds: float = 15.0
    max_retries: int = 1

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")

    async def get_daily(self, mode: str, locale: str) -> dict[str, Any]:
        return await self._get(f"/api/discord/{mode}/daily", locale)

    async def guess(self, mode: str, discord_user_id: str, entity_id: int, locale: str) -> dict[str, Any]:
        body = {
            "discord_user_id": discord_user_id,
            "entity_id": entity_id,
        }
        return await self._post(f"/api/discord/{mode}/guess", body, locale)

    async def get_state(self, mode: str, discord_user_id: str, locale: str) -> dict[str, Any]:
        return await self._get(f"/api/discord/{mode}/state/{discord_user_id}", locale)

    async def get_yesterday(self, mode: str, locale: str) -> dict[str, Any]:
        return await self._get(f"/api/discord/{mode}/yesterday", locale)

    async def quiz_start(self, guild_id: str, channel_id: str, user_id: str, locale: str) -> dict[str, Any]:
        body = {
            "guild_id": guild_id,
            "channel_id": channel_id,
            "discord_user_id": user_id,
        }
        return await self._post("/api/discord/quiz/start", body, locale)

    async def quiz_complete(self, quiz_id: int, won: bool, winner_user_id: str | None) -> None:
        body: dict[str, Any] = {"won": won}
        if winner_user_id is not None:
            body["winner_discord_user_id"] = winner_user_id
        try:
            await self._post(f"/api/discord/quiz/{quiz_id}/complete", body, "en")
        except Exception as exc:  # noqa: BLE001
            print(f"[Quiz] Failed to send quiz result: {exc}")

    async def _get(self, path: str, locale: str) -> dict[str, Any]:
        url = self._url(path, locale)
        response = await self._request_with_retry("GET", url, payload=None)
        return self._handle_response(response)

    async def _post(self, path: str, payload: dict[str, Any], locale: str) -> dict[str, Any]:
        url = self._url(path, locale)
        response = await self._request_with_retry("POST", url, payload=payload)
        return self._handle_response(response)

    async def _request_with_retry(
        self,
        method: str,
        url: str,
        payload: dict[str, Any] | None,
    ) -> httpx.Response:
        last_exception: Exception | None = None
        attempts = self.max_retries + 1

        for attempt in range(1, attempts + 1):
            try:
                async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                    if method == "GET":
                        response = await client.get(url, headers=self._headers())
                    else:
                        response = await client.post(url, headers=self._headers(), json=payload)
                return response
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                last_exception = exc
                LOGGER.warning(
                    "API request failed (%s %s), attempt %s/%s: %s",
                    method,
                    url,
                    attempt,
                    attempts,
                    exc,
                )
                if attempt < attempts:
                    continue

        assert last_exception is not None
        raise last_exception

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.api_token}",
            "Accept": "application/json",
        }

    def _url(self, path: str, locale: str) -> str:
        separator = "&" if "?" in path else "?"
        return f"{self.base_url}{path}{separator}locale={locale}"

    @staticmethod
    def _handle_response(response: httpx.Response) -> dict[str, Any]:
        try:
            body = response.json()
        except ValueError:
            body = {"error": f"Invalid JSON response (HTTP {response.status_code})"}
        if response.status_code >= 400:
            error = body.get("error", f"Unknown error (HTTP {response.status_code})")
            raise ApiException(response.status_code, error, body)
        return body
