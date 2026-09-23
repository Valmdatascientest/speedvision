# SpeedVision

Prototype Android expérimental d'estimation de **vitesse relative axiale**, à partir de la géométrie d'une plaque connue. Cette livraison couvre uniquement les Sprints 0 et 1 : étude et lecteur vidéo local. Elle ne mesure encore aucune vitesse et ne se connecte pas aux lunettes.

## Utiliser

Installer l'APK debug compilé ou ouvrir le dépôt avec Android Studio. Android 9/API 28 minimum. Choisir une vidéo locale MP4/H.264 SDR, au plus 1920 × 1920, puis START. STOP arrête; START rejoue depuis le début. Le diagnostic affiche PTS, compteur et débit réellement livré à l'aperçu, plafonné à 10 fps/640 px. Pas de son vidéo, pas d'enregistrement, aucun modèle téléchargé. Les fonctions prévues sont indiquées comme indisponibles.

## Compiler et tester

Base : JDK 17, SDK 35/build-tools 35.0.0, Gradle wrapper 8.11.1. Définir sdk.dir dans local.properties (non versionné) ou ANDROID_HOME, puis :

```sh
./scripts/check.sh
```

Sur ce poste, le JDK et le SDK ont été installés dans `.tools/` pour isoler l'environnement. Le script configure le JDK et les caches locaux. Pour lancer directement Gradle ici :

```sh
export JAVA_HOME="$PWD/.tools/jdk/Contents/Home"
export ANDROID_USER_HOME="$PWD/.tools/android-user"
export GRADLE_USER_HOME="$PWD/.tools/gradle-home"
./gradlew :app:connectedDebugAndroidTest
```

APK : `app/build/outputs/apk/debug/app-debug.apk`. Rapports : `app/build/reports/` et `domain/build/reports/`. Première compilation : accès réseau pour outils et dépendances. L'application elle-même ne demande aucune permission réseau.

## Conception et suivi

- [Faisabilité et vérification Meta](docs/FEASIBILITY.md)
- [Architecture](ARCHITECTURE.md) et [algorithme mathématique](docs/ALGORITHM.md)
- [Backlog avec critères/tests/DoD](BACKLOG.md) et [sprints](SPRINTS.md)
- [Calibration](CALIBRATION.md), [tests](TESTING.md), [protocole terrain](testing/README.md)
- [Résultats de l'incrément](docs/SPRINT_REPORT.md)

La profondeur Z = fx W/w exige des hypothèses fortes; sa dérivée n'est pas une vitesse absolue routière. Un résultat ambigu devra être rejeté. Aucun chiffre de précision, de confiance ou de FPS d'inférence n'est simulé ici.

## Git et CI

Branches attendues : main stable, develop intégration, feature/*, fix/*, test/*. Commits courts et explicites. CI GitHub Actions : build, lint, formatage, tests JVM et émulateur Android. Aucun remote n'est présumé; publication et CI distante restent à effectuer si aucun dépôt distant n'est configuré.

## Données

Sélection explicite via sélecteur système; lecture de l'URI en mémoire, sans copie ni permission globale. Après mort du processus, sélectionner à nouveau. Aucun OCR, export, télémétrie, cloud ou SDK Meta dans l'application actuelle. Le modèle de distribution et les licences de futurs poids ML doivent être validés avant leur intégration.
