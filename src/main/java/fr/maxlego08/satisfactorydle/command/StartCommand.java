package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.ApiException;
import fr.maxlego08.satisfactorydle.GameMode;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class StartCommand {

    private final SatisfactorydleAPI api;
    private final EntityCache entityCache;

    public StartCommand(SatisfactorydleAPI api, EntityCache entityCache) {
        this.api = api;
        this.entityCache = entityCache;
    }

    public void execute(SlashCommandInteractionEvent event, String mode, String locale) throws Exception {
        JsonObject daily = api.getDaily(mode, locale);
        entityCache.store(entityCache.cacheKey(mode, locale), daily.getAsJsonArray("entities"));

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
}
