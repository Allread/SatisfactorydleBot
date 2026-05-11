package fr.maxlego08.satisfactorydle.command;

import fr.maxlego08.satisfactorydle.GameMode;
import fr.maxlego08.satisfactorydle.GuildConfig;
import fr.maxlego08.satisfactorydle.GuildConfigManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.*;

public class ConfigCommand {

    private final GuildConfigManager guildConfigManager;

    public ConfigCommand(GuildConfigManager guildConfigManager) {
        this.guildConfigManager = guildConfigManager;
    }

    public void execute(SlashCommandInteractionEvent event, String subcommand) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            replyError(event, "You need the **Manage Server** permission to configure the bot.");
            return;
        }

        String guildId = event.getGuild().getId();

        try {
            switch (subcommand) {
                case "language" -> handleLanguage(event, guildId);
                case "mode" -> handleMode(event, guildId);
                case "show" -> handleShow(event, guildId);
            }
        } catch (Exception e) {
            replyError(event, "An unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleLanguage(SlashCommandInteractionEvent event, String guildId) {
        String locale = event.getOption("locale", OptionMapping::getAsString);
        guildConfigManager.setLocale(guildId, locale);

        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setTitle("Configuration Updated")
                        .setDescription("Language set to **" + locale + "**")
                        .build()
        ).queue();
    }

    private void handleMode(SlashCommandInteractionEvent event, String guildId) {
        String mode = event.getOption("mode", OptionMapping::getAsString);
        boolean enabled = event.getOption("enabled", OptionMapping::getAsBoolean);
        guildConfigManager.setModeEnabled(guildId, mode, enabled);

        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_SUCCESS)
                        .setTitle("Configuration Updated")
                        .setDescription("Mode **" + GameMode.fromKey(mode).getDisplay()
                                + "** is now " + (enabled ? "enabled" : "disabled"))
                        .build()
        ).queue();
    }

    private void handleShow(SlashCommandInteractionEvent event, String guildId) {
        GuildConfig config = guildConfigManager.getConfig(guildId);

        StringBuilder modes = new StringBuilder();
        for (GameMode gm : GameMode.values()) {
            boolean active = config.isModeActive(gm.getKey());
            modes.append(active ? "✅" : "❌").append(" ").append(gm.getDisplay()).append("\n");
        }

        event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                        .setColor(COLOR_INFO)
                        .setTitle("Server Configuration")
                        .addField("Language", config.getLocale(), true)
                        .addField("Active Modes", modes.toString(), false)
                        .build()
        ).queue();
    }
}
