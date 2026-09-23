# Dataset et protocole de validation (préparation)

Le répertoire contient un schéma de manifeste vide, pas un dataset routier prétendument validé. La fixture vidéo de test vit dans app/src/androidTest/assets. Collecter uniquement avec consentement/action explicite; définir conservation et accès. Ne pas committer des plaques ou vidéos personnelles.

Chaque séquence : identifiant, fichier local hors Git, checksum, source/mode, calibration, dimensions plaque mesurées, trajectoire/caméra, éclairage, vitesse de référence et incertitude, synchronisation et split. Associer un fichier de référence par frame avec `pts_us,reference_axial_closing_mps,reference_uncertainty_mps`.

1. Commencer caméra fixe, plaque plane connue, trajectoire alignée; mesurer distance de référence vs temps indépendamment (banc translation ou instrumentation synchronisée).
2. Synchroniser les horloges par un événement visible, estimer offset et dérive; reporter l'incertitude temporelle. La vitesse GPS d'un véhicule seule n'est pas la vérité terrain de la vitesse axiale relative avec caméra mobile.
3. Augmenter progressivement distance, angle, flou et illumination. Ajouter véhicules croisés, occultations, caméras mobiles, rotations et scènes sans plaque.
4. Séparer train/tuning/test par session, véhicule et lieu, pas par frames voisines. Figer paramètres avant test.
5. Calculer sur les couples valides synchronisés e_i = v_est_i - v_ref_i, MAE = mean(|e|), RMSE = sqrt(mean(e²)), biais = mean(e), écart-type = sqrt(mean((e-biais)²)). Reporter unités, effectif et incertitude de la référence.
6. Rapporter aussi proportion rejetée sur toutes les opportunités éligibles, faux résultats acceptés, pertes de tracking par track-minute, durée des pertes, FPS traités, latences bout-en-bout p50/p95, énergie et température après session soutenue. Une faible MAE avec 99 % de rejets est insuffisante.
7. Tracer Z/temps, v/temps, erreur/temps avec gaps/rejets visibles; bootstrap par séquence pour les intervalles. Réévaluer la couverture des intervalles de vitesse, pas seulement le score.

Objectif exploratoire : MAE < 5 km/h dans conditions favorables documentées, sous réserve de couverture et de taux de rejet acceptables à fixer avant campagne. Pas une précision acquise.
