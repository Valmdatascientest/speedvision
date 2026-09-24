# Sprint 4 — calibration et profondeur manuelle

## Livré

- Contrats de calibration, géométrie source exacte, dimensions physiques et quatre coins ordonnés dans le domaine Kotlin.
- Assistant Android sur image arrêtée : saisie de paramètres natifs connus, provenance/RMS, attestation du mode, profil de dimensions, zoom et sélection des coins, calcul et motifs de rejet.
- Import/export explicite JSON v1 sans image; validation des nombres, modèle Brown5, limites de taille et compatibilité source/crop/résolution/rotation. Pas de sauvegarde automatique.
- Moteur OpenCV Android 4.12.0 : IPPE planaire, profondeur axiale métrique, contrôle des profondeurs des coins, reprojection, inclinaison et ambiguïté. Libération des matrices natives.
- Outil hors application de calibration sur damier avec au moins 12 vues de réglage et 4 vues réservées, contrôle des doublons et rapport de reprojection par vue.
- Version Android 0.4.0. Vitesse, voix et Meta non ajoutés.

## Choix et limites

Les modèles YOLO fournissent des boîtes, pas les coins physiques. L'assistant utilise donc des coins explicitement sélectionnés; aucune distance automatique issue d'une boîte supposée frontale. Une image entière est conservée en mémoire après arrêt, avec son PTS, sans nouveau fichier image. Le résultat n'est pas rattaché automatiquement à une piste et ne nourrit pas une fenêtre de vitesse.

La profondeur Z est axiale, pas une distance euclidienne ou une vitesse. L'incertitude métrique est **non quantifiée**, mentionnée dans l'interface. Reprojection et inclinaison sont des diagnostics. Un test démontre qu'une mauvaise échelle physique peut doubler la profondeur tout en conservant une excellente reprojection.

Le mode optique doit être connu et fixe. Le descripteur logiciel ne détecte pas tous les changements de lentille/zoom/stabilisation, ni le remplacement d'un fichier au même URI. La confirmation explicite et la provenance sont nécessaires; elles ne constituent pas une certification. Les acquisitions sur mire ne sont pas intégrées à l'application.

## Validation

Validation locale du 24 septembre 2026 : **27 tests JVM, 4 tests Python et 17 tests Android réussis**, zéro échec et zéro test Android ignoré. Les tests Android utilisent OpenCV et les poids ONNX réels (`requireModels=true`) sur émulateur Android 15/API 35 ARM64. Le test Compose complète le parcours paramètres → coins → profondeur → effacement.

Formatage, compilation, lint et assemblage des APK réussis. Preuves locales : `.tools/sprint4-check-final.log`, `.tools/sprint4-device-final.log` et rapports XML sous `build`. Contrôle visuel sur émulateur : assistant, géométrie de l’image arrêtée, zoom et boutons désactivés sans paramètres/coins vérifiés. Captures locales `.tools/sprint4-workbench.png` et `.tools/sprint4-corners.png`, non publiées. La [CI distante du commit `054654e`](https://github.com/Valmdatascientest/speedvision/actions/runs/35979925964) est **verte** : jobs `build` et `device-tests` réussis. Le job optionnel `model-validation` n’a pas été exécuté; la CI générique sans poids ignore les trois tests ONNX qui les exigent. Les nouveaux tests de géométrie et d’assistant y sont exécutés. La suite locale ci-dessus impose aussi les poids ONNX.

Les tests couvrent la transformation native pour les quatre rotations, valeurs non finies et paramètres invalides, ordre/taille des coins, refus de poses ambiguës/négatives, récupération de profondeur avec yaw/roulis/distorsion, crop/rotation, coins bruités, petite plaque/source incompatible, échelle physique erronée, JSON et parcours Compose. La calibration Python est testée sur des projections analytiques à intrinsics connus avec vues réservées. Aucun test synthétique ne constitue une mesure de précision terrain.

L’APK local de debug universel mesure environ 255 Mo avec les poids locaux et les bibliothèques OpenCV multi-architectures. Il n’est pas un paquet de distribution optimisé; les poids restent absents de Git et des builds CI génériques.

## Critères encore ouverts

- Détection automatique des quatre coins, acquisition intégrée de mire et estimation de distance continue.
- Calibration d'une caméra réelle vérifiée avec distances de référence indépendantes; évaluation du biais selon angle, profondeur, résolution et flou.
- Incertitude métrique étalonnée et essais sur téléphone cible.
- Réserves héritées sur corpus de détection/tracking et distribution des poids AGPL.

S4-01/02/03 disposent de leurs composants techniques pour des essais manuels contrôlés. La validation matérielle et le passage à une mesure automatique restent ouverts. Le Sprint 5 n'a pas été commencé. Procédure complète dans [CALIBRATION.md](../CALIBRATION.md).
