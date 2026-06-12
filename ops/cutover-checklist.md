# Cutover Checklist (Java -> Python)

## 1) Préparation

- [ ] Vérifier que les secrets GitHub Actions sont définis (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DISCORD_TOKEN`, `API_URL`, `API_TOKEN`, `DEFAULT_LOCALE`).
- [ ] Vérifier l’accès SSH du runner vers le serveur cible.
- [ ] Vérifier que Docker est installé sur le serveur cible.
- [ ] Vérifier le dossier de persistance SQLite: `/opt/satisfactorydle-bot/data`.

## 2) Backup SQLite

Exécuter sur le serveur avant la bascule:

```bash
mkdir -p /opt/satisfactorydle-bot/backups
cp /opt/satisfactorydle-bot/data/sfdle.db /opt/satisfactorydle-bot/backups/sfdle-$(date +%Y%m%d-%H%M%S).db
```

Optionnel (hash de contrôle):

```bash
sha256sum /opt/satisfactorydle-bot/backups/sfdle-*.db | tail -n 1
```

## 3) Déploiement

- [ ] Push sur `refacto_python` (déclenche le workflow).
- [ ] Vérifier job `ci` (ruff + pytest) en succès.
- [ ] Vérifier job `build-and-push` (image GHCR publiée).
- [ ] Vérifier job `deploy` (container redémarré).

## 4) Smoke tests post-déploiement

Discord:

- [ ] `/sfdle start item` retourne un embed valide.
- [ ] `/sfdle guess item <entity>` fonctionne + historique visible.
- [ ] `/sfdle score item` affiche le statut courant.
- [ ] `/sfdle config show` répond (admin).
- [ ] `/sfdle quiz` démarre un quiz (réponse publique).
- [ ] Bonne réponse texte -> nouveau quiz auto.
- [ ] Pas de réponse 60s -> arrêt du quiz.

Infra:

```bash
docker ps | grep satisfactorydle-bot
docker logs --tail=200 satisfactorydle-bot
```

- [ ] Pas d’erreurs critiques dans les logs.

## 5) Validation finale de bascule

- [ ] Désactiver définitivement l’ancien service Java.
- [ ] Confirmer que seul le bot Python consomme le token Discord.
- [ ] Documenter l’heure de bascule et le SHA déployé.

## 6) Rollback (si incident)

```bash
docker stop satisfactorydle-bot || true
docker rm satisfactorydle-bot || true
# relancer l’ancienne stack Java (commande interne)
```

Restauration DB si nécessaire:

```bash
cp /opt/satisfactorydle-bot/backups/<backup>.db /opt/satisfactorydle-bot/data/sfdle.db
```

Puis relancer le service cible (Java ou Python) selon stratégie décidée.
