# Validation Sprint 3

Le tracker ajoute 11 tests JVM déterministes, dont croisements, perte/expiration, resets, association des plaques et comparaison Hungarian à une recherche exhaustive. Les tests Android incluent le suivi des détections ONNX réelles sur une image répétée et les compteurs Compose. Voir [rapport Sprint 3](docs/SPRINT_3_REPORT.md) pour les résultats et limites. Les commandes `scripts/check.sh` et `connectedDebugAndroidTest` restent identiques.

# Tests et validation — état Sprint 2

`./scripts/check.sh` exécute tests Python de métriques, tests JVM, formatage, lint et compilation des APK. Les variables du [README](README.md) permettent de lancer les tests instrumentés sur Android. Pour exiger les poids réels :

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.requireModels=true
```

Préparer les poids selon [models/README.md](models/README.md). Sans poids, trois tests ONNX sont explicitement ignorés; l'argument ci-dessus transforme leur absence en échec. Aucun mock de réseau n'est substitué. La CI générique compile un APK sans modèles; le job manuel de validation peut les préparer pour ses tests et ne publie que les rapports.

Couverture Sprint 2 : letterbox portrait/paysage/ROI, sortie YOLO brute, classes, NMS, rejets numériques, fenêtre de percentiles; rotation pixels; inférence réelle véhicule/plaque sur exemples positifs et entrée noire; CameraX permission refusée, vraies images émulateur, arrêt/reprise. Voir [protocole détection](testing/detection/README.md). Les tests de lecture Sprint 1 sont conservés.

La suite locale comprend 12 tests JVM, 10 tests instrumentés avec modèles et 3 tests Python de métriques. Voir le [rapport Sprint 2](docs/SPRINT_2_REPORT.md) pour résultats et limites. Les mesures de temps sur émulateur sont des smoke benchmarks; le rappel sur données indépendantes et la comparaison accélérateurs sur téléphone restent à réaliser.

## Historique des essais de lecture et protocole vitesse


## Commandes

Java 17 et SDK Android 35 requis. Pour l'environnement local préparé, `./scripts/check.sh` sélectionne le JDK et les caches locaux. Ailleurs, définir JAVA_HOME et sdk.dir dans local.properties (ou ANDROID_HOME).

```sh
./scripts/check.sh
# Sur un appareil connecté/émulateur (voir variables locales ci-dessous)
./gradlew :app:connectedDebugAndroidTest
```

Le script vérifie formatage, tests JVM domaine, tests JVM Android, lint et compilation des APK application/tests. Les tests instrumentés nécessitent un système Android et sont distincts de leur compilation. La CI comporte aussi un job émulateur API 35. Les tests du moteur géométrique/vitesse seront ajoutés avec ce moteur aux Sprints 4–5; aucune implémentation future n'est présentée comme testée.

## Tests présents

- FrameSampler : PTS irréguliers, négatifs, doublons, régression temporelle, restart, intervalle invalide.
- Conversion YUV : niveaux noir/blanc, rouge, saturation.
- Android : véritable MP4 H.264 décodé par MediaCodec, dimensions des pixels, monotonie PTS, fin et replay; fichier absent, arrêt idempotent, arrêt pendant lecture, double START et redémarrage.
- Compose : aucun START sans sélection, aucune vitesse fabriquée.

`app/src/androidTest/assets/sample.mp4` est une mire synthétique de 1 s générée avec FFmpeg (320 × 240, 15 fps). Elle vérifie l'ingestion, pas l'exactitude d'une vitesse. Génération reproductible :

```sh
ffmpeg -f lavfi -i 'testsrc2=size=320x240:rate=15' -t 1 -c:v libx264 -pix_fmt yuv420p -an -y app/src/androidTest/assets/sample.mp4
```

## Essais manuels Sprint 1

Sélectionner un MP4 local H.264 SDR ≤ 1920 × 1920. Essayer portrait avec rotation, EOF/replay, STOP/START rapide, remplacement pendant lecture, fond/premier plan, rotation écran, annulation du sélecteur, fichier corrompu/non vidéo et fournisseur inaccessible. Vérifier absence de copie et de permission globale, aucune voix, aucune valeur de vitesse. Les codecs et strides dépendent du matériel : essais complémentaires sur au moins deux téléphones nécessaires avant de déclarer la compatibilité générale. HDR/4K exclus explicitement dans ce prototype.

## Tests futurs de calcul

Projection pinhole/PnP avec distances connues, distorsion, yaw/roulis et coins bruités. Estimation vitesse sur PTS irréguliers, gaps, bruit corrélé, outliers, accélération, changement de piste. Tests de confiance : mouvement ambigu/calibration absente imposent rejet, résultat périmé impose silence. Vérifier taux de faux résultats acceptés. Comparer Huber/Kalman/Savitzky–Golay/RANSAC par séquence entière.

## Validation terrain

Voir [protocole et manifeste](testing/README.md). Aucune séquence réelle avec vérité terrain n'a été fournie. Le dataset ne peut pas être honnêtement fabriqué : seule la fixture d'ingestion est synthétique. Aucun MAE terrain, FPS d'inférence ou résultat Meta n'est revendiqué.

Les résultats de cette exécution sont consignés dans [docs/SPRINT_REPORT.md](docs/SPRINT_REPORT.md). Une CI configurée mais non déclenchée n'est pas une CI réussie.
