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

public final class EmbedHelper {

    public static final Color COLOR_SUCCESS = new Color(0x57F287);
    public static final Color COLOR_ERROR = new Color(0xED4245);
    public static final Color COLOR_WARNING = new Color(0xFEE75C);
    public static final Color COLOR_INFO = new Color(0x5865F2);

    private static final String FOOTER_ICON = "https://satisfactorydle.net/favicon-96x96.png";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
}
