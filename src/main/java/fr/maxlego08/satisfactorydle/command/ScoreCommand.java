package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.ApiException;
import fr.maxlego08.satisfactorydle.config.GameMode;
import fr.maxlego08.satisfactorydle.Messages;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class ScoreCommand {

    private final SatisfactorydleAPI api;

    public ScoreCommand(SatisfactorydleAPI api) {
        this.api = api;
    }

    public void execute(SlashCommandInteractionEvent event, String mode, String locale, Messages messages) throws Exception {
        String userId = event.getUser().getId();
        String modeDisplay = GameMode.fromKey(mode).getDisplay();

        try {
            JsonObject state = api.getState(mode, userId, locale);

            boolean won = state.get("won").getAsBoolean();
            int totalGuesses = state.get("total_guesses").getAsInt();
            int gameId = state.get("game_id").getAsInt();

            EmbedBuilder embed = new EmbedBuilder().setColor(won ? COLOR_SUCCESS : COLOR_INFO).setTitle(messages.get("score.title", "mode", modeDisplay, "id", gameId));

            embed.addField(messages.get("score.status"), won ? messages.get("score.won") : messages.get("score.in_progress"), true);
            embed.addField(messages.get("score.guesses_field"), String.valueOf(totalGuesses), true);

            if (state.has("guesses") && state.get("guesses").isJsonArray()) {
                JsonArray guesses = state.getAsJsonArray("guesses");
                if (!guesses.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < guesses.size(); i++) {
                        JsonObject g = guesses.get(i).getAsJsonObject();
                        boolean correct = g.get("correct").getAsBoolean();
                        sb.append(i + 1).append(". ").append(correct ? "✅" : "❌").append(" ").append(g.get("name").getAsString()).append("\n");
                    }
                    embed.addField(messages.get("score.your_guesses"), sb.toString(), false);
                }
            }

            if (state.has("hints") && !state.get("hints").isJsonNull()) {
                addHintFields(embed, state.getAsJsonObject("hints"), messages);
            }

            if (won && state.has("answer") && !state.get("answer").isJsonNull()) {
                JsonObject answer = state.getAsJsonObject("answer");
                if (hasValue(answer, "image_url")) {
                    embed.setThumbnail(answer.get("image_url").getAsString());
                }
            }

            applyFooter(embed, won ? messages.get("score.footer_won") : messages.get("score.footer_playing"));
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        } catch (ApiException e) {
            if (e.getStatusCode() == 404) {
                EmbedBuilder noGameEmbed = new EmbedBuilder().setColor(COLOR_INFO).setTitle(messages.get("score.title_no_game", "mode", modeDisplay)).setDescription(messages.get("score.no_game"));
                applyFooter(noGameEmbed, null);
                event.getHook().editOriginalEmbeds(noGameEmbed.build()).queue();
            } else {
                throw e;
            }
        }
    }
}
