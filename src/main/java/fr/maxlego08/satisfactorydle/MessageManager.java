package fr.maxlego08.satisfactorydle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager {

    private final Map<String, JsonObject> locales = new ConcurrentHashMap<>();
    private final String defaultLocale;

    public MessageManager(String defaultLocale) {
        this.defaultLocale = defaultLocale;
        loadLocale("en");
        loadLocale("fr");
    }

    private void loadLocale(String locale) {
        String path = "locales/" + locale + ".json";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            System.out.println("Warning: locale file not found: " + path);
            return;
        }
        JsonObject json = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
        locales.put(locale, json);
    }

    public String get(String locale, String key, Object... replacements) {
        JsonObject messages = locales.getOrDefault(locale, locales.get(defaultLocale));
        if (messages == null) return key;

        String[] parts = key.split("\\.");
        JsonElement current = messages;
        for (String part : parts) {
            if (current == null || !current.isJsonObject()) return key;
            current = current.getAsJsonObject().get(part);
        }

        if (current == null || !current.isJsonPrimitive()) return key;
        String result = current.getAsString();

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }

        return result;
    }

    public Messages forLocale(String locale) {
        return new Messages(this, locale);
    }
}
