package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.Messages;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class QuizCommand {

    private final SatisfactorydleAPI api;
    private final QuizManager quizManager;

    public QuizCommand(SatisfactorydleAPI api, QuizManager quizManager) {
        this.api = api;
        this.quizManager = quizManager;
    }

    public void execute(SlashCommandInteractionEvent event, String locale, Messages messages) throws Exception {
        String channelId = event.getChannel().getId();

        if (quizManager.hasActiveQuiz(channelId)) {
            event.deferReply().setEphemeral(true).queue();
            replyError(event, messages, messages.get("quiz.already_active"));
            return;
        }

        event.deferReply().setEphemeral(false).queue();

        JsonObject result = api.quizRandom(locale);
        JsonObject entity = result.getAsJsonObject("entity");

        quizManager.startQuiz(channelId, entity, event.getChannel(), messages);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(COLOR_INFO)
                .setTitle(messages.get("quiz.title"))
                .setDescription(messages.get("quiz.description"));

        addFieldIfPresent(embed, messages.get("field.category"), entity, "category", true);
        addFieldIfPresent(embed, messages.get("field.tier"), entity, "tier", true);
        addFieldIfPresent(embed, messages.get("field.form"), entity, "form", true);

        applyFooter(embed, messages.get("quiz.footer"));
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
}
