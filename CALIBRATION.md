# Calibration et profondeur — Sprint 4

Le Sprint 4 permet une **mesure manuelle sur image arrêtée** avec quatre coins physiques sélectionnés et une calibration connue. Il ne mesure pas encore de distance en direct et n'utilise pas les coins des boîtes YOLO comme coins de plaque. Une calibration ne peut pas être obtenue à partir d'une plaque unique de taille supposée.

## Dans l'application

1. Lire une vidéo ou lancer CameraX pour obtenir une image, puis ouvrir **Calibration / distance sur image arrêtée**. Le flux s'arrête; l'image et son PTS sont conservés uniquement en mémoire.
2. Saisir fx/fy/cx/cy **natifs avant crop et rotation**, les coefficients Brown-Conrady `k1,k2,p1,p2,k3`, la provenance (caméra, mode, date) et la RMS d'un jeu de vues de validation distinct (≤ 2 px). Utiliser un point décimal. Aucun paramètre focal par défaut n'est inventé.
3. Choisir les dimensions mesurées de la plaque, ou le raccourci 520 × 110 mm après vérification. Ce format n'est ni universel ni reconnu automatiquement.
4. Confirmer explicitement la source, le mode optique fixe, les paramètres et dimensions, puis appliquer. Zoom/stabilisation variables, changement de lentille ou mode inconnu rendent cette calibration inadaptée : ne pas confirmer.
5. Avec zoom et réglage du centre, sélectionner les coins réels dans l'ordre physique haut gauche, haut droit, bas droit, bas gauche. La plaque entière doit être nette et plane. Effacer pour recommencer. Les points restent attachés à cette seule image.
6. Estimer la **profondeur axiale Z**, en mètres. L'inclinaison et la reprojection sont des diagnostics, pas une garantie de précision. L'incertitude métrique reste explicitement non quantifiée. Aucune vitesse n'est produite dans cet assistant. Une observation peut être exportée pour le [laboratoire de séries](testing/speed/README.md), avec identité annotée, qualité évaluée et caméra fixe déclarée.

Un profil n'est conservé sur disque que via **Exporter le profil JSON**, au lieu choisi par l'utilisateur. Aucun pixel n'est exporté. L'import exige le même identifiant de source et la même géométrie puis une nouvelle confirmation. Fermer l'assistant perd les modifications non exportées. Exporter la géométrie pour la mire produit seulement le descripteur source, pas une calibration.

## Obtenir K sur mire, hors application

Acquérir des images natives d'un damier rigide aux carrés mesurés, dans le mode réellement utilisé. Couvrir le champ, les inclinaisons et plusieurs distances; éviter le flou et les séries uniquement frontales. L'application n'enregistre pas de mire automatiquement. Les clichés d'une autre application peuvent avoir un mode/crop/stabilisation différent : ils ne sont pas admissibles sans vérification.

Installer les outils dans un environnement local dédié :

```sh
python3 -m venv .tools/calibration-env
.tools/calibration-env/bin/pip install -r scripts/requirements-calibration.txt
```

Exporter `speedvision-source.json` depuis l'assistant. Copier `testing/calibration/manifest.template.json` dans le dossier des mires et renseigner : nombres de coins intérieurs, taille mesurée d'un carré en mètres, provenance, dimensions physiques de plaque, au moins 12 vues `training` et 4 vues `validation`. Les chemins sont relatifs au manifeste. Les listes vides du modèle ne constituent pas un dataset.

```sh
.tools/calibration-env/bin/python scripts/calibrate_camera.py \
  --manifest /chemin/mires/manifest.json \
  --source /chemin/speedvision-source.json \
  --output /chemin/calibration.json
```

Le script détecte réellement le damier avec OpenCV 4.12, résout K et les cinq coefficients, estime seulement la pose des vues réservées avec K figé et rapporte leur reprojection par vue. Il refuse doublons de fichiers entre/tous les splits, résolution différente, damier absent, paramètres non finis et erreur de validation > 2 px. Il n'écrase pas un fichier existant. Il ne télécharge ni n'envoie d'image. Le contrôle des hashes ne détecte pas des quasi-doublons : la séparation et la diversité des acquisitions restent à assurer.

Une RMS faible n'établit pas l'exactitude métrique : vérifier ensuite des objets plans aux dimensions et distances axiales mesurées indépendamment, hors jeu de réglage. Rapporter biais et dispersion selon profondeur, angle et résolution. Aucune calibration matérielle ni précision terrain n'est fournie avec le dépôt.

## Schéma et compatibilité

JSON `schemaVersion: 1`, `model: brown5-native`, `binding` (sourceId, nativeWidth/nativeHeight, cropLeft/cropTop, width/height du crop, rotation), fx/fy/cx/cy, distortion[5], provenance, validationRmsPx, plate{name,widthMeters,heightMeters}. Import borné à 32 Kio; schéma et valeurs invalides refusés. Les paramètres caméra sont en pixels natifs, dimensions plaque en mètres.

Le sourceId vidéo est un hash de l'URI locale, pas un hash du contenu; remplacer le contenu d'un fichier exige de revérifier son mode. CameraX identifie ici la caméra arrière et le modèle de téléphone, pas la lentille physique active. L'application ne certifie pas automatiquement le mode optique ni la provenance : l'attestation est indispensable. Un changement détecté de résolution, crop ou rotation empêche la réutilisation silencieuse du profil.

L'estimateur inverse la rotation autour des centres de pixels puis ajoute l'origine du crop pour ramener les coins dans le repère natif de K. Le redimensionnement d'affichage/zoom n'intervient pas dans la mesure. IPPE examine ses solutions, exige une profondeur positive de tous les coins, RMS ≤ 2 px, inclinaison ≤ 65°, largeur ≥ 32 px et hauteur ≥ 8 px. Deux solutions de reprojection proche (écart ≤ 0,5 px) aux profondeurs divergentes de plus de 2 % ou inclinaisons divergentes de plus de 5° sont rejetées. Ces seuils expérimentaux doivent être évalués sur un corpus indépendant.

Références : [calibration OpenCV 4.12](https://docs.opencv.org/4.12.0/dc/dbb/tutorial_py_calibration.html), [pose PnP et solutions multiples](https://docs.opencv.org/4.x/d5/d1f/calib3d_solvePnP.html). Pas de largeur d'image rectifiée arbitraire dans le calcul métrique; pas d'OCR.
