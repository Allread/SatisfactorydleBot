# Exploitation Python (run local, variables, déploiement, dépannage)

## Variables d’environnement

- `DISCORD_TOKEN` (requis)
- `API_URL` (requis)
- `API_TOKEN` (requis)
- `DEFAULT_LOCALE` (optionnel, défaut: `fr`)
- `SQLITE_PATH` (optionnel, défaut: `./sfdle.db`)
- `LOG_LEVEL` (optionnel, défaut: `INFO`)

## Lancement local

1. Créer un venv Python 3.12+
2. Installer:
```bash
pip install -e .[dev]
```
3. Exporter les variables (ou `.env` via votre outillage local)
4. Lancer:
```bash
satisfactorydle-bot
```

## Déploiement

Pipeline: `.github/workflows/deploy.yml`

- `ci`: lint + tests
- `build-and-push`: image Docker GHCR
- `deploy`: redéploiement VPS via SSH

## Dépannage rapide

Container:
```bash
docker ps | grep satisfactorydle-bot
docker logs --tail=200 satisfactorydle-bot
```

Cas fréquents:
- Slash commands absentes: vérifier `on_ready`/sync et permissions bot.
- Erreurs API: vérifier `API_URL`/`API_TOKEN`.
- Locale inattendue: vérifier `DEFAULT_LOCALE` et config guild.
- Quiz ne redémarre pas: vérifier logs `QuizManager` et endpoint `quiz/start`.
