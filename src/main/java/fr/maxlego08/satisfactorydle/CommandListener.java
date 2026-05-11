package fr.maxlego08.satisfactorydle;

import fr.maxlego08.satisfactorydle.command.ConfigCommand;
import fr.maxlego08.satisfactorydle.command.EntityCache;
import fr.maxlego08.satisfactorydle.command.GuessCommand;
import fr.maxlego08.satisfactorydle.command.ScoreCommand;
import fr.maxlego08.satisfactorydle.command.StartCommand;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Collections;
import java.util.List;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.replyError;

public class CommandListener extends ListenerAdapter {

    private final GuildConfigManager guildConfigManager;
    private final String defaultLocale;
    private final EntityCache entityCache;

    private final StartCommand startCommand;
    private final GuessCommand guessCommand;
    private final ScoreCommand scoreCommand;
    private final ConfigCommand configCommand;

    public CommandListener(SatisfactorydleAPI api, GuildConfigManager guildConfigManager, String defaultLocale) {
        this.guildConfigManager = guildConfigManager;
        this.defaultLocale = defaultLocale;
        this.entityCache = new EntityCache(api);

        this.startCommand = new StartCommand(api, entityCache);
        this.guessCommand = new GuessCommand(api, entityCache);
        this.scoreCommand = new ScoreCommand(api);
        this.configCommand = new ConfigCommand(guildConfigManager);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("sfdle")) return;

        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        event.deferReply().setEphemeral(true).queue();

        if ("config".equals(group)) {
            configCommand.execute(event, subcommand);
            return;
        }

        String locale = getLocale(event);
        String mode = event.getOption("mode", "item", OptionMapping::getAsString);

        GuildConfig guildConfig = getGuildConfig(event);
        if (guildConfig != null && !guildConfig.isModeActive(mode)) {
            replyError(event, "The **" + GameMode.fromKey(mode).getDisplay()
                    + "** mode is disabled on this server.\nAn administrator can enable it with `/sfdle config mode`.");
            return;
        }

        try {
            switch (subcommand) {
                case "start" -> startCommand.execute(event, mode, locale);
                case "guess" -> guessCommand.execute(event, mode, locale);
                case "score" -> scoreCommand.execute(event, mode, locale);
            }
        } catch (ApiException e) {
            replyError(event, e.getMessage());
        } catch (Exception e) {
            replyError(event, "An unexpected error occurred: " + e.getMessage());
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("sfdle")) return;
        if (!"entity".equals(event.getFocusedOption().getName())) return;

        OptionMapping modeMapping = event.getOption("mode");
        String mode = modeMapping != null ? modeMapping.getAsString() : "item";

        String locale = defaultLocale;
        if (event.getGuild() != null) {
            locale = guildConfigManager.getConfig(event.getGuild().getId()).getLocale();
        }

        String key = entityCache.cacheKey(mode, locale);
        entityCache.ensureCacheAsync(mode, locale);

        List<EntityCache.EntityEntry> entities = entityCache.get(key);
        if (entities == null) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        String typed = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = entities.stream()
                .filter(e -> e.name().toLowerCase().contains(typed))
                .sorted((a, b) -> {
                    boolean aStarts = a.name().toLowerCase().startsWith(typed);
                    boolean bStarts = b.name().toLowerCase().startsWith(typed);
                    if (aStarts != bStarts) return aStarts ? -1 : 1;
                    return a.name().compareToIgnoreCase(b.name());
                })
                .limit(25)
                .map(e -> new Command.Choice(e.name(), e.name()))
                .toList();

        event.replyChoices(choices).queue();
    }

    private String getLocale(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return defaultLocale;
        return guildConfigManager.getConfig(event.getGuild().getId()).getLocale();
    }

    private GuildConfig getGuildConfig(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return null;
        return guildConfigManager.getConfig(event.getGuild().getId());
    }
}
