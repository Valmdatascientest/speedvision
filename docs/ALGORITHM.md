# Algorithme métrologique proposé

Le suivi temporel est implémenté au Sprint 3. Le Sprint 4 implémente la profondeur PnP à partir de coins manuels et d’une calibration connue sur image arrêtée. Géométrie automatique, incertitude validée et estimation de vitesse ci-dessous restent planifiées. Voir [architecture du tracker](../ARCHITECTURE.md#suivi-sprint-3).

## Grandeur mesurée et signe

Dans le repère caméra, un point de plaque a les coordonnées P = (X,Y,Z). Le pinhole donne u = fx X/Z + cx et v = fy Y/Z + cy après traitement de la distorsion. Pour une plaque frontoparallèle connue, w = fx W/Z, donc Z = fx W/w. W n'est pas déduit des pixels : c'est une hypothèse explicitement configurée. Z est une profondeur axiale, pas une distance euclidienne. La distance euclidienne serait ||P|| si toute la pose est estimée.

La vitesse affichable est v_close = -dZ/dt : positive en rapprochement, négative en éloignement. v_kmh = 3,6 v_close. Ce n'est pas en général la vitesse radiale -d||P||/dt, ni la vitesse routière. Même immobile dans le monde, un véhicule peut sembler bouger lorsque la caméra tourne.

## Géométrie

1. Associer la plaque au véhicule et maintenir une identité de piste.
2. Rejeter flou, occultation, faible résolution, incohérence de proportions et mauvaise calibration.
3. Cas initial contrôlé : largeur rectifiée seulement si frontalité démontrée. Pour un yaw theta et une petite plaque, w ≈ fx W cos(theta)/Z, donc Z ≈ fx W cos(theta)/w. Sans yaw fiable, ne pas appliquer une correction arbitraire; la formule frontale surestime Z d'environ 1/cos(theta).
4. Cas cible : détecter les quatre coins ordonnés, associer les points métriques (±W/2, ±H/2, 0), estimer R,t par PnP planaire en utilisant K et la distorsion. Retenir une solution de profondeur positive cohérente temporellement et de faible erreur de reprojection. Ambiguïtés planes à contrôler, sinon rejeter. [OpenCV solvePnP](https://docs.opencv.org/4.x/d5/d1f/calib3d_solvePnP.html).
5. Une homographie vers un rectangle de largeur choisie aide le contrôle/les coins, mais sa largeur de sortie ne peut pas entrer dans Z = fx W/w. L'échelle est imposée par le warp. La profondeur vient de la pose métrique ou de la géométrie originale calibrée.

Préserver crop/rotation/resize et propager K. Recalibrer en cas de mode vidéo, stabilisation ou résolution modifiés.

## Régression temporelle retenue

Choix de départ Sprint 5 : régression linéaire locale pondérée avec perte Huber, fenêtre 0,8 à 1,5 s configurable. C'est un compromis vérifiable face au bruit et aux points aberrants, sans hypothèse d'accélération nécessaire. Comparer sur exactement les mêmes traces avec OLS, Kalman position/vitesse, Savitzky–Golay et RANSAC.

- Garder les mêmes `trackId`, timestamps strictement croissants et distances acceptées. Réinitialiser sur changement de piste, gap > 300 ms (valeur initiale à valider) ou changement de calibration.
- Exiger ≥ 8 observations sur ≥ 0,7 s (seuils expérimentaux).
- Centrer les temps : tau_i = (PTS_i - PTS_ref) × 10^-6 secondes. Ajuster Z_i = a + b tau_i.
- Poids initiaux q_i / max(sigma_Zi², epsilon), avec q qualité géométrique. Moyennes pondérées tau_bar et Z_bar; b = somme(w_i (tau_i-tau_bar)(Z_i-Z_bar)) / somme(w_i (tau_i-tau_bar)²). Dénominateur trop petit : rejet.
- Trois itérations Huber : estimer une échelle robuste 1,4826 × MAD des résidus, plancher numérique; multiplier les poids initiaux par min(1, 1,345 s/|r_i|). Contrôler fraction d'inliers et résidus.
- Retourner -b, son signe, 3,6(-b), effectif, durée, score et profondeur au temps de référence. La fenêtre induit un retard et l'accélération peut biaiser l'estimation; contrôler les résidus temporels et réduire la fenêtre seulement si l'effectif reste suffisant.

Kalman est intéressant pour la prédiction, mais Q/R mal réglés peuvent produire une stabilité trompeuse; à étalonner. Savitzky–Golay suppose un échantillonnage adapté ou un ajustement aux temps irréguliers. RANSAC rejette bien de gros outliers, au prix d'une variance/latence aléatoire. Moyenne robuste seule ne définit pas une dérivée.

## Incertitude et qualité

Pour la formule frontale et des petites erreurs indépendantes :

sigma_Z² ≈ (W/w)² sigma_fx² + (fx/w)² sigma_W² + (fx W/w²)² sigma_w².

Ajouter incertitude de pose, corrélations et biais calibration. Les erreurs systématiques ne disparaissent pas en ajoutant des frames. Avec un modèle OLS idéal, Var(b) ≈ sigma_res² / somme((t_i-t_bar)²); cette approximation sous-estime une série corrélée et n'est pas un intervalle fiable avec tracking/Huber. Estimer la couverture par bootstrap en blocs et séquences indépendantes. Ne pas afficher « ±4 » sans couverture mesurée sur données tenues à part.

Le score de confiance initial est un score de qualité, pas une probabilité de précision : agrégation conservatrice des détecteurs, continuité, résolution, pose, calibration, netteté, résidus et mouvement. Un gate dur précède le score : absence de calibration, géométrie ambiguë, track perdu, stale data ou mouvement non contraint → résultat rejeté. Seuils versionnés et ajustés sur validation, pas sur jeu test. La voix nécessite en plus plusieurs fenêtres stables, âge maximal, intervalle minimal et delta significatif.

## Mouvement propre

dP_cam/dt = R_cw (V_vehicle - V_camera) - omega_cam × P_cam.

Donc dZ/dt inclut le terme rotationnel. Estimer la rotation synchronisée depuis une IMU effectivement disponible ou par flot du fond, excluant véhicules et objets mobiles. Une homographie du fond est valide surtout sous rotation pure ou scène approximativement plane; avec parallaxe, ne pas traiter son résidu comme une translation métrique. On peut ramener les observations à une orientation commune si la rotation est bien estimée; il faut alors redéfinir l'axe de référence explicitement.

Une caméra monoculaire avec fond inconnu n'identifie pas généralement la vitesse absolue du véhicule et celle de la caméra séparément. Optical flow/VO seuls peuvent avoir une échelle inconnue. IMU intégrée dérive; IMU téléphone non solidaire des lunettes inadaptée. En cas d'échec, invalider la vitesse; une simple baisse de score ne suffit pas si la grandeur est devenue ambiguë.

## Erreurs à provoquer pendant les essais

Yaw/roulis, changement d'orientation, vibration/rolling shutter, compression, contre-jour, pluie, flou, plaque partiellement cachée, dimensions incorrectes, passage d'un véhicule à l'autre, timestamps dupliqués/désordonnés, dropped frames, changement de résolution, reflets et texture ressemblant à une plaque. Mesurer surtout les faux résultats acceptés, pas seulement l'erreur des cas faciles.
