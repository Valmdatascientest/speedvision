# Sprint 9 — baseline détecteurs sur Android

Le test `DetectionBenchmarkTest` est **ignoré par défaut**. Il utilise les vrais modèles installés et l'image de régression publique `bus.jpg`, répétée, déjà décodée. Il mesure le pipeline véhicules → plaques (prétraitement et post-traitement inclus), pas une caméra ni la précision des plaques. Il n'enregistre aucun pixel, identifiant matériel unique, son ou vidéo.

## Exécution explicite

Provisionner les modèles selon `models/README.md`, puis utiliser l'environnement Java/SDK du README. La sélection `-s SERIAL` doit viser le téléphone voulu. Ne pas confondre téléphone et émulateur.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w \
  -e class fr.speedvision.DetectionBenchmarkTest \
  -e runBenchmark true -e benchmarkSamples 100 -e benchmarkWarmup 5 \
  fr.speedvision.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL exec-out run-as fr.speedvision cat files/detection-benchmark.json > capture.json
python3 scripts/analyze_performance.py capture.json --output performance.json
```

Vérifier `OK (1 test)` avant récupération. Utiliser directement `am instrument` pour cette capture : la tâche Gradle `connectedDebugAndroidTest` peut désinstaller l'application en fin de test et supprimer ses fichiers. La variante Meta peut être compilée avec `-PmetaEnabled=true`; le benchmark n'active pas le SDK caméra. Aucun APK avec poids n'est à publier automatiquement.

Le test refuse d'écraser une capture. Après l'avoir récupérée et vérifiée, supprimer explicitement la copie privée pour permettre une nouvelle session :

```sh
adb -s SERIAL shell run-as fr.speedvision rm files/detection-benchmark.json
```

Le fichier local `capture.json` contient les empreintes SHA-256 des deux modèles et du fixture, version d'app, profil Meta, modèle du téléphone, API/ABI, provider CPU et threads (2 intra, 1 inter). Conserver aussi le commit Git testé, les APK localement, version Android complète, état de charge, température ambiante, luminosité écran, applications concurrentes et conditions de refroidissement. Les rapports ne sont pas une archive complète de l'environnement.

## Interprétation

- `cold_call_ms` : premier appel, initialisation des sessions comprise, hors chargement du fixture et calcul des empreintes.
- Warmup : 1 à 100 appels supplémentaires, exclus des échantillons. Mesure : 5 à 5 000 appels. Une session dépassant 30 minutes après warmup échoue sans rapport complet.
- `vehicle_ms`, `plate_ms`, `total_ms` : durées internes ; `call_ms` englobe l'appel suspendu et sa planification. Horloge monotone Android, pas PTS vidéo.
- Percentiles p50/p95 **nearest-rank**, comme l'affichage existant. Le débit vaut appels terminés / temps réel entre début du premier et fin du dernier, pauses d'instrumentation incluses. Ce n'est pas un FPS live ni `1000 / p50`.
- ROI traitées/omises : une ROI omise ne doit pas être comptée comme une plaque absente. Les comptages sont des occurrences par appel, pas des véhicules uniques.
- État thermique système échantillonné après chaque appel : 0 à 6 à partir d'API 29, `null` sous API 29. Ce n'est pas une température et les transitions entre échantillons ne sont pas observées. [Contrat Android PowerManager](https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus()).
- Médianes du premier/dernier quart : diagnostic de dérive seulement, sans conclure automatiquement à un throttling.

L'analyse refuse schéma inconnu, appels chevauchants, durées non finies/incohérentes et nombres de ROI invalides. La sortie JSON est nouvelle, jamais écrasée, et contient le SHA-256 de la capture source. Conserver cette capture pour retrouver toutes les métadonnées.

## Protocole Z Flip7 et optimisation

Faire plusieurs sessions à conditions comparables après refroidissement, alterner l'ordre des variantes, conserver les échecs. Augmenter progressivement les échantillons pour atteindre une durée soutenue mesurée. Une répétition de fixture ne remplace pas les séquences indépendantes avec petites plaques, variations d'éclairage, occultations et mouvements.

Avant FP16/INT8 : baseline réelle, provider et opérateurs compatibles vérifiés, jeu de calibration de quantification distinct du test, comparaison du rappel par taille et de la vitesse avec couverture sur les mêmes prises. Aucun modèle quantifié n'est activé par cette tranche.

L'énergie n'est **pas mesurée** par cet outil. Ne pas convertir un pourcentage batterie ou un état thermique en joules. Une mesure système contrôlée ou un instrument externe, avec baseline au repos et conditions de charge documentées, reste nécessaire pour attribuer un coût énergétique. L'absence de caméra, de tracking et de rendu interdit d'extrapoler cette capture à l'autonomie de l'application complète.
