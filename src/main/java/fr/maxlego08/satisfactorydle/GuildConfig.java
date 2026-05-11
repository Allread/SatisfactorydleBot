package fr.maxlego08.satisfactorydle;

import fr.maxlego08.sarah.Column;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuildConfig {

    @Column(value = "guild_id", primary = true)
    private final String guildId;

    @Column("locale")
    private final String locale;

    @Column("active_modes")
    private final String activeModes;

    public GuildConfig(String guildId, String locale, String activeModes) {
        this.guildId = guildId;
        this.locale = locale;
        this.activeModes = activeModes;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getLocale() {
        return locale;
    }

    public String getActiveModes() {
        return activeModes;
    }

    public List<String> getActiveModeList() {
        if (activeModes == null || activeModes.isEmpty()) return List.of();
        return new ArrayList<>(Arrays.asList(activeModes.split(",")));
    }

    public boolean isModeActive(String mode) {
        return getActiveModeList().contains(mode);
    }
}
