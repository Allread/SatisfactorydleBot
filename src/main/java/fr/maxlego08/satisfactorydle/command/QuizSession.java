package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;

import java.util.concurrent.ScheduledFuture;

public class QuizSession {

    private final String answer;
    private final JsonObject entity;
    private final long startTime;
    private final long quizId;
    private ScheduledFuture<?> timeout;
    private ScheduledFuture<?> hintTask;

    public QuizSession(String answer, JsonObject entity, long quizId) {
        this.answer = answer;
        this.entity = entity;
        this.quizId = quizId;
        this.startTime = System.currentTimeMillis();
    }

    public String getAnswer() {
        return answer;
    }

    public JsonObject getEntity() {
        return entity;
    }

    public long getStartTime() {
        return startTime;
    }

    public ScheduledFuture<?> getTimeout() {
        return timeout;
    }

    public void setTimeout(ScheduledFuture<?> timeout) {
        this.timeout = timeout;
    }

    public ScheduledFuture<?> getHintTask() {
        return hintTask;
    }

    public void setHintTask(ScheduledFuture<?> hintTask) {
        this.hintTask = hintTask;
    }

    public long getQuizId() {
        return quizId;
    }
}
