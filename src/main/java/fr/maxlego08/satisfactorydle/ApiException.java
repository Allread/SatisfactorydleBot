package fr.maxlego08.satisfactorydle;

import com.google.gson.JsonObject;

public class ApiException extends Exception {

    private final int statusCode;
    private final JsonObject body;

    public ApiException(int statusCode, String message, JsonObject body) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public JsonObject getBody() {
        return body;
    }
}
