# Sprints

Cadence indicative : une à deux semaines, réévaluée après chaque revue. Les Sprints 0 et 1 sont fermés pour leur portée logicielle et documentaire ; voir [la revue de clôture](docs/SPRINT_REPORT.md). L’incrément logiciel du Sprint 6 est validé. Le Sprint 8 est validé localement côté logiciel ; essais matériels ouverts. Le Sprint 9 est livré côté logiciel ; ses mesures scientifiques et matérielles restent conditionnelles au corpus et aux instruments disponibles. Le raccordement vocal live et les essais audio du Sprint 7 restent ouverts.

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
| 9 | Évaluation reproductible, benchmark Android, optimisation mémoire et documentation | Mesures indépendantes reproductibles ; FP16/INT8, énergie et essais terrain séparés |
| 10 | Passage vers la mesure live : profondeur plaque suivie, fallback largeur véhicule et calibration guidée | Série de profondeurs issue de frames réelles, rejets explicites, comparaison plaque/véhicule et validation sur scènes réservées |
| 11 | Stabilité caméra, estimation temps réel et annonces live | Vitesse seulement après fenêtre stable, caméra vérifiée, latence/thermique mesurés et silence sur toute incertitude |

Revue obligatoire après chaque sprint. Les portes de sortie des Sprints 0 et 1 sont maintenant documentées et fermées pour le dépôt. Le Sprint 5 est implémenté : vitesse relative sur séries horodatées et export explicite implémentés. Les validations terrain du Sprint 2 restent ouvertes et le tracking nécessite aussi des séquences indépendantes. La détection automatique de coins et la validation métrique terrain restent ouvertes. Le Sprint 6 ajoute un alignement visuel du fond avec rejets; il ne certifie pas l'immobilité ni la translation métrique. Voir docs/SPRINT_6_REPORT.md. L'absence de matériel ou d'un modèle plaque admissible bloque les stories correspondantes, pas la lecture vidéo indépendante. Le projet global reste conditionné aux portes de sortie des sprints suivants, et une CI non exécutée ne vaut pas une CI verte.

Sprint 7 : première tranche décrite dans [docs/SPRINT_7_REPORT.md](docs/SPRINT_7_REPORT.md). Sprint 8 autorisé et engagé; voir [rapport](docs/SPRINT_8_REPORT.md). Sprint 9 livré côté logiciel ; voir [rapport](docs/SPRINT_9_REPORT.md). Les essais terrain, l’énergie et la quantification restent des travaux distincts nécessitant des données et mesures admissibles.

Le Sprint 10 commence par un estimateur de profondeur de secours fondé sur des profils de largeur de véhicule. Il est explicitement distinct de la pose par plaque : une largeur moyenne de classe ne constitue pas une calibration ni une vérité terrain. La profondeur live, la calibration guidée et les validations indépendantes restent à implémenter avant toute vitesse affichée sur l’aperçu.
