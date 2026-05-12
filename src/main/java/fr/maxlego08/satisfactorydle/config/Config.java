package fr.maxlego08.satisfactorydle.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record Config(String discordToken, String apiUrl, String apiToken, String locale) {

    public static Config load(Path path) throws IOException {
        if (!Files.exists(path)) {
            JsonObject json = new JsonObject();
            json.addProperty("discord_token", "YOUR_DISCORD_BOT_TOKEN");
            json.addProperty("api_url", "https://satisfactorydle.net");
            json.addProperty("api_token", "YOUR_API_TOKEN");
            json.addProperty("locale", "fr");
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            System.out.println("Default config.json created. Please fill in your tokens and restart.");
            System.exit(0);
        }

        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

        for (String key : new String[]{"discord_token", "api_url", "api_token"}) {
            if (!json.has(key) || json.get(key).isJsonNull()) {
                throw new IOException("Missing required field '" + key + "' in config.json");
            }
        }

        String locale = json.has("locale") ? json.get("locale").getAsString() : "fr";
        return new Config(json.get("discord_token").getAsString(), json.get("api_url").getAsString(), json.get("api_token").getAsString(), locale);
    }
}
