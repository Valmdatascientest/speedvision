# Sprint 7 — première tranche : politique vocale et test TTS

Le Sprint 6 est validé ([rapport](SPRINT_6_REPORT.md)). Le Sprint 7 est **en cours** : sa première tranche ajoute la politique d'annonces et un véritable adaptateur Android TextToSpeech, sans inventer de mesure en direct.

## Implémenté

- `VoicePolicy` Kotlin pur, paramétrable par `VoiceConfig`, sans horloge système cachée : score minimum 0,8; plage de stabilité de 3 km/h pendant 1 s et au moins trois échantillons distincts; délai global de 5 s; variation minimum de 5 km/h pour une même identité; âge/gap maximum de 300 ms.
- Source, piste et calibration font partie de l'identité. Changement d'identité, rejet, score insuffisant, désactivation, sortie indisponible ou horodatages invalides demandent l'arrêt de l'annonce et une nouvelle accumulation. Le délai global survit à ces resets pour empêcher le spam par changement de piste.
- Données historiques refusées par défaut (`live=false`). Les doublons ne prolongent pas la stabilité; une modification d'un résultat au même PTS est refusée. Le consommateur doit réévaluer la fraîcheur même quand les images cessent d'arriver.
- `AudioOutput.apply` distingue l'arrêt immédiat d'une absence de nouvelle annonce pendant le délai minimum. Une soumission réserve le délai même si le moteur vocal échoue; aucune file de réessai différé.
- Texte explicitement relatif et signé : rapprochement ou éloignement, kilomètres par heure arrondis à l'unité. Le score reste une heuristique, pas une précision probabiliste.
- `AndroidAudioOutput` : initialisation asynchrone, choix d'une voix française déclarée hors ligne et installée, `QUEUE_FLUSH`, retours de synthèse, délais bornés, focus audio temporaire et fermeture idempotente.
- Écran **Voix / test audio**, désactivé à l'ouverture. Le bouton explicite lit uniquement « Test audio SpeedVision. Aucune mesure de vitesse annoncée. ». Fermer, couper la voix ou passer en arrière-plan arrête la sortie; aucune reprise automatique.

Version Android 0.7.0. Aucun modèle, enregistrement audio ni permission réseau/microphone ajouté. Le manifeste déclare uniquement la requête de visibilité du service TTS. Le moteur est un service Android externe : le choix hors ligne repose sur ses caractéristiques déclarées.

## Routage

La sortie utilise les attributs média/parole et le routage Android. L'écran liste les **sorties disponibles**, sans prétendre connaître la sortie TTS active. Un changement des périphériques signalés coupe/désactive la voix et impose une réactivation manuelle. Un changement de sélection parmi des périphériques déjà connectés n'est pas nécessairement observable par ce callback.

Le bouton de réglages ouvre les réglages audio Android, après coupure de la voix. Aucun forçage SCO, appel téléphonique, nom/adresse Bluetooth ou API Meta inventée. Une perte de focus coupe la voix, y compris un ducking demandé; aucun redémarrage lors du retour de focus. Le focus est demandé seulement depuis le test au premier plan.

## Validation

En cours : tests de politique, compilation/lint et tests instrumentés réels. Le résultat exécuté sera ajouté avant publication finale de cette tranche.

Les tests JVM couvrent stabilité, signe, rafales, délai et delta cumulés, changements piste/calibration, rejets, absence de source live, panne de sortie, ordre temporel, gaps, qualité/valeurs invalides et duplication modifiée. Ils contrôlent aussi le dispatch arrêt/parole et l'absence de file de réessai.

Les tests Android initialisent réellement TTS et vérifient désactivation initiale/fermeture. Le test positif de parole est explicitement ignoré si aucune voix française hors ligne admissible n'est installée; `-Pandroid.testInstrumentationRunnerArguments.requireOfflineVoice=true` transforme cette absence en échec. Une soumission et un callback ne démontrent pas que l'utilisateur entend la bonne sortie physique.

## Reste à faire dans le Sprint 7

La politique est disponible et testable, mais elle n'est **pas raccordée à des mesures vidéo live**, absentes du prototype. Les CSV et l'exemple synthétique ne déclenchent aucune voix. Le réglage des seuils est exposé dans l'API du domaine; son intégration utilisateur attend une vraie source de mesures. La première tranche ne présente pas de réglages graphiques sans effet.

Valider sur téléphone équipé d'une voix locale : audition, volume/silencieux, téléphone/casque/Bluetooth, déconnexion pendant parole, changement de route, appels/perte de focus, retour d'arrière-plan, moteur manquant et langue non installée. La sortie sur lunettes nécessite un essai matériel; aucun transport Meta dédié n'est intégré. La fin du Sprint 7 ne peut pas être déclarée sur la seule base des tests d'émulateur.

## Sources primaires

- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech) : initialisation, manifeste, soumission et fermeture.
- [Android — audio focus](https://developer.android.com/media/optimize/audio-focus) : perte de focus et restriction au premier plan pour une application ciblant API 35.
- [Android AudioDeviceCallback](https://developer.android.com/reference/android/media/AudioDeviceCallback) : ajout/retrait de périphériques, distinct de la sélection de route effective.

Consultées le 25 septembre 2026 (heure locale).
