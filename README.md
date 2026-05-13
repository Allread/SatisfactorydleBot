# Satisfactorydle Bot

Discord bot for [Satisfactorydle](https://satisfactorydle.net) - a Wordle-style guessing game for Satisfactory items, buildings, recipes, creatures, and milestones.

## Features

- 6 game modes: Classic, Item, Building, Recipe, Creature, Milestone
- Timed quiz mode (60 seconds to guess an item from hints)
- Smart autocomplete with dynamic filtering
- Progressive hint system that unlocks as you guess
- Detailed comparison grid after each guess
- Ephemeral responses - only you can see your guesses
- Per-server language and game mode configuration
- Multilingual support (English & French)

## Requirements

- Java 21+
- A Discord bot token
- A Satisfactorydle API token (generate one at [satisfactorydle.net/settings/api](https://satisfactorydle.net/settings/api))

## Setup

1. Clone the repository
2. Build the project:
   ```bash
   ./gradlew build
   ```
3. Run once to generate the default `config.json`:
   ```bash
   java -jar target/Satisfactorydle.jar
   ```
4. Edit `config.json` with your credentials:
   ```json
   {
     "discord_token": "YOUR_DISCORD_BOT_TOKEN",
     "api_url": "https://satisfactorydle.net",
     "api_token": "YOUR_API_TOKEN",
     "locale": "fr"
   }
   ```
5. Start the bot:
   ```bash
   java -jar target/Satisfactorydle.jar
   ```

## Commands

| Command | Description |
|---------|-------------|
| `/sfdle start <mode>` | Start a daily challenge and see yesterday's answer |
| `/sfdle guess <mode> <entity>` | Submit a guess with autocomplete suggestions |
| `/sfdle score <mode>` | View your current progress and game statistics |
| `/sfdle quiz` | Start a timed quiz - guess the item in 60 seconds |
| `/sfdle config language <locale>` | Change the bot language (requires Manage Server) |
| `/sfdle config mode <mode> <enabled>` | Enable/disable a game mode (requires Manage Server) |
| `/sfdle config show` | Display the current server configuration |

## Tech Stack

- **Java 21** - Gradle (Kotlin DSL)
- **[JDA 5.2.2](https://github.com/discord-jda/JDA)** - Discord API wrapper
- **[Gson](https://github.com/google/gson)** - JSON parsing
- **[Sarah](https://repo.groupez.dev)** - SQLite database ORM
- **java.net.http.HttpClient** - API communication

## Project Structure

```
src/main/java/fr/maxlego08/satisfactorydle/
├── Main.java                  # Entry point, JDA initialization
├── CommandListener.java       # Slash command & autocomplete handler
├── SatisfactorydleAPI.java    # HTTP client for the Satisfactorydle API
├── MessageManager.java        # i18n message loading
├── Messages.java              # Message accessor
├── ApiException.java          # API error wrapper
├── command/
│   ├── StartCommand.java      # /sfdle start
│   ├── GuessCommand.java      # /sfdle guess
│   ├── ScoreCommand.java      # /sfdle score
│   ├── QuizCommand.java       # /sfdle quiz
│   ├── ConfigCommand.java     # /sfdle config
│   ├── EmbedHelper.java       # Shared embed building utilities
│   └── EntityCache.java       # Cached entity list for autocomplete
├── quiz/
│   ├── QuizManager.java       # Quiz lifecycle (start, hint, timeout)
│   └── QuizSession.java       # Active quiz session state
├── config/
│   ├── Config.java            # config.json loader
│   ├── GameMode.java          # Game mode enum
│   ├── GuildConfig.java       # Per-guild configuration DTO
│   └── GuildConfigManager.java# Guild config CRUD with in-memory cache
└── database/
    └── CreateGuildConfigMigration.java  # SQLite schema migration
```

## License

This project is open source. See the [Satisfactorydle website](https://satisfactorydle.net) for more information.
