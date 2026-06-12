# Rollback Runbook

## Trigger

Rollback immédiat si l’un des cas suivants survient après déploiement:
- erreurs API massives (4xx/5xx anormales),
- commandes slash non fonctionnelles,
- crash loop container,
- comportement quiz incorrect bloquant.

## Procédure rapide

1. Stopper Python:
```bash
docker stop satisfactorydle-bot || true
docker rm satisfactorydle-bot || true
```

2. Restaurer la DB (si corruption / incohérence):
```bash
cp /opt/satisfactorydle-bot/backups/<backup>.db /opt/satisfactorydle-bot/data/sfdle.db
```

3. Redémarrer la version stable précédente (Java legacy ou image Python précédente).

4. Vérifier:
```bash
docker ps
docker logs --tail=200 satisfactorydle-bot
```

5. Smoke test minimal Discord:
- `/sfdle start item`
- `/sfdle score item`
- `/sfdle quiz`

## Post-mortem

- Capturer les logs de l’incident.
- Marquer le commit fautif.
- Ouvrir une issue de correction avec cause racine.
