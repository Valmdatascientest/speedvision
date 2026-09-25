# Vérifier la première tranche vocale

## Automatique

`./scripts/check.sh` inclut les tests JVM de politique et du contrat `AudioOutput`. Le test double de ce contrat vérifie seulement le dispatch : il ne simule pas une validation TTS ou Bluetooth. La suite Android utilise le vrai service TTS de l'appareil.

Sur un téléphone avec une voix française locale déjà installée, exiger le test positif :

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=fr.speedvision.AndroidAudioOutputTest \
  -Pandroid.testInstrumentationRunnerArguments.requireOfflineVoice=true
```

Sans ce flag, l'absence d'une voix admissible est signalée comme test ignoré. Un moteur installé n'implique pas que ses données françaises hors ligne le soient. Les tests de callbacks ne prouvent ni l'audition ni la route physique.

## Parcours manuel sur téléphone

Ouvrir **Voix / test audio**. L'écran doit être silencieux, interrupteur éteint. Activer puis demander explicitement la phrase de test. Elle ne comporte aucune mesure. Répéter pour le haut-parleur, un casque filaire et une sortie Bluetooth effectivement appairée. La sélection se fait dans Android; l'inventaire de l'app n'indique pas la sortie active.

| Action | Résultat attendu / preuve à relever |
|---|---|
| Ouvrir puis fermer sans activer | Aucune parole; instance TTS fermée |
| Activer et tester | Phrase française correcte; noter moteur, version, voix et sortie entendue |
| Couper pendant la phrase | Arrêt, interrupteur désactivé; aucun redémarrage |
| Retour accueil puis retour app | Voix désactivée; aucune reprise |
| Déconnecter le casque pendant la phrase | Callback de retrait, arrêt/désactivation; mesurer la transition réellement entendue |
| Reconnecter / ajouter une sortie | Aucune reprise; vérifier le routage avant réactivation |
| Changer la sélection entre sorties déjà connectées | Observer Android : ce changement peut ne pas déclencher le callback d'inventaire |
| Appel ou autre app demandant le focus | Arrêt/désactivation, pas de reprise au retour du focus |
| Moteur désactivé / voix française absente | Motif explicite, bouton de test désactivé |
| Volume média nul / silencieux | Distinguer callback terminé et audition effective; l'app ne change pas le volume |

Noter appareil, version Android, moteur/voix, accessoire et profil Bluetooth, état réseau, sortie réellement entendue, état de l'app et délai d'arrêt. Tester hors ligne après installation de la voix. Ne pas identifier une sortie Bluetooth comme lunettes Meta sans essai du matériel concerné.

## Prochaine intégration live

Il manque des mesures vidéo métriques automatiques valides. Une future intégration devra : conserver l'identité source/piste/calibration; transmettre les rejets; fournir un instant d'acquisition dans la même horloge monotone que `nowElapsedMs`; réévaluer périodiquement la fraîcheur même sans nouvelle frame; arrêter sur source/track/route/lifecycle invalides; appliquer immédiatement les décisions sans file différée. Le seuil et les durées de `VoiceConfig` devront alors être reliés aux réglages utilisateur.

Ni le CSV ni le bouton d'exemple synthétique ne doivent alimenter cette voie live. La phrase de test actuelle reste une action explicite indépendante et ne sert pas de démonstration de vitesse.
