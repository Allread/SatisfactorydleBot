package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;

public final class EmbedHelper {

    public static final Color COLOR_SUCCESS = new Color(0x57F287);
    public static final Color COLOR_ERROR = new Color(0xED4245);
    public static final Color COLOR_WARNING = new Color(0xFEE75C);
    public static final Color COLOR_INFO = new Color(0x5865F2);

    private EmbedHelper() {}

    public static void replyError(SlashCommandInteractionEvent event, String message) {
        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_ERROR)
                        .setTitle("Error")
                        .setDescription(message)
                        .build()
        ).queue();
    }

    public static void addAnswerFields(EmbedBuilder embed, JsonObject answer, String mode) {
        switch (mode) {
            case "item" -> {
                addFieldIfPresent(embed, "Category", answer, "category", true);
                addFieldIfPresent(embed, "Tier", answer, "tier", true);
                addFieldIfPresent(embed, "Form", answer, "form", true);
                addFieldIfPresent(embed, "Stack Size", answer, "stack_size", true);
                addFieldIfPresent(embed, "Sink Points", answer, "sink_points", true);
            }
            case "building" -> {
                addFieldIfPresent(embed, "Category", answer, "category", true);
                addFieldIfPresent(embed, "Tier", answer, "tier", true);
                addFieldIfPresent(embed, "Power", answer, "power_consumption", true);
            }
            case "recipe" -> {
                addFieldIfPresent(embed, "Building", answer, "building", true);
                if (answer.has("is_alternate") && !answer.get("is_alternate").isJsonNull()) {
                    embed.addField("Alternate", answer.get("is_alternate").getAsBoolean() ? "Yes" : "No", true);
                }
                addInputItems(embed, answer);
            }
            case "creature" -> {
                addFieldIfPresent(embed, "Hostility", answer, "hostility", true);
                addFieldIfPresent(embed, "Biome", answer, "biome", true);
                addFieldIfPresent(embed, "Type", answer, "type", true);
            }
            case "milestone" -> {
                addFieldIfPresent(embed, "Source", answer, "source", true);
                addFieldIfPresent(embed, "Tier", answer, "tier", true);
                addFieldIfPresent(embed, "Unlocked Items", answer, "unlocked_items_count", true);
            }
        }

        if (hasValue(answer, "description")) {
            String desc = answer.get("description").getAsString();
            if (desc.length() > 200) desc = desc.substring(0, 200) + "...";
            embed.addField("Description", desc, false);
        }
    }

    public static void addHintFields(EmbedBuilder embed, JsonObject hints) {
        StringBuilder unlocked = new StringBuilder();
        StringBuilder locked = new StringBuilder();

        for (String key : hints.keySet()) {
            if (key.equals("locked") || key.equals("image_url")) continue;
            JsonElement value = hints.get(key);
            if (value == null || value.isJsonNull()) continue;

            String label = formatLabel(key);

            if (key.equals("reveal_image")) {
                if (value.getAsBoolean() && hasValue(hints, "image_url")) {
                    embed.setImage(hints.get("image_url").getAsString());
                    unlocked.append("**Image:** Revealed\n");
                }
            } else if (key.equals("is_alternate")) {
                unlocked.append("**").append(label).append(":** ").append(value.getAsBoolean() ? "Yes" : "No").append("\n");
            } else if (value.isJsonArray()) {
                unlocked.append("**").append(label).append(":** ").append(formatJsonArray(value.getAsJsonArray())).append("\n");
            } else {
                unlocked.append("**").append(label).append(":** ").append(jsonToString(value)).append("\n");
            }
        }

        if (hints.has("locked") && hints.get("locked").isJsonArray()) {
            for (JsonElement item : hints.getAsJsonArray("locked")) {
                JsonObject lock = item.getAsJsonObject();
                String label = lock.get("label").getAsString();
                int remaining = lock.get("remaining").getAsInt();
                locked.append("**").append(label).append("** - unlocks in ").append(remaining)
                        .append(" guess").append(remaining > 1 ? "es" : "").append("\n");
            }
        }

        if (!unlocked.isEmpty()) {
            embed.addField("Hints", unlocked.toString(), false);
        }
        if (!locked.isEmpty()) {
            embed.addField("Locked Hints", locked.toString(), false);
        }
    }

    public static void addInputItems(EmbedBuilder embed, JsonObject json) {
        if (!json.has("input_items") || !json.get("input_items").isJsonArray()) return;
        JsonArray inputs = json.getAsJsonArray("input_items");
        if (inputs.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (JsonElement element : inputs) {
            JsonObject item = element.getAsJsonObject();
            sb.append("- ").append(item.get("slug").getAsString());
            if (item.has("amount")) sb.append(" x").append(item.get("amount").getAsInt());
            sb.append("\n");
        }
        embed.addField("Input Items", sb.toString(), false);
    }

    public static void addFieldIfPresent(EmbedBuilder embed, String label, JsonObject json, String key, boolean inline) {
        if (hasValue(json, key)) {
            embed.addField(label, jsonToString(json.get(key)), inline);
        }
    }

    public static boolean hasValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    public static String jsonToString(JsonElement element) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean() ? "Yes" : "No";
            if (prim.isNumber()) return prim.getAsNumber().toString();
            return prim.getAsString();
        }
        return element.toString();
    }

    public static String formatJsonArray(JsonArray array) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement element : array) {
            if (!sb.isEmpty()) sb.append(", ");
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                sb.append(obj.get("slug").getAsString());
                if (obj.has("amount")) sb.append(" x").append(obj.get("amount").getAsInt());
            } else {
                sb.append(jsonToString(element));
            }
        }
        return sb.toString();
    }

    public static String formatLabel(String key) {
        String result = key.replace("_", " ");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }
}
