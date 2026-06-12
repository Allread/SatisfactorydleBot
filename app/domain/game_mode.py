from enum import Enum


class GameMode(str, Enum):
    CLASSIC = "classic"
    ITEM = "item"
    BUILDING = "building"
    RECIPE = "recipe"
    CREATURE = "creature"
    MILESTONE = "milestone"

    @property
    def display(self) -> str:
        return {
            GameMode.CLASSIC: "Classic",
            GameMode.ITEM: "Item",
            GameMode.BUILDING: "Building",
            GameMode.RECIPE: "Recipe",
            GameMode.CREATURE: "Creature",
            GameMode.MILESTONE: "Milestone",
        }[self]

    @classmethod
    def from_key(cls, key: str) -> "GameMode":
        for mode in cls:
            if mode.value == key:
                return mode
        return cls.ITEM
