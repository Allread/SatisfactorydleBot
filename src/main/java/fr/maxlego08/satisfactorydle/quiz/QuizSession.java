package fr.maxlego08.satisfactorydle.quiz;

import com.google.gson.JsonObject;

import java.util.concurrent.ScheduledFuture;

public class QuizSession {

    private final String answer;
    private final JsonObject entity;
    private final long startTime;
    private final long quizId;
    private final String imageUrl;

    // Contexte pour relancer automatiquement un quiz
    private final String guildId;
    private final String starterUserId;
    private final String locale;

    private ScheduledFuture<?> timeout;
    private ScheduledFuture<?> hintTask;

    public QuizSession(String answer, JsonObject entity, long quizId, String imageUrl,
                       String guildId, String starterUserId, String locale) {
        this.answer = answer;
        this.entity = entity;
        this.quizId = quizId;
        this.imageUrl = imageUrl;
        this.guildId = guildId;
        this.starterUserId = starterUserId;
        this.locale = locale;
        this.startTime = System.currentTimeMillis();
    }

    public String getAnswer() {
        return this.answer;
    }

    public JsonObject getEntity() {
        return this.entity;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public ScheduledFuture<?> getTimeout() {
        return this.timeout;
    }

    public void setTimeout(ScheduledFuture<?> timeout) {
        this.timeout = timeout;
    }

    public ScheduledFuture<?> getHintTask() {
        return this.hintTask;
    }

    public void setHintTask(ScheduledFuture<?> hintTask) {
        this.hintTask = hintTask;
    }

    public long getQuizId() {
        return this.quizId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public String getLocale() {
        return locale;
    }
}