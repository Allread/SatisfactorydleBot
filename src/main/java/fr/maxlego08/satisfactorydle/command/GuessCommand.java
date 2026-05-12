package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.ApiException;
import fr.maxlego08.satisfactorydle.Messages;
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

    public void execute(SlashCommandInteractionEvent event, String mode, String locale, Messages messages) throws Exception {
        String entityName = event.getOption("entity", OptionMapping::getAsString);
        if (entityName == null) return;

        String key = entityCache.cacheKey(mode, locale);
        entityCache.ensureCache(mode, locale);

        List<EntityCache.EntityEntry> entities = entityCache.get(key);
        EntityCache.EntityEntry entity = entities != null ? entities.stream().filter(e -> e.name().equalsIgnoreCase(entityName)).findFirst().orElse(null) : null;

        if (entity == null) {
            replyError(event, messages, messages.get("error.entity_not_found", "name", entityName));
            return;
        }

        String userId = event.getUser().getId();

        try {
            JsonObject result = api.guess(mode, userId, entity.id(), locale);

            boolean correct = result.get("correct").getAsBoolean();
            int totalGuesses = result.get("total_guesses").getAsInt();

            // Fetch full game state for guess history
            JsonObject state = api.getState(mode, userId, locale);

            if (correct) {
                JsonObject answer = result.getAsJsonObject("answer");
                String guessWord = totalGuesses > 1 ? messages.get("common.guesses") : messages.get("common.guess");
                EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_SUCCESS).setTitle(messages.get("guess.correct_title")).setDescription(messages.get("guess.correct_description", "name", answer.get("name").getAsString(), "count", totalGuesses, "guess_word", guessWord));

                if (state.has("guesses") && state.get("guesses").isJsonArray()) {
                    addGuessHistory(embed, state.getAsJsonArray("guesses"), mode, messages);
                }

                addAnswerFields(embed, answer, mode, messages);

                if (hasValue(answer, "image_url")) {
                    embed.setThumbnail(answer.get("image_url").getAsString());
                }

                applyFooter(embed, messages.get("guess.correct_footer"));
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } else {
                int guessNumber = result.get("guess_number").getAsInt();
                EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_ERROR).setTitle(messages.get("guess.wrong_title", "name", entityName)).setDescription(messages.get("guess.wrong_description", "number", guessNumber));

                if (state.has("guesses") && state.get("guesses").isJsonArray()) {
                    addGuessHistory(embed, state.getAsJsonArray("guesses"), mode, messages);
                }

                if (result.has("hints") && !result.get("hints").isJsonNull()) {
                    addHintFields(embed, result.getAsJsonObject("hints"), messages);
                }

                applyFooter(embed, messages.get("guess.wrong_footer"));
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            }
        } catch (ApiException e) {
            if (e.getStatusCode() == 409) {
                JsonObject body = e.getBody();
                boolean won = body.has("won") && body.get("won").getAsBoolean();

                EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_WARNING).setTitle(won ? messages.get("guess.already_won_title") : messages.get("guess.duplicate_title")).setDescription(e.getMessage());

                if (won && body.has("total_guesses")) {
                    embed.addField(messages.get("field.total_guesses"), String.valueOf(body.get("total_guesses").getAsInt()), true);
                }

                applyFooter(embed, null);
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } else {
                throw e;
            }
        }
    }
}
