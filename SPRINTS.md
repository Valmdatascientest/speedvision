# Sprints

Cadence indicative : une à deux semaines, réévaluée après chaque revue. Le périmètre de cette livraison s'arrête au Sprint 1.

| Sprint | Livrable et dépendances | Porte de sortie |
|---|---|---|
| 0 | Faisabilité Meta, mathématiques, architecture, backlog, risques, bootstrap Gradle/CI | Sources primaires datées, aucun accès matériel inventé, build reproductible |
| 1 | App Compose/MVVM/Hilt, vrai lecteur vidéo, PTS, START/STOP, erreurs et tests | Build/lint/format/unit verts; tests Android exécutés et limites documentées |
| 2 | Benchmark véhicules YOLO11n, poids plaques audité, pipeline ROI et CameraX de test | Licence/provenance vérifiées, rappel par taille de plaque, budget p95 mesuré |
| 3 | Tracking Kalman + association IoU/Hungarian (SORT initial), séparation détection | Tests croisements/occultations, identités stables, reset après perte |
| 4 | Assistant calibration, profils plaques, bbox contrôlée puis coins/PnP | Tests distance, angles et calibration invalide; incertitude visible |
| 5 | Régression Huber, comparaison des filtres, invalidation et qualité | Synthétique puis vidéos de référence, MAE/RMSE et rejet, pas de vitesse absolue |
| 6 | Flot de fond/rotation, capteurs réellement disponibles et synchronisation | Rejet des mouvements non observables, tests tête/rotation/parallaxe |
| 7 | TTS, stabilité/anti-spam, routes téléphone/Bluetooth | Pas de voix sous seuil, tests route et déconnexion matérielle |
| 8 | MetaGlassesVideoSource avec SDK officiel revalidé | Appairage, autorisations, frames, reconnexion, thermique et PTS sur matériel |
| 9 | Optimisation FP16/INT8, essais terrain, analyse et documentation | Mesures indépendantes reproductibles, limites et énergie rapportées |

Revue obligatoire après chaque sprint. Aucune extension au Sprint 2 dans cet incrément. L'absence de matériel ou d'un modèle plaque admissible bloque les stories correspondantes, pas la lecture vidéo indépendante. Le projet global n'est pas terminé avec le Sprint 1, et une CI non exécutée ne vaut pas une CI verte.
