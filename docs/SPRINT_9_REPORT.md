# Sprint 9 — première tranche : évaluation reproductible

Outil `scripts/evaluate_speed.py` ajouté pour les exports réels du laboratoire. Appariement strict à l'instant de référence de la fenêtre, identité complète, MAE/RMSE/biais/écart-type population, couverture, rejets et prédictions sans référence. JSON avec empreintes des entrées, sans écrasement de fichier. Absence de comparaison représentée par `null`.

Six tests dédiés vérifient les erreurs signées connues, la couverture incomplète, les mauvais instants/calibrations, les rejets, doublons, valeurs non finies, unités incohérentes, percentiles et exécution CLI sans écrasement. Ils sont intégrés à la découverte Python existante de `scripts/check.sh`.

[Protocole et commande](../testing/speed/EVALUATION.md). Les temps exportés concernent uniquement la régression du laboratoire. Aucun benchmark caméra→résultat ou énergétique n'est revendiqué.

Restent ouverts : acquisition indépendante synchronisée, validation du matériel Meta, baseline soutenue sur Z Flip7, énergie/thermique, puis comparaison float/FP16/INT8 avec contrôle de précision et couverture sur un split réservé. Aucun poids n'a été quantifié sans données de calibration admissibles. Cette tranche prépare la mesure ; elle ne démontre pas une précision terrain.

Validation locale de l’outil : six tests dédiés réussis, plus le test existant d’assemblage CSV. Aucune donnée de terrain utilisée.
