package fr.maxlego08.satisfactorydle;

import fr.maxlego08.sarah.DatabaseConfiguration;
import fr.maxlego08.sarah.MigrationManager;
import fr.maxlego08.sarah.RequestHelper;
import fr.maxlego08.sarah.SqliteConnection;
import fr.maxlego08.sarah.logger.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        Config config = Config.load(Path.of("config-stdle.json"));

        // Database setup
        Logger logger = System.out::println;
        DatabaseConfiguration dbConfig = DatabaseConfiguration.sqlite(false);
        SqliteConnection connection = new SqliteConnection(dbConfig, new File("."), logger);
        connection.connect();

        MigrationManager.setDatabaseConfiguration(dbConfig);
        MigrationManager.registerMigration(new CreateGuildConfigMigration());
        MigrationManager.execute(connection, logger);

        RequestHelper requestHelper = new RequestHelper(connection, logger);
        GuildConfigManager guildConfigManager = new GuildConfigManager(requestHelper, config.locale());

        // Bot setup
        SatisfactorydleAPI api = new SatisfactorydleAPI(config);
        MessageManager messageManager = new MessageManager(config.locale());
        CommandListener listener = new CommandListener(api, guildConfigManager, messageManager, config.locale());

        JDA jda = JDABuilder.createLight(config.discordToken())
                .addEventListeners(listener)
                .build()
                .awaitReady();

        jda.updateCommands().addCommands(
                Commands.slash("sfdle", "Play Satisfactorydle")
                        .addSubcommands(
                                new SubcommandData("start", "Start the daily challenge and see yesterday's answer")
                                        .addOptions(createModeOption()),
                                new SubcommandData("guess", "Make a guess")
                                        .addOptions(createModeOption())
                                        .addOption(OptionType.STRING, "entity", "The entity to guess", true, true),
                                new SubcommandData("score", "View your current score and guesses")
                                        .addOptions(createModeOption())
                        )
                        .addSubcommandGroups(
                                new SubcommandGroupData("config", "Configure the bot for this server")
                                        .addSubcommands(
                                                new SubcommandData("language", "Set the language")
                                                        .addOptions(new OptionData(OptionType.STRING, "locale", "Language", true)
                                                                .addChoice("Français", "fr")
                                                                .addChoice("English", "en")),
                                                new SubcommandData("mode", "Enable or disable a game mode")
                                                        .addOptions(createModeOption())
                                                        .addOption(OptionType.BOOLEAN, "enabled", "Enable or disable", true),
                                                new SubcommandData("show", "Show current configuration")
                                        )
                        )
        ).queue();

        System.out.println("Bot is ready! Logged in as " + jda.getSelfUser().getName());
        System.out.println("Type 'stop' to shut down the bot.");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equalsIgnoreCase("stop")) {
                    System.out.println("Shutting down...");
                    jda.shutdown();
                    connection.disconnect();
                    System.out.println("Bot stopped.");
                    break;
                }
            }
        }
    }

    private static OptionData createModeOption() {
        OptionData option = new OptionData(OptionType.STRING, "mode", "Game mode", true);
        for (GameMode mode : GameMode.values()) {
            option.addChoice(mode.getDisplay(), mode.getKey());
        }
        return option;
    }
}
