# Calibration — procédure prévue au Sprint 4

Aucun assistant ni estimation n'est activé au Sprint 1. Ne pas saisir une focale arbitraire pour obtenir une vitesse plausible.

1. Identifier caméra, firmware, mode vidéo, résolution native, crop, orientation et stabilisation. Si un changement modifie le modèle d'image, créer une nouvelle calibration.
2. Acquérir des images de mire damier/ChArUco aux dimensions mesurées, dans le flux réellement analysé. Couvrir le champ, plusieurs distances et orientations; éviter flou et scènes toutes frontales. Capturer uniquement sur action explicite.
3. Détecter les coins subpixel, résoudre K et distorsion avec OpenCV. Garder des vues à part pour vérifier reprojection et distances connues. Ne pas se contenter de la RMS d'entraînement.
4. Enregistrer fx, fy, cx, cy en pixels, modèle de distorsion et coefficients ordonnés, largeur/hauteur, sourceId, mode, date, erreur par vue, unités et version. L'assistant devra valider valeurs finies, fx/fy positifs, dimensions positives et correspondance exacte avec la source active.
5. Propager resize/crop : fx' = sx fx, fy' = sy fy, cx' = sx(cx - cropLeft), cy' = sy(cy - cropTop). Traiter séparément rotation/flip; une calibration non transformée est invalide.
6. Tester plusieurs plaques planes de largeur/hauteur connues à distances mesurées. Les dimensions « 520 × 110 mm » peuvent servir de profil de travail, jamais de dimension universelle ou détectée automatiquement. Prévoir profils pays/format et saisie mesurée. Refuser si format inconnu.
7. Contrôler biais vs distance et yaw. Fixer les seuils de rejet par validation, puis figer les paramètres pour le jeu test.

Si le SDK Meta fournit un jour des intrinsics utilisables, enregistrer leur provenance et vérifier leur cohérence avec le flux. Leur existence n'est pas présumée. Une stabilisation dynamique non modélisable peut imposer un mode vidéo différent ou empêcher une estimation fiable.

Sortie future : JSON versionné choisi/exporté explicitement par l'utilisateur; pas d'image de plaque nécessaire dans le profil. L'import doit refuser les valeurs non finies, le mauvais schéma ou une source incompatible.
