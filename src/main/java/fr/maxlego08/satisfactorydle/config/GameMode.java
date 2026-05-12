package fr.maxlego08.satisfactorydle.config;

public enum GameMode {

    ITEM("item", "Item"),

    BUILDING("building", "Building"),

    RECIPE("recipe", "Recipe"),

    CREATURE("creature", "Creature"),

    MILESTONE("milestone", "Milestone");

    private final String key;
    private final String display;

    GameMode(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public static GameMode fromKey(String key) {
        for (GameMode mode : values()) {
            if (mode.key.equals(key)) return mode;
        }
        return ITEM;
    }

    public String getKey() {
        return key;
    }

    public String getDisplay() {
        return display;
    }
}
