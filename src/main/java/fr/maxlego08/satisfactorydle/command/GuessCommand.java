package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.ApiException;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class GuessCommand {

    private final SatisfactorydleAPI api;
    private final EntityCache entityCache;

    public GuessCommand(SatisfactorydleAPI api, EntityCache entityCache) {
        this.api = api;
        this.entityCache = entityCache;
    }

    public void execute(SlashCommandInteractionEvent event, String mode, String locale) throws Exception {
        String entityName = event.getOption("entity", OptionMapping::getAsString);
        if (entityName == null) return;

        String key = entityCache.cacheKey(mode, locale);
        entityCache.ensureCache(mode, locale);

        List<EntityCache.EntityEntry> entities = entityCache.get(key);
        EntityCache.EntityEntry entity = entities != null
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
}
