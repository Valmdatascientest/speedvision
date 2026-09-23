# Validation détection

Les tests de post-traitement utilisent des tenseurs et boîtes synthétiques : inversion du letterbox, offsets de ROI, portrait/paysage, NMS par classe, score invalide et mauvais schéma. Les tests Android exécutent les deux véritables réseaux sur une photo de référence, un exemple annoté de l’auteur et une image noire, ainsi que la rotation des pixels avant inférence.

Exécution obligatoire des modèles après provisionnement :

```sh
# Variables JAVA_HOME, ANDROID_USER_HOME, GRADLE_USER_HOME : voir README
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.requireModels=true
```

Sans poids, les trois tests de réseaux sont explicitement ignorés dans la suite générique; avec `requireModels=true`, leur absence est un échec. Le test caméra nécessite un émulateur muni d'une caméra arrière et une installation neuve sans autorisation caméra préaccordée. L'émulateur valide l'intégration, pas les performances du téléphone cible.

## Rappel sur annotations indépendantes

Copier `manifest.template.json` hors Git puis annoter des images entières dans le repère pixel redressé, avant tout resize du réseau. Ajouter les résultats de détection au même instant, y compris les images sans résultat et les scènes négatives. Exemple de schéma (coordonnées illustratives, pas une mesure) :

```json
{"id":"sessionA-frame001", "truth":[{"kind":"plate","box":[10,20,110,50]}], "predictions":[{"kind":"plate","box":[11,20,109,51],"score":0.8}]}
```

Placer les objets dans `frames`. Seuls `vehicle` et `plate` sont admis. Les listes vides sont des observations valides. Les scores doivent déjà respecter le seuil figé de l'expérience. Le matching est glouton par score, un à un, IoU ≥ 0,5; les doublons deviennent des faux positifs. Le rappel plaque est ventilé par largeur native <32, 32–63 et ≥64 px. Ce script calcule précision/rappel à seuil fixe, pas mAP.

```sh
python3 scripts/evaluate_detection.py /chemin/annotations.json --output /chemin/metrics.json
python3 -m unittest discover -s testing/detection
```

Chaque entrée doit documenter source, droits, sessions et absence de fuite train/test dans `dataset_note`. Aucune annotation réelle indépendante n'a été fournie : aucun rappel de terrain n'est revendiqué. Le manifeste vide est volontairement refusé. Les tests du calcul de métriques utilisent des cas dont les TP/FP/FN sont connus, y compris une image négative et une détection dupliquée.

## Performances

Le test `realVehicleAndPlateModelsRunAndReportTimings` chauffe le pipeline puis mesure huit passages CPU sur la photo de test. Le log `SpeedVisionBenchmark` donne matériel, API, n, p50/p95. Durées : preprocessing + session.run + postprocessing des véhicules et des ROI; chargement initial des sessions exclu. L'UI expose aussi temps depuis décodage et débit de résultats. Ces chiffres ne sont pas le FPS natif caméra, ni une comparaison CPU/GPU, ni une garantie temps réel. Une session thermique prolongée sur téléphone reste obligatoire.
