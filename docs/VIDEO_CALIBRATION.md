# Calibration par vidéo à vitesse connue

Le parcours utilise les observations de profondeur déjà produites par la chaîne véhicule → piste → largeur apparente. Il ajuste uniquement `vehicleWidthScale`, un facteur explicite du modèle de largeur moyenne de la classe. Il ne modifie pas silencieusement les intrinsics `fx/fy`, la distorsion ou la géométrie de la source.

## Procédure

1. Sélectionner une vidéo dont la vitesse réelle est connue et suffisamment constante.
2. Ouvrir **Calibration / distance** et utiliser **Préremplir une calibration approximative**, puis appliquer le profil après avoir lu l’avertissement.
3. Activer **Caméra fixe déclarée**, lancer la vidéo et attendre une piste confirmée.
4. Arrêter après au moins huit observations réparties sur environ 0,7 seconde.
5. Rouvrir **Calibration / distance**, saisir la vitesse réelle en km/h et choisir **Ajuster avec la vidéo connue**.
6. Exporter le profil JSON ou le laisser dans le stockage local de l’application pour cette géométrie de source.

Le moteur conserve les PTS source, exige une identité de séquence/piste/calibration constante, rejette les gaps de plus de 300 ms et vérifie la qualité de la régression Huber existante. Il ajuste le facteur dans `[0,25 ; 4]` et refuse une erreur résiduelle supérieure à 0,5 m/s ou une qualité inférieure à 0,6. Le résultat expose le nombre de frames, les inliers, la stabilité, le résidu, la dispersion, le facteur et les avertissements.

Cette méthode calibre un modèle de largeur moyenne, pas une caméra universelle. La largeur réelle, l’angle, la perspective, le type de véhicule et la détection peuvent introduire un biais. Une vidéo de référence ne suffit donc pas à revendiquer une précision terrain sur d’autres véhicules ; conserver un jeu de validation indépendant.
