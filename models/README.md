# Modèles du Sprint 2

## Choix et audit

| Usage | Poids source | Révision | Licence annoncée |
|---|---|---|---|
| Véhicules | [Ultralytics YOLO11n](https://huggingface.co/Ultralytics/YOLO11) · yolo11n.pt | 8b8ac7d1fae7468f85dbf89670dd66f41f485aab | AGPL-3.0 |
| Plaques | [morsetechlab YOLO11n plaques](https://huggingface.co/morsetechlab/yolov11-license-plate-detection) · license-plate-finetune-v1n.onnx | 251a30d7daedca065f56e04b0af04052c907c68f | AGPL-3.0 |

Le candidat plaques est explicitement présenté par son auteur comme entraîné sur un corpus dont les splits sont contaminés. Ses scores publiés ne constituent donc pas une preuve indépendante; aucun score amont n'est repris comme performance SpeedVision. Ce choix permet de tester une intégration réelle, pas de valider la généralisation aux plaques françaises ou au flux lunettes.

Architecture nano pour limiter le coût, même interface brute pour deux détecteurs. ONNX Runtime Android 1.23.2 CPU/2 threads est la référence initiale. TFLite/GPU/NNAPI ne sont pas supposés plus rapides : comparaison sur téléphone physique encore à effectuer. Aucun réseau n'est exécuté dans un service distant.

## Provisionnement explicite

Les poids, la mire photographique et le manifeste généré sont ignorés par Git. Aucun modèle ne se télécharge au lancement de l'application. Une compilation sans modèles reste utilisable comme lecteur/caméra; elle indique clairement que la détection est indisponible.

```sh
python3 -m venv .tools/ml
.tools/ml/bin/pip install -r scripts/requirements-models.txt
.tools/ml/bin/python scripts/prepare_models.py --accept-agpl
./scripts/check.sh
```

Ce drapeau autorise l'opération locale du script; il **ne change pas la licence du dépôt**. Le choix de licence de SpeedVision reste ouvert. Ne pas publier un APK embarquant ces poids sans avoir arrêté les conditions de distribution. Les sources primaires sont [licence Ultralytics](https://www.ultralytics.com/license) et les cartes de modèle liées ci-dessus. Aucun checkpoint n'est relabellisé MIT sur la foi d'un miroir tiers.

Le script vérifie SHA-256 des poids source et de l'image de test, exporte le checkpoint véhicule avec Ultralytics 8.3.221/ONNX 1.19.1, fixe les dimensions de l'interface plaque puis vérifie réellement les sorties avec ONNX Runtime. Les versions Python directes sont épinglées dans `scripts/requirements-models.txt`. Les hashes des exports et l'environnement sont enregistrés dans `app/src/main/assets/models/runtime.json`; ils peuvent différer selon les versions/plateformes d'export, d'où la distinction entre hash source épinglé et hash d'artefact généré. L'application vérifie ce dernier avant d'ouvrir chaque session.

Entrée fixe float32 RGB NCHW `[1,3,640,640]`, couleurs /255 et padding 114. Sorties `[1,84,8400]` pour COCO et `[1,5,8400]` pour la plaque, sans objectness séparée ni NMS embarquée. Garder car/motorcycle/bus/truck (2/3/5/7), score ≥ 0,35, NMS IoU 0,45, 30 résultats max. Ces seuils expérimentaux ne sont pas calibrés sur un corpus français. Les plaques sont cherchées uniquement dans les quatre meilleurs véhicules; l'UI affiche les zones omises. Une plaque dont le véhicule a été manqué peut donc être manquée aussi.

La photo `bus.jpg` provient du dépôt Ultralytics v8.3.221 et sert exclusivement au test d'intégration. Elle n'est ni un jeu de rappel indépendant ni une preuve de précision métrologique. Aucun OCR n'est exécuté.
