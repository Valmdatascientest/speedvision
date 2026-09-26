# Essais Meta sur matériel — non encore exécutés

Cible déclarée : Samsung Z Flip7, Wayfarer 0015, version déclarée 68449720064700100. Commencer par confirmer génération, firmware, Android/One UI, Meta AI, version DAT 1.0.0, mode développeur ou inscription WDC, et type/compatibilité retournés par le SDK. Ne pas conserver de token dans le compte rendu.

| Essai | Preuve attendue |
|---|---|
| Bluetooth refusé, puis autorisé | Pas de session avant autorisation; reprise explicite possible |
| Inscription refusée/annulée | Pas de faux état connecté; motif SDK visible |
| Permission caméra refusée puis accordée | Aucun flux avant accord; image seulement après START |
| Plusieurs appareils | Sélection physique correcte, aucun basculement automatique |
| Mire couleur et orientation | I420 compact, dimensions, couleurs et orientation vérifiées indépendamment |
| PTS pendant 5 min | Monotonie, unités µs, cadence réelle et écarts; aucun temps de réception substitué |
| STOP/START rapide ×20 | Pas d'ancienne image/résultat après arrêt, pas de sessions concurrentes |
| Arrière-plan/verrouillage | Arrêt caméra; aucune reprise sans action |
| Déconnexion, lunettes retirées, pause matérielle | Rejet du suivi/diagnostic, arrêt et reprise manuelle |
| Firmware/compagnon incompatibles | Erreur exploitable, aucune boucle de reconnexion |
| Session 15–30 min | Batterie/thermique SDK, température/énergie téléphone mesurées séparément, débit p50/p95 et rejets |
| TTS Bluetooth simultané | Route réellement entendue, interruptions et comportement après retrait; pas de vitesse fabriquée |

Pour chaque cas, noter appareil/mode, action, comportement attendu/observé et horodatages. Utiliser une mire/scène autorisée; aucune collecte ou sauvegarde de vidéo n'est automatique dans SpeedVision. Un flux fonctionnel ne valide ni la profondeur, ni les coins, ni une vitesse routière.

## Régression logicielle

```sh
./gradlew :app:connectedDebugAndroidTest -PmetaEnabled=true
# Avec les modèles locaux autorisés :
./gradlew :app:connectedDebugAndroidTest -PmetaEnabled=true \
  -Pandroid.testInstrumentationRunnerArguments.requireModels=true
```

Les tests Meta additionnels vérifient le panneau avant activation, l'initialisation réelle du SDK sans compagnon et le rejet d'une source non inscrite. Ils n'utilisent pas MockDeviceKit et ne produisent pas de fausse preuve de réception. Exécuter ces tests d'absence sur un émulateur **sans lunettes**, pas sur un téléphone déjà inscrit/appairé.
