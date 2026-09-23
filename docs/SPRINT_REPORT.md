# Livraison Sprints 0 et 1 — 23 septembre 2026

## Résultat

Application Android réelle, Kotlin/Compose/MVVM/Hilt, lecteur de vidéos locales avec MediaExtractor/MediaCodec et PTS natifs. Sélection explicite, aperçu, START/STOP/relecture, fin de vidéo, erreurs, diagnostic de débit et arrêt en arrière-plan. Domaine Kotlin séparé; aucune valeur de vitesse ni confiance simulée.

Documents livrés : README, architecture, faisabilité SDK Meta datée et sourcée, algorithme détaillé, backlog avec critères/tests/DoD, plan des sprints, calibration, stratégie de tests et protocole terrain. CI GitHub Actions créée. SDK Meta non embarqué; caméra et voix relèvent des sprints suivants.

## Vérification exécutée

| Contrôle | Résultat |
|---|---|
| Compilation APK debug | Réussie, API minimale 28 / cible 35 |
| Compilation APK de tests | Réussie |
| Formatage Kotlin et Gradle | Spotless/ktlint |
| Tests domaine JVM | 4 réussis, 0 échec |
| Tests pixels JVM | 2 réussis, 0 échec |
| Tests Android sur émulateur API 35 ARM64 | 4 réussis, 0 échec, 0 ignoré |
| Lint Android | 0 erreur; avertissements de versions de dépendances plus récentes |
| Démarrage écran de l'application | Vérifié sur émulateur |
| CI distante GitHub | Non exécutée : aucun remote configuré |
| Lunettes / téléphone physique / vitesse terrain | Non testés, matériel et séquences de référence absents |

Tests instrumentés : MP4 réellement décodé avec PTS croissants, fin/relecture, fichier absent, STOP idempotent, arrêt en lecture, double START, redémarrage; écran initial sans mesure et START désactivé. Le test de mire ne valide aucune estimation géométrique ou de vitesse.

Corrections pendant la validation : valeur attendue de conversion rouge corrigée, protection des transitions de session contre un décodeur précédent, ordre explicite entre génération kapt et analyse lint pour éviter une course sur les stubs. Règles de sauvegarde désactivées et icône fournies. Contraste des barres système ajusté après inspection de l'écran.

## Reproduire

`./scripts/check.sh` exécute formatage, tests JVM, lint, assemblage application et tests. Pour les tests appareil, reprendre les variables d'environnement du README puis `./gradlew :app:connectedDebugAndroidTest` avec un émulateur/appareil démarré.

Rapports générés (non versionnés) : `domain/build/reports/tests/test/`, `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-debug.html`, `app/build/reports/androidTests/connected/debug/`. APK : `app/build/outputs/apk/debug/app-debug.apk`.

## Limites assumées et suite

- Aperçu 10 fps maximum, côté maximal 640 px; ce débit n'est ni celui du flux original ni un benchmark d'inférence.
- MP4/H.264 SDR testé; HDR et dimensions > 1920 × 1920 refusés. Colorimétrie BT.601 limitée. Autres décodeurs/téléphones et cycle de vie complet à couvrir sur matériel.
- Aucune caméra, aucune connexion Meta, aucune détection, calibration, mesure ou voix activée. Ces fonctions ne sont pas remplacées par des mocks.
- Choix initial YOLO11n véhicules documenté; checkpoint plaques spécialisé et licence encore à auditer au Sprint 2.
- Pas de dataset réel inventé : manifeste et protocole prêts, collecte et référence indépendante nécessaires.
- Les dépendances sont épinglées et compilées; les mises à jour proposées par lint sont un travail de maintenance distinct.

La revue des Sprints 0–1 est prête. Le projet complet reste en développement et aucune précision métrologique n'est acquise. Le Sprint 2 n'a pas été commencé.
