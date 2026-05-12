package fr.maxlego08.satisfactorydle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.maxlego08.satisfactorydle.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SatisfactorydleAPI {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiToken;
    private final Gson gson = new Gson();

    public SatisfactorydleAPI(Config config) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = config.apiUrl().replaceAll("/$", "");
        this.apiToken = config.apiToken();
    }

    public JsonObject getDaily(String mode, String locale) throws Exception {
        return get("/api/discord/" + mode + "/daily", locale);
    }

    public JsonObject guess(String mode, String discordUserId, int entityId, String locale) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("discord_user_id", discordUserId);
        body.addProperty("entity_id", entityId);
        return post("/api/discord/" + mode + "/guess", body, locale);
    }

    public JsonObject getState(String mode, String discordUserId, String locale) throws Exception {
        return get("/api/discord/" + mode + "/state/" + discordUserId, locale);
    }

    public JsonObject getYesterday(String mode, String locale) throws Exception {
        return get("/api/discord/" + mode + "/yesterday", locale);
    }

    private JsonObject get(String path, String locale) throws Exception {
        HttpRequest request = newRequest(path, locale).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    private JsonObject post(String path, JsonObject body, String locale) throws Exception {
        HttpRequest request = newRequest(path, locale).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    private HttpRequest.Builder newRequest(String path, String locale) {
        String separator = path.contains("?") ? "&" : "?";
        return HttpRequest.newBuilder().uri(URI.create(baseUrl + path + separator + "locale=" + locale)).header("Authorization", "Bearer " + apiToken).header("Accept", "application/json");
    }

    private JsonObject handleResponse(HttpResponse<String> response) throws ApiException {
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() >= 400) {
            String error = json.has("error") ? json.get("error").getAsString() : "Unknown error (HTTP " + response.statusCode() + ")";
            throw new ApiException(response.statusCode(), error, json);
        }
        return json;
    }
}
