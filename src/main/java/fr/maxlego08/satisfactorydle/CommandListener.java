package fr.maxlego08.satisfactorydle;

import fr.maxlego08.satisfactorydle.command.*;
import fr.maxlego08.satisfactorydle.config.GameMode;
import fr.maxlego08.satisfactorydle.config.GuildConfig;
import fr.maxlego08.satisfactorydle.config.GuildConfigManager;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Collections;
import java.util.List;

import static fr.maxlego08.satisfactorydle.command.EmbedHelper.replyError;

public class CommandListener extends ListenerAdapter {

    private final GuildConfigManager guildConfigManager;
    private final MessageManager messageManager;
    private final String defaultLocale;
    private final EntityCache entityCache;

    private final StartCommand startCommand;
    private final GuessCommand guessCommand;
    private final ScoreCommand scoreCommand;
    private final ConfigCommand configCommand;
    private final QuizCommand quizCommand;
    private final QuizManager quizManager;

    public CommandListener(SatisfactorydleAPI api, GuildConfigManager guildConfigManager, MessageManager messageManager, String defaultLocale) {
        this.guildConfigManager = guildConfigManager;
        this.messageManager = messageManager;
        this.defaultLocale = defaultLocale;
        this.entityCache = new EntityCache(api);

        this.startCommand = new StartCommand(api, entityCache);
        this.guessCommand = new GuessCommand(api, entityCache);
        this.scoreCommand = new ScoreCommand(api);
        this.configCommand = new ConfigCommand(guildConfigManager);
        this.quizManager = new QuizManager(api);
        this.quizCommand = new QuizCommand(api, quizManager);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("sfdle")) return;

        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        String locale = getLocale(event);
        Messages messages = messageManager.forLocale(locale);

        // Quiz handles its own defer (public reply)
        if ("quiz".equals(subcommand)) {
            try {
                quizCommand.execute(event, locale, messages);
            } catch (Exception e) {
                if (!event.isAcknowledged()) {
                    event.deferReply().setEphemeral(true).queue();
                }
                replyError(event, messages, messages.get("error.unexpected", "message", e.getMessage()));
            }
            return;
        }

        // All other commands are ephemeral
        event.deferReply().setEphemeral(true).queue();

        if ("config".equals(group)) {
            configCommand.execute(event, subcommand, messages);
            return;
        }

        String mode = event.getOption("mode", "item", OptionMapping::getAsString);

        GuildConfig guildConfig = getGuildConfig(event);
        if (guildConfig != null && !guildConfig.isModeActive(mode)) {
            replyError(event, messages, messages.get("error.mode_disabled", "mode", GameMode.fromKey(mode).getDisplay()));
            return;
        }

        try {
            switch (subcommand) {
                case "start" -> startCommand.execute(event, mode, locale, messages);
                case "guess" -> guessCommand.execute(event, mode, locale, messages);
                case "score" -> scoreCommand.execute(event, mode, locale, messages);
            }
        } catch (ApiException e) {
            replyError(event, messages, e.getMessage());
        } catch (Exception e) {
            replyError(event, messages, messages.get("error.unexpected", "message", e.getMessage()));
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String channelId = event.getChannel().getId();
        if (!quizManager.hasActiveQuiz(channelId)) return;

        String locale = guildConfigManager.getConfig(event.getGuild().getId()).locale();
        Messages messages = messageManager.forLocale(locale);

        quizManager.handleMessage(channelId, event.getMessage().getContentRaw().trim(), event.getAuthor(), event.getChannel(), messages);
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("sfdle")) return;
        if (!"entity".equals(event.getFocusedOption().getName())) return;

        OptionMapping modeMapping = event.getOption("mode");
        String mode = modeMapping != null ? modeMapping.getAsString() : "item";

        String locale = defaultLocale;
        if (event.getGuild() != null) {
            locale = guildConfigManager.getConfig(event.getGuild().getId()).locale();
        }

        String key = entityCache.cacheKey(mode, locale);
        entityCache.ensureCacheAsync(mode, locale);

        List<EntityCache.EntityEntry> entities = entityCache.get(key);
        if (entities == null) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        String typed = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = entities.stream().filter(e -> e.name().toLowerCase().contains(typed)).sorted((a, b) -> {
            boolean aStarts = a.name().toLowerCase().startsWith(typed);
            boolean bStarts = b.name().toLowerCase().startsWith(typed);
            if (aStarts != bStarts) return aStarts ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        }).limit(25).map(e -> new Command.Choice(e.name(), e.name())).toList();

        event.replyChoices(choices).queue();
    }

    public void shutdown() {
        quizManager.shutdown();
    }

    private String getLocale(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return defaultLocale;
        return guildConfigManager.getConfig(event.getGuild().getId()).locale();
    }

    private GuildConfig getGuildConfig(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return null;
        return guildConfigManager.getConfig(event.getGuild().getId());
    }
}
