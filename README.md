# SpeedVision

Prototype Android expérimental d'estimation future de **vitesse relative axiale** à partir de la géométrie d'une plaque connue. Le Sprint 3 ajoute un suivi temporel des véhicules à la détection locale véhicule → plaque et à CameraX. **Aucune vitesse ou distance n'est encore mesurée.**

## Utiliser

Android 9/API 28 minimum. Choisir une vidéo locale MP4/H.264 SDR (≤ 1920 × 1920) ou la caméra arrière du téléphone, puis START. L'autorisation caméra n'est demandée qu'au choix de cette source. STOP arrête; START rejoue le fichier ou relance la caméra. Le passage en arrière-plan arrête le flux. Après recréation de l'écran, choisir à nouveau la caméra.

Avec les modèles provisionnés, les cadres verts indiquent les véhicules et les jaunes les plaques candidates. Le bouton Détection permet de comparer avec la lecture seule. Le diagnostic affiche le débit effectivement traité, les zones omises, les latences CPU p50/p95 et le temps depuis décodage. Les identifiants de piste apparaissent sur les cadres : provisoire après détection, confirmée après trois observations consécutives, perdue en gris pendant au plus 500 ms sans observation. Une prédiction perdue ne constitue pas une mesure de plaque. STOP, changement de source ou de géométrie et activation/désactivation de la détection réinitialisent le suivi.

Sans modèles, le lecteur et CameraX fonctionnent et la détection indique son indisponibilité. Aucun téléchargement n'est effectué par l'application. [Préparer les modèles et comprendre leurs limites/licences](models/README.md).

## Compiler et tester

JDK 17, SDK 35/build-tools 35.0.0, wrapper Gradle 8.11.1. Définir sdk.dir dans local.properties (non versionné) ou ANDROID_HOME :

```sh
./scripts/check.sh
```

Le script exécute les tests Python de métriques, les tests JVM, le formatage, lint et l'assemblage des APK application/tests. Sur ce poste, le JDK et le SDK sont isolés dans `.tools/` et sélectionnés par le script. Pour Gradle directement :

```sh
export JAVA_HOME="$PWD/.tools/jdk/Contents/Home"
export ANDROID_USER_HOME="$PWD/.tools/android-user"
export GRADLE_USER_HOME="$PWD/.tools/gradle-home"
./gradlew :app:connectedDebugAndroidTest
# Après provisionnement : imposer l'exécution des vrais modèles
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.requireModels=true
```

APK local : `app/build/outputs/apk/debug/app-debug.apk`. Les modèles AGPL ne sont pas suivis dans Git; ils sont inclus dans l'APK local seulement après provisionnement explicite. Le choix de licence de distribution de SpeedVision reste à arrêter avant publication d'un APK avec poids. La CI générique compile sans modèles et signale les tests de réseaux ignorés. Le workflow manuel peut exécuter les modèles sur le runner sans publier d'APK contenant les poids.

## Conception et preuves

- [Architecture](ARCHITECTURE.md), [faisabilité Meta](docs/FEASIBILITY.md), [algorithme futur](docs/ALGORITHM.md)
- [Backlog](BACKLOG.md), [sprints](SPRINTS.md), [tests](TESTING.md), [calibration](CALIBRATION.md)
- [Audit et versions des modèles](models/README.md), [évaluation détection](testing/detection/README.md), [protocole vitesse terrain](testing/README.md)
- [Rapport Sprints 0–1](docs/SPRINT_REPORT.md) et [rapport Sprint 2](docs/SPRINT_2_REPORT.md), [rapport Sprint 3](docs/SPRINT_3_REPORT.md)

La profondeur Z = fx W/w exige une pose et une calibration appropriées. Sa dérivée n'est pas une vitesse absolue routière. Un score de détecteur n'est ni une confiance de vitesse ni une précision acquise. Le modèle de plaques doit être évalué sur un corpus indépendant; aucune précision terrain n'est revendiquée.

## Données et Git

Traitement local, aucun OCR, aucune sauvegarde de vidéo ou de plaque, aucune permission Internet, microphone ou stockage global. Le sélecteur système donne accès au seul fichier choisi; préférer un fichier déjà local pour un essai hors ligne. L'APK n'embarque pas le SDK Meta. Les images publiques de smoke test sont téléchargées uniquement par le script de développement et ne sont pas des captures utilisateur.

Dépôt : [Valmdatascientest/speedvision](https://github.com/Valmdatascientest/speedvision). main stable, develop intégration, feature/*, fix/*, test/*. La branche du Sprint 3 est `feature/sprint-3-tracking`. La suite calibration/vitesse/voix/Meta reste planifiée.
