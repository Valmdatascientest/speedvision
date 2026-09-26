# Sprint 9 — première tranche : évaluation reproductible

Outil `scripts/evaluate_speed.py` ajouté pour les exports réels du laboratoire. Appariement strict à l'instant de référence de la fenêtre, identité complète, MAE/RMSE/biais/écart-type population, couverture, rejets et prédictions sans référence. JSON avec empreintes des entrées, sans écrasement de fichier. Absence de comparaison représentée par `null`.

Six tests dédiés vérifient les erreurs signées connues, la couverture incomplète, les mauvais instants/calibrations, les rejets, doublons, valeurs non finies, unités incohérentes, percentiles et exécution CLI sans écrasement. Ils sont intégrés à la découverte Python existante de `scripts/check.sh`.

[Protocole et commande](../testing/speed/EVALUATION.md). Les temps exportés concernent uniquement la régression du laboratoire. Aucun benchmark caméra→résultat ou énergétique n'est revendiqué.

Restent ouverts : acquisition indépendante synchronisée, validation du matériel Meta, baseline soutenue sur Z Flip7, énergie/thermique, puis comparaison float/FP16/INT8 avec contrôle de précision et couverture sur un split réservé. Aucun poids n'a été quantifié sans données de calibration admissibles. Cette tranche prépare la mesure ; elle ne démontre pas une précision terrain.

Validation locale de l’outil : six tests dédiés réussis, plus le test existant d’assemblage CSV. Aucune donnée de terrain utilisée.

## Deuxième tranche — baseline Android et comparaison

`DetectionBenchmarkTest` ajouté uniquement aux sources instrumentées. Activation explicite obligatoire, vrais poids locaux, fixture bus répété, empreintes modèle/image et contexte CPU/version/API/ABI. Premier appel à froid et warmup séparés, échantillons monotones avec durées véhicules/plaques/appel complet, ROI omises et états thermiques système. Capture dans les fichiers privés de l'application ; refus d'écrasement, aucun export automatique en production.

`analyze_performance.py` vérifie la cohérence de la capture et calcule p50/p95 nearest-rank, débit sur durée écoulée, compte des états thermiques et médianes début/fin. Quatre tests couvrent calcul connu, pauses, percentiles, état absent, mesures invalides et capture trop courte. [Exécution sur Z Flip7 et limites](../testing/performance/README.md).

`compare_speed_reports.py` exige le même SHA-256 de référence et des comptes cohérents. Il rapporte les deltas d'erreur et de couverture sans choisir automatiquement une variante, ni prétendre comparer les mêmes observations acceptées. Quatre tests supplémentaires couvrent perte de couverture, références différentes, absence de comparaisons et métriques invalides.

## Validation de cette tranche

- `scripts/check.sh` réussi : format, 54 tests JVM, tests Python existants et performance, benchmark synthétique, lint, assemblage APK/test et audit du manifeste sans SDK.
- Après ajout de la comparaison : 11 tests Python vitesse et 4 tests performance réussis (19 tests Python au total avec détection/calibration).
- Benchmark instrumenté opt-in réussi sur émulateur API 35 ARM64 : premier appel, un warmup, cinq appels mesurés ; récupération du JSON et analyse réellement exécutées. Aucun modèle de téléphone physique testé. Cette capture courte constitue uniquement un test de l'outillage ; elle ne représente pas un benchmark soutenu.
- Compilation du nouveau test dans le profil Meta réussie. Exécution sans opt-in vérifiée : aucun nouveau benchmark. CI de branche : à déclencher.

Aucune optimisation FP16/INT8 activée. Les portes matérielles du Sprint 9 restent ouvertes : sessions soutenues sur Z Flip7, corpus indépendant, énergie, thermique et contrôle de précision après optimisation. Le script ne mesure pas l'énergie et son débit n'est pas un FPS caméra.
