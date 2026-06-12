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

- Python 3.12+
- A Discord bot token
- A Satisfactorydle API token (generate one at [satisfactorydle.net/settings/api](https://satisfactorydle.net/settings/api))

## Setup

1. Clone the repository
2. Create and activate a virtual environment
3. Install dependencies:
   ```bash
   pip install -e .[dev]
   ```
4. Copy `.env.example` to `.env` and fill your values:
   ```env
   DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN
   API_URL=https://satisfactorydle.net
   API_TOKEN=YOUR_API_TOKEN
   DEFAULT_LOCALE=fr
   SQLITE_PATH=./sfdle.db
   LOG_LEVEL=INFO
   ```
5. Start the bot:
   ```bash
   satisfactorydle-bot
   ```

## Docker

Build image:

```bash
docker build -t satisfactorydle-bot:latest .
```

Run container:

```bash
docker run -d --name satisfactorydle-bot \
  -e DISCORD_TOKEN=YOUR_DISCORD_BOT_TOKEN \
  -e API_URL=https://satisfactorydle.net \
  -e API_TOKEN=YOUR_API_TOKEN \
  -e DEFAULT_LOCALE=fr \
  -e SQLITE_PATH=/data/sfdle.db \
  -v $(pwd)/data:/data \
  satisfactorydle-bot:latest
```

## CI/CD Deployment

GitHub Actions workflow: `.github/workflows/deploy.yml`

- `ci`: lint (`ruff`) + tests (`pytest`)
- `build-and-push`: build and push Docker image to GHCR
- `deploy`: SSH deploy to VPS and container restart

Required repository secrets:

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `DISCORD_TOKEN`
- `API_URL`
- `API_TOKEN`
- `DEFAULT_LOCALE`

## Cutover and Rollback

- Cutover checklist: `ops/cutover-checklist.md`
- Rollback runbook: `ops/rollback.md`
- Python operations guide: `ops/python-operations.md`

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

- **Python 3.12**
- **[discord.py](https://github.com/Rapptz/discord.py)** - Discord API wrapper
- **[httpx](https://www.python-httpx.org/)** - async API communication
- **[aiosqlite](https://github.com/omnilib/aiosqlite)** - SQLite async access
- **[pytest](https://pytest.org/)** + **ruff** - quality pipeline

## Project Structure

```
app/
├── main.py                        # Entry point
├── bot/client.py                  # discord.py client & event wiring
├── api/satisfactorydle_api.py     # HTTP client for Satisfactorydle API
├── i18n/message_manager.py        # i18n message loader/accessor
├── locales/                       # en/fr locale files
├── commands/
│   ├── handlers.py                # /sfdle command handlers
│   ├── embed_helper.py            # Shared embed utilities
│   └── entity_cache.py            # Cached entity list for autocomplete
├── quiz/
│   ├── quiz_manager.py            # Quiz lifecycle (start, hint, timeout, auto-restart)
│   └── session.py                 # Active quiz session state
├── storage/
│   ├── sqlite.py                  # DB init
│   └── guild_config_manager.py    # Guild config CRUD with in-memory cache
└── domain/
    ├── game_mode.py
    └── guild_config.py
tests/
└── ...
```

## License

This project is open source. See the [Satisfactorydle website](https://satisfactorydle.net) for more information.
