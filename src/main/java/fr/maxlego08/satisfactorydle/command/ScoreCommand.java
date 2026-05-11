package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.ApiException;
import fr.maxlego08.satisfactorydle.GameMode;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class ScoreCommand {

    private final SatisfactorydleAPI api;

    public ScoreCommand(SatisfactorydleAPI api) {
        this.api = api;
    }

    public void execute(SlashCommandInteractionEvent event, String mode, String locale) throws Exception {
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
}
