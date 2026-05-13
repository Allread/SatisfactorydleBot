package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.Messages;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.util.Map;
import java.util.concurrent.*;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class QuizManager {

    private final SatisfactorydleAPI api;
    private final Map<String, QuizSession> activeQuizzes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public QuizManager(SatisfactorydleAPI api) {
        this.api = api;
    }

    public boolean hasActiveQuiz(String channelId) {
        return activeQuizzes.containsKey(channelId);
    }

    public void startQuiz(String channelId, long quizId, JsonObject entity, MessageChannelUnion channel, Messages messages) {
        String answer = entity.get("name").getAsString();
        QuizSession session = new QuizSession(answer, entity, quizId);

        ScheduledFuture<?> hintTask = scheduler.schedule(() -> sendHint(channelId, channel, messages), 30, TimeUnit.SECONDS);
        session.setHintTask(hintTask);

        ScheduledFuture<?> timeout = scheduler.schedule(() -> endQuiz(channelId, channel, null, messages), 60, TimeUnit.SECONDS);
        session.setTimeout(timeout);

        activeQuizzes.put(channelId, session);
        System.out.println("[Quiz] Started in channel " + channelId + " — answer: " + answer);
    }

    public void handleMessage(String channelId, String content, User author, MessageChannelUnion channel, Messages messages) {
        QuizSession session = activeQuizzes.get(channelId);
        if (session == null) return;

        if (content.equalsIgnoreCase(session.getAnswer())) {
            session.getTimeout().cancel(false);
            if (session.getHintTask() != null) session.getHintTask().cancel(false);
            endQuiz(channelId, channel, author, messages);
        }
    }

    private void sendHint(String channelId, MessageChannelUnion channel, Messages messages) {
        QuizSession session = activeQuizzes.get(channelId);
        if (session == null) return;

        JsonObject entity = session.getEntity();
        if (!hasValue(entity, "description")) return;

        String desc = entity.get("description").getAsString();
        if (desc.length() > 200) desc = desc.substring(0, 200) + "...";

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(COLOR_WARNING)
                .setTitle(messages.get("quiz.hint_title"))
                .addField(messages.get("field.description"), desc, false);

        applyFooter(embed, messages.get("quiz.hint_footer"));
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void endQuiz(String channelId, MessageChannelUnion channel, User winner, Messages messages) {
        QuizSession session = activeQuizzes.remove(channelId);
        if (session == null) return;

        JsonObject entity = session.getEntity();
        String name = entity.get("name").getAsString();

        long elapsed = (System.currentTimeMillis() - session.getStartTime()) / 1000;

        if (winner != null) {
            System.out.println("[Quiz] Channel " + channelId + " — " + winner.getName() + " found \"" + name + "\" in " + elapsed + "s");
            api.quizComplete(session.getQuizId(), true, winner.getId());
        } else {
            System.out.println("[Quiz] Channel " + channelId + " — timeout, answer was \"" + name + "\"");
            api.quizComplete(session.getQuizId(), false, null);
        }

        EmbedBuilder embed;
        if (winner != null) {
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
            embed.setImage(entity.get("image_url").getAsString());
        }

        applyFooter(embed, null);
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void shutdown() {
        activeQuizzes.values().forEach(s -> {
            if (s.getTimeout() != null) s.getTimeout().cancel(false);
            if (s.getHintTask() != null) s.getHintTask().cancel(false);
        });
        activeQuizzes.clear();
        scheduler.shutdown();
    }
}
