package fr.maxlego08.satisfactorydle.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.maxlego08.satisfactorydle.SatisfactorydleAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class EntityCache {

    private final SatisfactorydleAPI api;
    private final Map<String, List<EntityEntry>> cache = new ConcurrentHashMap<>();
    public EntityCache(SatisfactorydleAPI api) {
        this.api = api;
    }

    public String cacheKey(String mode, String locale) {
        return mode + ":" + locale;
    }

    public List<EntityEntry> get(String key) {
        return cache.get(key);
    }

    public void store(String key, JsonArray entitiesArray) {
        List<EntityEntry> entries = new ArrayList<>();
        for (JsonElement element : entitiesArray) {
            JsonObject obj = element.getAsJsonObject();
            entries.add(new EntityEntry(obj.get("id").getAsInt(), obj.get("name").getAsString()));
        }
        cache.put(key, entries);
    }

    public void ensureCache(String mode, String locale) throws Exception {
        String key = cacheKey(mode, locale);
        if (!cache.containsKey(key)) {
            JsonObject daily = api.getDaily(mode, locale);
            store(key, daily.getAsJsonArray("entities"));
        }
    }

    public void ensureCacheAsync(String mode, String locale) {
        String key = cacheKey(mode, locale);
        if (!cache.containsKey(key)) {
            CompletableFuture.runAsync(() -> {
                try {
                    JsonObject daily = api.getDaily(mode, locale);
                    store(key, daily.getAsJsonArray("entities"));
                } catch (Exception ignored) {
                }
            });
        }
    }

    public record EntityEntry(int id, String name) {
    }
}
