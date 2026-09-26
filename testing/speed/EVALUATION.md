# Évaluation indépendante — Sprint 9

Après export des résultats du laboratoire Android :

```sh
python3 scripts/evaluate_speed.py --results resultats.csv --reference reference.csv --output evaluation.json
```

La sortie doit être nouvelle. Aucun fichier n'est téléversé. Le JSON inclut les SHA-256 des deux entrées et la version de l'algorithme exportée.

Référence UTF-8, en-tête exact :

```csv
sequence_id,track_id,calibration_id,reference_us,speed_mps
```

Vitesse **relative axiale signée** en m/s : rapprochement positif. Une vitesse routière absolue issue d'un compteur ne constitue pas directement cette référence. Documenter séparément instrument, précision, synchronisation des horloges, projection sur l'axe caméra, calibration et identité du véhicule. Ne pas produire la vérité terrain à partir des profondeurs évaluées.

L'appariement est exact sur prise, piste, calibration et `reference_us` (centre de fenêtre exporté), jamais sur le temps de fin de calcul ou `timestamp_us`. Aucune interpolation, tolérance ou correction d'horloge automatique. Préparer la référence aux instants requis selon un protocole documenté. Les instants manquants ne sont pas supprimés du dénominateur : conserver une grille d'évaluation définie avant inspection des résultats, incluant échecs et scènes difficiles. La couverture vaut comparaisons / références. Les résultats acceptés sans référence et les motifs de rejet sont comptés séparément.

MAE, RMSE, biais (estimation moins référence) et écart-type population sont en m/s, uniquement sur les paires appariées. Sans paire, ils valent `null`, pas zéro. Les doublons et nombres non finis sont refusés. Évaluer chaque algorithme séparément avec exactement la même grille ; comparer erreur **et** couverture, par prise et condition, avant toute agrégation. Des fenêtres successives se recouvrent : elles ne sont pas des répétitions indépendantes pour un intervalle de confiance.

Réserver les prises de test avant réglage : aucune image d'une même prise/scène ne doit entrer dans entraînement, calibration de quantification ou sélection de seuils. Consigner luminosité, taille de plaque, distance, mouvement, matériel et version. Inclure prises négatives. Aucun corpus terrain n'est livré ici.

`replay_compute_ms` donne les percentiles p50/p95 interpolés du calcul de régression pour toutes les observations. Ce n'est ni la latence caméra→résultat, ni le temps des modèles, ni un débit live. Les mesures énergie/thermique sur Z Flip7 et les comparaisons float/FP16/INT8 restent à réaliser avant sélection d'une optimisation.
