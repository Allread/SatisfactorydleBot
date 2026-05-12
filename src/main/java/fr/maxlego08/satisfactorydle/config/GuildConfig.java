package fr.maxlego08.satisfactorydle.config;

import fr.maxlego08.sarah.Column;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record GuildConfig(@Column(value = "guild_id", primary = true) String guildId, @Column("locale") String locale,
                          @Column("active_modes") String activeModes) {

    public GuildConfig(String guildId, String locale, String activeModes) {
        this.guildId = guildId;
        this.locale = locale;
        this.activeModes = activeModes;
    }

    @Override
    public String guildId() {
        return guildId;
    }

    @Override
    public String locale() {
        return locale;
    }

    @Override
    public String activeModes() {
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
