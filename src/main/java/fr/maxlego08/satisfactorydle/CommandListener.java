package fr.maxlego08.satisfactorydle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CommandListener extends ListenerAdapter {

    private static final Color COLOR_SUCCESS = new Color(0x57F287);
    private static final Color COLOR_ERROR = new Color(0xED4245);
    private static final Color COLOR_WARNING = new Color(0xFEE75C);
    private static final Color COLOR_INFO = new Color(0x5865F2);

    private final SatisfactorydleAPI api;
    private final GuildConfigManager guildConfigManager;
    private final String defaultLocale;
    private final Map<String, List<EntityEntry>> entityCache = new ConcurrentHashMap<>();

    record EntityEntry(int id, String name) {}

    public CommandListener(SatisfactorydleAPI api, GuildConfigManager guildConfigManager, String defaultLocale) {
        this.api = api;
        this.guildConfigManager = guildConfigManager;
        this.defaultLocale = defaultLocale;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("sfdle")) return;

        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        event.deferReply().setEphemeral(true).queue();

        if ("config".equals(group)) {
            handleConfig(event, subcommand);
            return;
        }

        String locale = getLocale(event);
        String mode = event.getOption("mode", "item", OptionMapping::getAsString);

        // Check if mode is active on this server
        GuildConfig guildConfig = getGuildConfig(event);
        if (guildConfig != null && !guildConfig.isModeActive(mode)) {
            replyError(event, "The **" + GameMode.fromKey(mode).getDisplay()
                    + "** mode is disabled on this server.\nAn administrator can enable it with `/sfdle config mode`.");
            return;
        }

        try {
            switch (subcommand) {
                case "start" -> handleStart(event, mode, locale);
                case "guess" -> handleGuess(event, mode, locale);
                case "score" -> handleScore(event, mode, locale);
            }
        } catch (ApiException e) {
            replyError(event, e.getMessage());
        } catch (Exception e) {
            replyError(event, "An unexpected error occurred: " + e.getMessage());
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("sfdle")) return;
        if (!"entity".equals(event.getFocusedOption().getName())) return;

        OptionMapping modeMapping = event.getOption("mode");
        String mode = modeMapping != null ? modeMapping.getAsString() : "item";

        String locale = defaultLocale;
        if (event.getGuild() != null) {
            locale = guildConfigManager.getConfig(event.getGuild().getId()).getLocale();
        }

        String key = cacheKey(mode, locale);
        ensureCacheAsync(mode, locale);

        List<EntityEntry> entities = entityCache.get(key);
        if (entities == null) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        String typed = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = entities.stream()
                .filter(e -> e.name().toLowerCase().contains(typed))
                .sorted((a, b) -> {
                    boolean aStarts = a.name().toLowerCase().startsWith(typed);
                    boolean bStarts = b.name().toLowerCase().startsWith(typed);
                    if (aStarts != bStarts) return aStarts ? -1 : 1;
                    return a.name().compareToIgnoreCase(b.name());
                })
                .limit(25)
                .map(e -> new Command.Choice(e.name(), e.name()))
                .toList();

        event.replyChoices(choices).queue();
    }

    // --- Config commands ---

    private void handleConfig(SlashCommandInteractionEvent event, String subcommand) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            replyError(event, "You need the **Manage Server** permission to configure the bot.");
            return;
        }

        String guildId = event.getGuild().getId();

        try {
            switch (subcommand) {
                case "language" -> handleConfigLanguage(event, guildId);
                case "mode" -> handleConfigMode(event, guildId);
                case "show" -> handleConfigShow(event, guildId);
            }
        } catch (Exception e) {
            replyError(event, "An unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleConfigLanguage(SlashCommandInteractionEvent event, String guildId) {
        String locale = event.getOption("locale", OptionMapping::getAsString);
        guildConfigManager.setLocale(guildId, locale);

        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setTitle("Configuration Updated")
                        .setDescription("Language set to **" + locale + "**")
                        .build()
        ).queue();
    }

    private void handleConfigMode(SlashCommandInteractionEvent event, String guildId) {
        String mode = event.getOption("mode", OptionMapping::getAsString);
        boolean enabled = event.getOption("enabled", OptionMapping::getAsBoolean);
        guildConfigManager.setModeEnabled(guildId, mode, enabled);

        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setTitle("Configuration Updated")
                        .setDescription("Mode **" + GameMode.fromKey(mode).getDisplay()
                                + "** is now " + (enabled ? "enabled" : "disabled"))
                        .build()
        ).queue();
    }

    private void handleConfigShow(SlashCommandInteractionEvent event, String guildId) {
        GuildConfig config = guildConfigManager.getConfig(guildId);

        StringBuilder modes = new StringBuilder();
        for (GameMode gm : GameMode.values()) {
            boolean active = config.isModeActive(gm.getKey());
            modes.append(active ? "✅" : "❌").append(" ").append(gm.getDisplay()).append("\n");
        }

        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_INFO)
                        .setTitle("Server Configuration")
                        .addField("Language", config.getLocale(), true)
                        .addField("Active Modes", modes.toString(), false)
                        .build()
        ).queue();
    }

    // --- Game command handlers ---

    private void handleStart(SlashCommandInteractionEvent event, String mode, String locale) throws Exception {
        JsonObject daily = api.getDaily(mode, locale);
        cacheEntities(cacheKey(mode, locale), daily.getAsJsonArray("entities"));

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(COLOR_INFO)
                .setTitle("Satisfactorydle - " + GameMode.fromKey(mode).getDisplay());

        try {
            JsonObject yesterday = api.getYesterday(mode, locale);
            JsonObject answer = yesterday.getAsJsonObject("answer");
            int gameId = yesterday.get("game_id").getAsInt();
            String name = answer.get("name").getAsString();

            StringBuilder text = new StringBuilder("**").append(name).append("**");
            if (hasValue(answer, "description")) {
                text.append("\n").append(answer.get("description").getAsString());
            }
            embed.addField("Yesterday's Answer (#" + gameId + ")", text.toString(), false);

            if (hasValue(answer, "image_url")) {
                embed.setThumbnail(answer.get("image_url").getAsString());
            }
        } catch (ApiException e) {
            if (e.getStatusCode() == 404) {
                embed.addField("Yesterday", "No puzzle from yesterday.", false);
            } else {
                throw e;
            }
        }

        int gameId = daily.get("game_id").getAsInt();
        JsonObject clue = daily.getAsJsonObject("clue");

        StringBuilder todayText = new StringBuilder()
                .append("Game **#").append(gameId).append("** is ready!\n")
                .append("Use `/sfdle guess` to make your first guess.");

        if (hasValue(clue, "description")) {
            todayText.append("\n\n**Clue:** ").append(clue.get("description").getAsString());
        }

        embed.addField("Today's Challenge", todayText.toString(), false);

        if (hasValue(clue, "image_url")) {
            embed.setImage(clue.get("image_url").getAsString());
        }

        embed.setFooter("Good luck!");
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleGuess(SlashCommandInteractionEvent event, String mode, String locale) throws Exception {
        String entityName = event.getOption("entity", OptionMapping::getAsString);
        if (entityName == null) return;

        String key = cacheKey(mode, locale);
        ensureCache(mode, locale);

        List<EntityEntry> entities = entityCache.get(key);
        EntityEntry entity = entities != null
                ? entities.stream().filter(e -> e.name().equalsIgnoreCase(entityName)).findFirst().orElse(null)
                : null;

        if (entity == null) {
            replyError(event, "\"" + entityName + "\" is not in the list.\nUse autocomplete to select a valid entity.");
            return;
        }

        String userId = event.getUser().getId();

        try {
            JsonObject result = api.guess(mode, userId, entity.id(), locale);

            boolean correct = result.get("correct").getAsBoolean();
            int totalGuesses = result.get("total_guesses").getAsInt();

            if (correct) {
                JsonObject answer = result.getAsJsonObject("answer");
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setTitle("Correct!")
                        .setDescription("You found **" + answer.get("name").getAsString()
                                + "** in **" + totalGuesses + "** guess" + (totalGuesses > 1 ? "es" : "") + "!");

                addAnswerFields(embed, answer, mode);

                if (hasValue(answer, "image_url")) {
                    embed.setThumbnail(answer.get("image_url").getAsString());
                }

                embed.setFooter("Congratulations! Come back tomorrow for a new challenge.");
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } else {
                int guessNumber = result.get("guess_number").getAsInt();
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(COLOR_ERROR)
                        .setTitle("Wrong - " + entityName)
                        .setDescription("Guess **#" + guessNumber + "** - Not the right answer!");

                if (result.has("hints") && !result.get("hints").isJsonNull()) {
                    addHintFields(embed, result.getAsJsonObject("hints"));
                }

                embed.setFooter("Use /sfdle guess to try again!");
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            }
        } catch (ApiException e) {
            if (e.getStatusCode() == 409) {
                JsonObject body = e.getBody();
                boolean won = body.has("won") && body.get("won").getAsBoolean();

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(COLOR_WARNING)
                        .setTitle(won ? "Already Won!" : "Duplicate Guess")
                        .setDescription(e.getMessage());

                if (won && body.has("total_guesses")) {
                    embed.addField("Total Guesses", String.valueOf(body.get("total_guesses").getAsInt()), true);
                }

                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } else {
                throw e;
            }
        }
    }

    private void handleScore(SlashCommandInteractionEvent event, String mode, String locale) throws Exception {
        String userId = event.getUser().getId();

        try {
            JsonObject state = api.getState(mode, userId, locale);

            boolean won = state.get("won").getAsBoolean();
            int totalGuesses = state.get("total_guesses").getAsInt();
            int gameId = state.get("game_id").getAsInt();

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(won ? COLOR_SUCCESS : COLOR_INFO)
                    .setTitle("Score - " + GameMode.fromKey(mode).getDisplay() + " #" + gameId);

            embed.addField("Status", won ? "Won!" : "In Progress", true);
            embed.addField("Guesses", String.valueOf(totalGuesses), true);

            if (state.has("guesses") && state.get("guesses").isJsonArray()) {
                JsonArray guesses = state.getAsJsonArray("guesses");
                if (!guesses.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < guesses.size(); i++) {
                        JsonObject g = guesses.get(i).getAsJsonObject();
                        boolean correct = g.get("correct").getAsBoolean();
                        sb.append(i + 1).append(". ")
                                .append(correct ? "✅" : "❌").append(" ")
                                .append(g.get("name").getAsString()).append("\n");
                    }
                    embed.addField("Your Guesses", sb.toString(), false);
                }
            }

            if (state.has("hints") && !state.get("hints").isJsonNull()) {
                addHintFields(embed, state.getAsJsonObject("hints"));
            }

            if (won && state.has("answer") && !state.get("answer").isJsonNull()) {
                JsonObject answer = state.getAsJsonObject("answer");
                if (hasValue(answer, "image_url")) {
                    embed.setThumbnail(answer.get("image_url").getAsString());
                }
            }

            embed.setFooter(won ? "Come back tomorrow!" : "Use /sfdle guess to continue!");
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        } catch (ApiException e) {
            if (e.getStatusCode() == 404) {
                event.getHook().editOriginalEmbeds(
                        new EmbedBuilder()
                                .setColor(COLOR_INFO)
                                .setTitle("Score - " + GameMode.fromKey(mode).getDisplay())
                                .setDescription("You haven't started today's challenge yet!\nUse `/sfdle start` to see today's puzzle.")
                                .build()
                ).queue();
            } else {
                throw e;
            }
        }
    }

    // --- Embed field helpers ---

    private void addAnswerFields(EmbedBuilder embed, JsonObject answer, String mode) {
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

    private void addHintFields(EmbedBuilder embed, JsonObject hints) {
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

    private void addInputItems(EmbedBuilder embed, JsonObject json) {
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

    // --- Utility methods ---

    private String getLocale(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return defaultLocale;
        return guildConfigManager.getConfig(event.getGuild().getId()).getLocale();
    }

    private GuildConfig getGuildConfig(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return null;
        return guildConfigManager.getConfig(event.getGuild().getId());
    }

    private void addFieldIfPresent(EmbedBuilder embed, String label, JsonObject json, String key, boolean inline) {
        if (hasValue(json, key)) {
            embed.addField(label, jsonToString(json.get(key)), inline);
        }
    }

    private boolean hasValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    private String jsonToString(JsonElement element) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean() ? "Yes" : "No";
            if (prim.isNumber()) return prim.getAsNumber().toString();
            return prim.getAsString();
        }
        return element.toString();
    }

    private String formatJsonArray(JsonArray array) {
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

    private String formatLabel(String key) {
        String result = key.replace("_", " ");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    private void replyError(SlashCommandInteractionEvent event, String message) {
        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_ERROR)
                        .setTitle("Error")
                        .setDescription(message)
                        .build()
        ).queue();
    }

    // --- Entity cache (keyed by mode:locale) ---

    private String cacheKey(String mode, String locale) {
        return mode + ":" + locale;
    }

    private void cacheEntities(String key, JsonArray entitiesArray) {
        List<EntityEntry> entries = new ArrayList<>();
        for (JsonElement element : entitiesArray) {
            JsonObject obj = element.getAsJsonObject();
            entries.add(new EntityEntry(obj.get("id").getAsInt(), obj.get("name").getAsString()));
        }
        entityCache.put(key, entries);
    }

    private void ensureCache(String mode, String locale) throws Exception {
        String key = cacheKey(mode, locale);
        if (!entityCache.containsKey(key)) {
            JsonObject daily = api.getDaily(mode, locale);
            cacheEntities(key, daily.getAsJsonArray("entities"));
        }
    }

    private void ensureCacheAsync(String mode, String locale) {
        String key = cacheKey(mode, locale);
        if (!entityCache.containsKey(key)) {
            CompletableFuture.runAsync(() -> {
                try {
                    JsonObject daily = api.getDaily(mode, locale);
                    cacheEntities(key, daily.getAsJsonArray("entities"));
                } catch (Exception ignored) {
                }
            });
        }
    }
}
