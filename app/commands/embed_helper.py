from __future__ import annotations

from datetime import date
from typing import Any

import discord


COLOR_SUCCESS = discord.Color(0x57F287)
COLOR_ERROR = discord.Color(0xED4245)
COLOR_WARNING = discord.Color(0xFEE75C)
COLOR_INFO = discord.Color(0x5865F2)

FOOTER_ICON = "https://satisfactorydle.net/favicon-96x96.png"

COLUMN_TO_FIELD_KEY = {
    "power_consumption": "power",
    "is_alternate": "alternate",
    "unlocked_items_count": "unlocked_items",
    "used_in_count": "used_in_count",
}


def apply_footer(embed: discord.Embed, text: str | None) -> None:
    current_date = date.today().strftime("%d/%m/%Y")
    footer = (
        f"{text} | satisfactorydle.net | {current_date}"
        if text
        else f"satisfactorydle.net | {current_date}"
    )
    embed.set_footer(text=footer, icon_url=FOOTER_ICON)


def build_error_embed(messages: Any, message: str) -> discord.Embed:
    embed = discord.Embed(color=COLOR_ERROR, title=messages.get("error.title"), description=message)
    apply_footer(embed, None)
    return embed


def has_value(data: dict[str, Any], key: str) -> bool:
    return key in data and data[key] is not None


def json_to_string(value: Any, messages: Any) -> str:
    if isinstance(value, bool):
        return messages.get("common.yes") if value else messages.get("common.no")
    return str(value)


def json_to_string_raw(value: Any) -> str:
    if isinstance(value, bool):
        return "Yes" if value else "No"
    return str(value)


def format_json_array(values: list[Any]) -> str:
    rendered: list[str] = []
    for value in values:
        if isinstance(value, dict):
            part = value.get("slug", "")
            if "amount" in value:
                part += f" x{value['amount']}"
            rendered.append(part)
        else:
            rendered.append(json_to_string_raw(value))
    return ", ".join(rendered)


def format_label(key: str) -> str:
    label = key.replace("_", " ")
    return label[:1].upper() + label[1:] if label else key


def format_tier_value(value: str) -> str:
    return "MAM" if value == "-1" else value


def result_emoji(result: str) -> str:
    return {
        "correct": "🟩",
        "higher": "⬆️",
        "lower": "⬇️",
        "partial": "🟧",
    }.get(result, "🟥")


def get_comparison_columns(mode: str) -> list[str]:
    mapping = {
        "classic": ["category", "tier", "stack_size", "form", "sink_points", "used_in_count"],
        "item": ["category", "tier", "form", "stack_size", "sink_points"],
        "building": ["category", "tier", "power_consumption"],
        "recipe": ["building", "is_alternate", "tier"],
        "creature": ["hostility", "biome", "type"],
        "milestone": ["source", "tier", "unlocked_items_count"],
    }
    return mapping.get(mode, [])


def add_field_if_present(embed: discord.Embed, label: str, data: dict[str, Any], key: str, inline: bool) -> None:
    if has_value(data, key):
        value = json_to_string_raw(data[key])
        if key == "tier":
            value = format_tier_value(value)
        embed.add_field(name=label, value=value, inline=inline)


def add_input_items(embed: discord.Embed, data: dict[str, Any], messages: Any) -> None:
    input_items = data.get("input_items")
    if not isinstance(input_items, list) or not input_items:
        return

    rows = []
    for item in input_items:
        row = f"- {item.get('slug', '')}"
        if "amount" in item:
            row += f" x{item['amount']}"
        rows.append(row)
    embed.add_field(name=messages.get("field.input_items"), value="\n".join(rows), inline=False)


def add_hint_fields(embed: discord.Embed, hints: dict[str, Any], messages: Any) -> None:
    unlocked_rows: list[str] = []
    locked_rows: list[str] = []

    for key, value in hints.items():
        if key in {"locked", "image_url"} or value is None:
            continue

        field_key = COLUMN_TO_FIELD_KEY.get(key, key)
        label = messages.get(f"field.{field_key}")
        if label == f"field.{field_key}":
            label = format_label(key)

        if key == "reveal_image":
            if bool(value) and has_value(hints, "image_url"):
                embed.set_image(url=str(hints["image_url"]))
                unlocked_rows.append(f"**Image:** {messages.get('hints.image_revealed')}")
        elif key == "is_alternate":
            display = messages.get("common.yes") if bool(value) else messages.get("common.no")
            unlocked_rows.append(f"**{label}:** {display}")
        elif isinstance(value, list):
            unlocked_rows.append(f"**{label}:** {format_json_array(value)}")
        else:
            display = json_to_string(value, messages)
            if key == "tier":
                display = format_tier_value(display)
            unlocked_rows.append(f"**{label}:** {display}")

    locked = hints.get("locked")
    if isinstance(locked, list):
        for item in locked:
            label = item.get("label", "?")
            remaining = int(item.get("remaining", 0))
            guess_word = messages.get("common.guesses") if remaining > 1 else messages.get("common.guess")
            locked_rows.append(f"**{label}** - {messages.get('hints.unlock_in', 'remaining', remaining, 'guess_word', guess_word)}")

    if unlocked_rows:
        embed.add_field(name=messages.get("hints.title"), value="\n".join(unlocked_rows), inline=False)
    if locked_rows:
        embed.add_field(name=messages.get("hints.locked_title"), value="\n".join(locked_rows), inline=False)


def add_answer_fields(embed: discord.Embed, answer: dict[str, Any], mode: str, messages: Any) -> None:
    if mode == "classic":
        add_field_if_present(embed, messages.get("field.category"), answer, "category", True)
        add_field_if_present(embed, messages.get("field.tier"), answer, "tier", True)
        add_field_if_present(embed, messages.get("field.stack_size"), answer, "stack_size", True)
        add_field_if_present(embed, messages.get("field.form"), answer, "form", True)
        add_field_if_present(embed, messages.get("field.sink_points"), answer, "sink_points", True)
        add_field_if_present(embed, messages.get("field.used_in_count"), answer, "used_in_count", True)
    elif mode == "item":
        add_field_if_present(embed, messages.get("field.category"), answer, "category", True)
        add_field_if_present(embed, messages.get("field.tier"), answer, "tier", True)
        add_field_if_present(embed, messages.get("field.form"), answer, "form", True)
        add_field_if_present(embed, messages.get("field.stack_size"), answer, "stack_size", True)
        add_field_if_present(embed, messages.get("field.sink_points"), answer, "sink_points", True)
    elif mode == "building":
        add_field_if_present(embed, messages.get("field.category"), answer, "category", True)
        add_field_if_present(embed, messages.get("field.tier"), answer, "tier", True)
        add_field_if_present(embed, messages.get("field.power"), answer, "power_consumption", True)
    elif mode == "recipe":
        add_field_if_present(embed, messages.get("field.building"), answer, "building", True)
        if has_value(answer, "is_alternate"):
            value = messages.get("common.yes") if bool(answer["is_alternate"]) else messages.get("common.no")
            embed.add_field(name=messages.get("field.alternate"), value=value, inline=True)
        add_input_items(embed, answer, messages)
    elif mode == "creature":
        add_field_if_present(embed, messages.get("field.hostility"), answer, "hostility", True)
        add_field_if_present(embed, messages.get("field.biome"), answer, "biome", True)
        add_field_if_present(embed, messages.get("field.type"), answer, "type", True)
    elif mode == "milestone":
        add_field_if_present(embed, messages.get("field.source"), answer, "source", True)
        add_field_if_present(embed, messages.get("field.tier"), answer, "tier", True)
        add_field_if_present(embed, messages.get("field.unlocked_items"), answer, "unlocked_items_count", True)

    if has_value(answer, "description"):
        desc = str(answer["description"])
        if len(desc) > 200:
            desc = desc[:200] + "..."
        embed.add_field(name=messages.get("field.description"), value=desc, inline=False)


def add_guess_history(embed: discord.Embed, guesses: list[dict[str, Any]], mode: str, messages: Any) -> None:
    if not guesses:
        return

    columns = get_comparison_columns(mode)
    if not columns:
        return

    headers: list[str] = []
    for column in columns:
        field_key = COLUMN_TO_FIELD_KEY.get(column, column)
        headers.append(messages.get(f"field.{field_key}"))

    lines = [f"*{' | '.join(headers)}*"]
    start = max(0, len(guesses) - 10)

    for guess in guesses[start:]:
        correct = bool(guess.get("correct", False))
        name = str(guess.get("name", "?"))
        lines.append(f"\n{'✅' if correct else '❌'} **{name}**")

        comparisons = guess.get("comparisons")
        if isinstance(comparisons, dict):
            row_parts: list[str] = []
            for column in columns:
                comp = comparisons.get(column)
                if isinstance(comp, dict):
                    result = str(comp.get("result", "wrong"))
                    value = str(comp.get("value", "?"))
                    if column == "tier":
                        value = format_tier_value(value)
                    row_parts.append(f"{result_emoji(result)} {value}")
                else:
                    row_parts.append("⬜ ?")
            lines.append(" | ".join(row_parts))

    title = messages.get("guess.history_title")
    if start > 0:
        title += f" ({messages.get('guess.history_last', 'count', 10)})"

    value = "\n".join(lines)
    if len(value) > 1024:
        value = value[:1020] + "..."

    embed.add_field(name=title, value=value, inline=False)
