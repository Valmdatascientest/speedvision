# Sprint 2 — livraison technique et réserves de validation

## Livré

- CameraX avec autorisation à la demande, lifecycle Android, dernière image disponible, gestion des erreurs et fermeture des ressources.
- YOLO11n COCO pour véhicules, puis YOLO11n spécialisé plaques dans les ROI, exécutés réellement avec ONNX Runtime Android CPU.
- Préservation des pixels natifs, rotation avant inférence, letterbox inversé, coordonnées ROI ramenées à l'image, NMS et limites de travail explicites.
- Cadres véhicules/plaques, activation de la détection, compteurs et diagnostics p50/p95.
- Provisionnement local des poids avec révisions et SHA-256 vérifiés; absence de modèles signalée clairement.
- Outil Python de calcul précision/rappel et rappel par largeur de plaque; tests de matching TP/FP/FN.

Aucun tracking, calcul de distance/vitesse, OCR ou SDK Meta ajouté. Aucun entraînement depuis zéro.

## Preuves locales — 23 septembre 2026

| Vérification | Résultat |
|---|---|
| Formatage, compilation application et APK tests | Réussis |
| Tests JVM | 12 réussis, 0 échec |
| Tests Python métriques | 3 réussis, 0 échec |
| Tests instrumentés Android 15 / API 35 ARM64 | 10 réussis, 0 ignoré, 0 échec |
| Modèles obligatoires pendant cette exécution | `requireModels=true` |
| Lint | Réussi, 0 erreur |

L'inférence positive véhicule est testée sur la photo Ultralytics du bus. Un exemple déjà annoté de l'auteur du modèle plaque teste la détection positive et la cohérence des coordonnées parent/ROI. Une image noire teste les deux réseaux en négatif. Aucun de ces cas n'est un dataset indépendant permettant de revendiquer un rappel terrain.

CameraX : permission refusée, frames réelles de caméra émulée, dimensions/pixels/timestamps, STOP, passage de lifecycle et redémarrage. Rotation des pixels : test d'image connue à 90°. Les tests de lecture MP4, PTS, arrêt/relecture du Sprint 1 restent verts.

## Mesure exploratoire

ONNX Runtime 1.23.2, CPU 2 threads, appareil émulateur `sdk_gphone64_arm64`, API 35. Une chauffe, puis 8 passages sur l'image du bus : **p50 151,49 ms; p95 715,30 ms**. Le pipeline inclut pré/post-traitement et les étages véhicule/plaque; initialisation des sessions exclue. Une seule fixture et un émulateur ne caractérisent ni la chauffe ni la latence d'un téléphone. Le nombre de ROI influence fortement le coût. Pas de promesse de 24 fps ou de temps réel soutenu.

Les preuves sont dans `app/build/outputs/androidTest-results/connected/debug/`, notamment le log `SpeedVisionBenchmark`. Rapports JVM/lint dans les répertoires `build/reports` des modules. Le build local peut contenir les modèles; les builds CI génériques n'en contiennent pas.

## CI et publication

La première CI distante du commit `98adc98` a échoué pendant l'installation Android : le paquet historique `tools` n'existe plus. Le workflow demande maintenant explicitement `platform-tools`, avec caméra émulée activée. Les tests Kotlin d'inférence ont aussi été corrigés puis exécutés localement.

La [CI distante du commit `71651b9`](https://github.com/Valmdatascientest/speedvision/actions/runs/35872377853) est **verte** : jobs `build` et `device-tests` réussis le 23 septembre 2026. Le job optionnel `model-validation` n'a pas été exécuté. La CI générique exclut les poids et ignore les trois tests qui les exigent; les dix tests Android avec modèles obligatoires ont été exécutés localement comme indiqué ci-dessus. Le job manuel `local_model_validation` peut exiger les poids et ne publie aucun APK les contenant.

## Réserves qui empêchent de fermer tous les critères terrain du sprint

1. **Corpus indépendant absent** : rappel/precision sur petites plaques, faux positifs et cas difficiles restent non validés. Le corpus amont du modèle plaques a une contamination train/test déclarée.
2. **Téléphone cible absent** : comparaison CPU/GPU, mémoire, énergie, thermique et cycle de vie sur plusieurs téléphones restent à mesurer. La source CameraX est validée sur émulateur.
3. **Licence de distribution du projet ouverte** : les poids candidats sont AGPL-3.0; ils ne sont pas publiés dans Git. Aucun choix de licence du code SpeedVision n'a été fait à la place de l'utilisateur. Préparer un APK local n'est pas une décision de publication publique des poids.

S2-01 est validé sur émulateur. S2-02 et S2-03 ont une implémentation et des tests d'intégration réels, avec validation statistique terrain encore ouverte. Le Sprint 3 n'a pas été commencé.
