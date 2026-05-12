package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonObject;

import java.util.concurrent.ScheduledFuture;

public class QuizSession {

    private final String answer;
    private final JsonObject entity;
    private final long startTime;
    private ScheduledFuture<?> timeout;

    public QuizSession(String answer, JsonObject entity) {
        this.answer = answer;
        this.entity = entity;
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
}
