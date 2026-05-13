package fr.maxlego08.satisfactorydle.command;

import fr.maxlego08.satisfactorydle.config.GameMode;
import fr.maxlego08.satisfactorydle.config.GuildConfig;
import fr.maxlego08.satisfactorydle.config.GuildConfigManager;
import fr.maxlego08.satisfactorydle.Messages;
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

    public void execute(SlashCommandInteractionEvent event, String subcommand, Messages messages) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            replyError(event, messages, messages.get("error.no_permission"));
            return;
        }

        String guildId = event.getGuild().getId();

        try {
            switch (subcommand) {
                case "language" -> handleLanguage(event, guildId, messages);
                case "mode" -> handleMode(event, guildId, messages);
                case "show" -> handleShow(event, guildId, messages);
            }
        } catch (Exception e) {
            replyError(event, messages, messages.get("error.unexpected", "message", e.getMessage()));
        }
    }

    private void handleLanguage(SlashCommandInteractionEvent event, String guildId, Messages messages) {
        String locale = event.getOption("locale", OptionMapping::getAsString);
        guildConfigManager.setLocale(guildId, locale);

        EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_SUCCESS).setTitle(messages.get("config.updated_title")).setDescription(messages.get("config.language_set", "locale", locale));
        applyFooter(embed, null);
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleMode(SlashCommandInteractionEvent event, String guildId, Messages messages) {
        String mode = event.getOption("mode", OptionMapping::getAsString);
        boolean enabled = event.getOption("enabled", OptionMapping::getAsBoolean);
        guildConfigManager.setModeEnabled(guildId, mode, enabled);

        String modeDisplay = GameMode.fromKey(mode).getDisplay();
        String description = enabled ? messages.get("config.mode_enabled", "mode", modeDisplay) : messages.get("config.mode_disabled", "mode", modeDisplay);

        EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_SUCCESS).setTitle(messages.get("config.updated_title")).setDescription(description);
        applyFooter(embed, null);
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleShow(SlashCommandInteractionEvent event, String guildId, Messages messages) {
        GuildConfig config = guildConfigManager.getConfig(guildId);

        StringBuilder modes = new StringBuilder();
        for (GameMode gm : GameMode.values()) {
            boolean active = config.isModeActive(gm.getKey());
            modes.append(active ? "✅" : "❌").append(" ").append(gm.getDisplay()).append("\n");
        }

        EmbedBuilder embed = new EmbedBuilder().setColor(COLOR_INFO).setTitle(messages.get("config.show_title")).addField(messages.get("config.language_field"), config.locale(), true).addField(messages.get("config.modes_field"), modes.toString(), false);
        applyFooter(embed, null);
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
}
