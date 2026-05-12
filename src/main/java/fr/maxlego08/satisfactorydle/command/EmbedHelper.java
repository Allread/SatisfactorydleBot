package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.maxlego08.satisfactorydle.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class EmbedHelper {

    public static final Color COLOR_SUCCESS = new Color(0x57F287);
    public static final Color COLOR_ERROR = new Color(0xED4245);
    public static final Color COLOR_WARNING = new Color(0xFEE75C);
    public static final Color COLOR_INFO = new Color(0x5865F2);

    private static final String FOOTER_ICON = "https://satisfactorydle.net/favicon-96x96.png";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, String> COLUMN_TO_FIELD_KEY = Map.of(
            "power_consumption", "power",
            "is_alternate", "alternate",
            "unlocked_items_count", "unlocked_items",
            "used_in_count", "used_in_count"
    );

    private EmbedHelper() {
    }

    public static void applyFooter(EmbedBuilder embed, String text) {
        String date = LocalDate.now().format(DATE_FORMAT);
        String footer = text != null && !text.isEmpty() ? text + " | satisfactorydle.net | " + date : "satisfactorydle.net | " + date;
        embed.setFooter(footer, FOOTER_ICON);
    }

    public static void replyError(SlashCommandInteractionEvent event, Messages messages, String message) {
        EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_ERROR).setTitle(messages.get("error.title")).setDescription(message);
        applyFooter(embed, null);
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    public static void addAnswerFields(EmbedBuilder embed, JsonObject answer, String mode, Messages messages) {
        switch (mode) {
            case "classic" -> {
                addFieldIfPresent(embed, messages.get("field.category"), answer, "category", true);
                addFieldIfPresent(embed, messages.get("field.tier"), answer, "tier", true);
                addFieldIfPresent(embed, messages.get("field.stack_size"), answer, "stack_size", true);
                addFieldIfPresent(embed, messages.get("field.form"), answer, "form", true);
                addFieldIfPresent(embed, messages.get("field.sink_points"), answer, "sink_points", true);
                addFieldIfPresent(embed, messages.get("field.used_in_count"), answer, "used_in_count", true);
            }
            case "item" -> {
                addFieldIfPresent(embed, messages.get("field.category"), answer, "category", true);
                addFieldIfPresent(embed, messages.get("field.tier"), answer, "tier", true);
                addFieldIfPresent(embed, messages.get("field.form"), answer, "form", true);
                addFieldIfPresent(embed, messages.get("field.stack_size"), answer, "stack_size", true);
                addFieldIfPresent(embed, messages.get("field.sink_points"), answer, "sink_points", true);
            }
            case "building" -> {
                addFieldIfPresent(embed, messages.get("field.category"), answer, "category", true);
                addFieldIfPresent(embed, messages.get("field.tier"), answer, "tier", true);
                addFieldIfPresent(embed, messages.get("field.power"), answer, "power_consumption", true);
            }
            case "recipe" -> {
                addFieldIfPresent(embed, messages.get("field.building"), answer, "building", true);
                if (answer.has("is_alternate") && !answer.get("is_alternate").isJsonNull()) {
                    embed.addField(messages.get("field.alternate"), answer.get("is_alternate").getAsBoolean() ? messages.get("common.yes") : messages.get("common.no"), true);
                }
                addInputItems(embed, answer, messages);
            }
            case "creature" -> {
                addFieldIfPresent(embed, messages.get("field.hostility"), answer, "hostility", true);
                addFieldIfPresent(embed, messages.get("field.biome"), answer, "biome", true);
                addFieldIfPresent(embed, messages.get("field.type"), answer, "type", true);
            }
            case "milestone" -> {
                addFieldIfPresent(embed, messages.get("field.source"), answer, "source", true);
                addFieldIfPresent(embed, messages.get("field.tier"), answer, "tier", true);
                addFieldIfPresent(embed, messages.get("field.unlocked_items"), answer, "unlocked_items_count", true);
            }
        }

        if (hasValue(answer, "description")) {
            String desc = answer.get("description").getAsString();
            if (desc.length() > 200) desc = desc.substring(0, 200) + "...";
            embed.addField(messages.get("field.description"), desc, false);
        }
    }

    public static void addHintFields(EmbedBuilder embed, JsonObject hints, Messages messages) {
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
                    unlocked.append("**Image:** ").append(messages.get("hints.image_revealed")).append("\n");
                }
            } else if (key.equals("is_alternate")) {
                unlocked.append("**").append(label).append(":** ").append(value.getAsBoolean() ? messages.get("common.yes") : messages.get("common.no")).append("\n");
            } else if (value.isJsonArray()) {
                unlocked.append("**").append(label).append(":** ").append(formatJsonArray(value.getAsJsonArray())).append("\n");
            } else {
                unlocked.append("**").append(label).append(":** ").append(jsonToString(value, messages)).append("\n");
            }
        }

        if (hints.has("locked") && hints.get("locked").isJsonArray()) {
            for (JsonElement item : hints.getAsJsonArray("locked")) {
                JsonObject lock = item.getAsJsonObject();
                String label = lock.get("label").getAsString();
                int remaining = lock.get("remaining").getAsInt();
                String guessWord = remaining > 1 ? messages.get("common.guesses") : messages.get("common.guess");
                locked.append("**").append(label).append("** - ").append(messages.get("hints.unlock_in", "remaining", remaining, "guess_word", guessWord)).append("\n");
            }
        }

        if (!unlocked.isEmpty()) {
            embed.addField(messages.get("hints.title"), unlocked.toString(), false);
        }
        if (!locked.isEmpty()) {
            embed.addField(messages.get("hints.locked_title"), locked.toString(), false);
        }
    }

    public static void addInputItems(EmbedBuilder embed, JsonObject json, Messages messages) {
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
        embed.addField(messages.get("field.input_items"), sb.toString(), false);
    }

    public static void addFieldIfPresent(EmbedBuilder embed, String label, JsonObject json, String key, boolean inline) {
        if (hasValue(json, key)) {
            embed.addField(label, jsonToStringRaw(json.get(key)), inline);
        }
    }

    public static boolean hasValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    public static String jsonToString(JsonElement element, Messages messages) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean() ? messages.get("common.yes") : messages.get("common.no");
            if (prim.isNumber()) return prim.getAsNumber().toString();
            return prim.getAsString();
        }
        return element.toString();
    }

    public static String jsonToStringRaw(JsonElement element) {
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
                sb.append(jsonToStringRaw(element));
            }
        }
        return sb.toString();
    }

    public static String formatLabel(String key) {
        String result = key.replace("_", " ");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    public static void addGuessHistory(EmbedBuilder embed, JsonArray guesses, String mode, Messages messages) {
        if (guesses.isEmpty()) return;

        List<String> columns = getComparisonColumns(mode);
        if (columns.isEmpty()) return;

        // Column headers in italics
        StringBuilder header = new StringBuilder("*");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) header.append(" | ");
            String fieldKey = COLUMN_TO_FIELD_KEY.getOrDefault(columns.get(i), columns.get(i));
            header.append(messages.get("field." + fieldKey));
        }
        header.append("*\n");

        StringBuilder sb = new StringBuilder(header);
        int start = Math.max(0, guesses.size() - 10);

        for (int i = start; i < guesses.size(); i++) {
            JsonObject guess = guesses.get(i).getAsJsonObject();
            String name = guess.get("name").getAsString();
            boolean correct = guess.get("correct").getAsBoolean();

            sb.append("\n").append(correct ? "\u2705" : "\u274C").append(" **").append(name).append("**\n");

            if (guess.has("comparisons") && !guess.get("comparisons").isJsonNull()) {
                JsonObject comparisons = guess.getAsJsonObject("comparisons");
                StringBuilder row = new StringBuilder();
                for (String col : columns) {
                    if (!row.isEmpty()) row.append(" | ");
                    if (comparisons.has(col) && !comparisons.get(col).isJsonNull()) {
                        JsonObject comp = comparisons.getAsJsonObject(col);
                        String result = comp.has("result") ? comp.get("result").getAsString() : "wrong";
                        String value = comp.has("value") && !comp.get("value").isJsonNull()
                                ? comp.get("value").getAsString() : "?";
                        row.append(resultEmoji(result)).append(" ").append(value);
                    } else {
                        row.append("\u2B1C ?");
                    }
                }
                sb.append(row).append("\n");
            }
        }

        String title = messages.get("guess.history_title");
        if (start > 0) {
            title += " (" + messages.get("guess.history_last", "count", 10) + ")";
        }

        String value = sb.toString();
        if (value.length() > 1024) {
            value = value.substring(0, 1020) + "...";
        }

        embed.addField(title, value, false);
    }

    private static List<String> getComparisonColumns(String mode) {
        return switch (mode) {
            case "classic" -> List.of("category", "tier", "stack_size", "form", "sink_points", "used_in_count");
            case "item" -> List.of("category", "tier", "form", "stack_size", "sink_points");
            case "building" -> List.of("category", "tier", "power_consumption");
            case "recipe" -> List.of("building", "is_alternate", "tier");
            case "creature" -> List.of("hostility", "biome", "type");
            case "milestone" -> List.of("source", "tier", "unlocked_items_count");
            default -> List.of();
        };
    }

    private static String resultEmoji(String result) {
        return switch (result) {
            case "correct" -> "\uD83D\uDFE9";
            case "higher" -> "\u2B06\uFE0F";
            case "lower" -> "\u2B07\uFE0F";
            case "partial" -> "\uD83D\uDFE7";
            default -> "\uD83D\uDFE5";
        };
    }
}
