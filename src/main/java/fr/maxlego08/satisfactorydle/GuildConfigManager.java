package fr.maxlego08.satisfactorydle;

import fr.maxlego08.sarah.RequestHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuildConfigManager {

    private static final String TABLE = "guild_configs";
    private static final String ALL_MODES = String.join(",",
            Arrays.stream(GameMode.values()).map(GameMode::getKey).toArray(String[]::new));

    private final RequestHelper requestHelper;
    private final String defaultLocale;
    private final Map<String, GuildConfig> cache = new ConcurrentHashMap<>();

    public GuildConfigManager(RequestHelper requestHelper, String defaultLocale) {
        this.requestHelper = requestHelper;
        this.defaultLocale = defaultLocale;
        loadAll();
    }

    private void loadAll() {
        List<GuildConfig> configs = requestHelper.selectAll(TABLE, GuildConfig.class);
        for (GuildConfig config : configs) {
            cache.put(config.getGuildId(), config);
        }
    }

    public GuildConfig getConfig(String guildId) {
        return cache.computeIfAbsent(guildId, id -> {
            GuildConfig config = new GuildConfig(id, defaultLocale, ALL_MODES);
            save(config);
            return config;
        });
    }

    public void setLocale(String guildId, String locale) {
        GuildConfig current = getConfig(guildId);
        GuildConfig updated = new GuildConfig(guildId, locale, current.getActiveModes());
        cache.put(guildId, updated);
        save(updated);
    }

    public void setModeEnabled(String guildId, String mode, boolean enabled) {
        GuildConfig current = getConfig(guildId);
        List<String> modes = new ArrayList<>(current.getActiveModeList());
        if (enabled && !modes.contains(mode)) {
            modes.add(mode);
        } else if (!enabled) {
            modes.remove(mode);
        }
        String activeModes = String.join(",", modes);
        GuildConfig updated = new GuildConfig(guildId, current.getLocale(), activeModes);
        cache.put(guildId, updated);
        save(updated);
    }

    private void save(GuildConfig config) {
        requestHelper.upsert(TABLE, GuildConfig.class, config);
    }
}
