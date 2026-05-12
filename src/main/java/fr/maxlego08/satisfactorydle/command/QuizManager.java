package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.util.Map;
import java.util.concurrent.*;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class QuizManager {

    private final Map<String, QuizSession> activeQuizzes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public boolean hasActiveQuiz(String channelId) {
        return activeQuizzes.containsKey(channelId);
    }

    public void startQuiz(String channelId, JsonObject entity, MessageChannelUnion channel, Messages messages) {
        String answer = entity.get("name").getAsString();
        QuizSession session = new QuizSession(answer, entity);

        ScheduledFuture<?> timeout = scheduler.schedule(() -> endQuiz(channelId, channel, null, messages), 60, TimeUnit.SECONDS);
        session.setTimeout(timeout);

        activeQuizzes.put(channelId, session);
    }

    public void handleMessage(String channelId, String content, User author, MessageChannelUnion channel, Messages messages) {
        QuizSession session = activeQuizzes.get(channelId);
        if (session == null) return;

        if (content.equalsIgnoreCase(session.getAnswer())) {
            session.getTimeout().cancel(false);
            endQuiz(channelId, channel, author, messages);
        }
    }

    private void endQuiz(String channelId, MessageChannelUnion channel, User winner, Messages messages) {
        QuizSession session = activeQuizzes.remove(channelId);
        if (session == null) return;

        JsonObject entity = session.getEntity();
        String name = entity.get("name").getAsString();

        EmbedBuilder embed;
        if (winner != null) {
            long elapsed = (System.currentTimeMillis() - session.getStartTime()) / 1000;
            embed = new EmbedBuilder()
                    .setColor(COLOR_SUCCESS)
                    .setTitle(messages.get("quiz.winner_title"))
                    .setDescription(messages.get("quiz.winner_description", "user", winner.getAsMention(), "name", name, "time", elapsed));
        } else {
            embed = new EmbedBuilder()
                    .setColor(COLOR_ERROR)
                    .setTitle(messages.get("quiz.timeout_title"))
                    .setDescription(messages.get("quiz.timeout_description", "name", name));
        }

        if (hasValue(entity, "image_url")) {
            embed.setThumbnail(entity.get("image_url").getAsString());
        }

        applyFooter(embed, null);
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void shutdown() {
        activeQuizzes.values().forEach(s -> {
            if (s.getTimeout() != null) s.getTimeout().cancel(false);
        });
        activeQuizzes.clear();
        scheduler.shutdown();
    }
}
