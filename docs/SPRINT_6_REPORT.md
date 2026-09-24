# Sprint 6 — alignement visuel du fond

## Livré

Version Android 0.6.0. Le flux réel vidéo/CameraX transmet chaque image traitée et ses boîtes véhicules à `BackgroundMotionEstimator`. Le diagnostic présente le statut, le nombre de correspondances/inliers, la couverture spatiale, le résidu p95 et le temps de calcul du flot, séparé de la latence des réseaux.

Le moteur OpenCV suit au plus 250 coins Shi–Tomasi par Lucas–Kanade pyramidal aller/retour. Les deux images excluent les boîtes véhicules avec une marge de 20 pixels à la résolution de travail. Une homographie RANSAC aligne le fond; `predict` et `residual` permettent une compensation **dans le plan image**, en pixels redressés. Aucun pixel ni diagnostic n'est enregistré automatiquement.

## Contrôles

- Grand côté réduit à 640 pixels au maximum, ratios horizontal/vertical exacts lors du retour au repère image.
- Masque absent ou détections saturées (30 boîtes) : rejet et suppression de l'historique.
- Erreur de flot photométrique < 30, retour à ≤ 1 pixel; coordonnées dans le masque courant.
- Au moins 30 correspondances et 30 inliers, ratio ≥ 85 %, présence dans ≥ 8 des 16 cellules du cadre précédent.
- RANSAC : seuil 1,5 pixel, 1 000 itérations au plus, probabilité paramètre 0,995. Ce paramètre n'est pas une confiance métrique.
- Résidu p95 sur toutes les correspondances filtrées ≤ 1,5 pixel réduit; déplacement des quatre coins du cadre < 30 % de sa diagonale.
- PTS strictement croissants, intervalle ≤ 300 ms. Changement de lecture, activation de détection, source, dimensions/crop/rotation : nouvelle initialisation.
- STOP et erreur source effacent immédiatement le diagnostic. Une génération empêche la publication des calculs devenus obsolètes. Les matrices de travail sont libérées en `finally`; une image grise réduite et son masque restent au plus en mémoire pour la prochaine paire, jusqu'à remplacement ou destruction du collecteur. Aucune matrice native partagée avec l'interface.

Les seuils sont des paramètres expérimentaux de `background-lk-homography-v1`, pas des garanties terrain.

## Observabilité et capteurs

Une caméra en rotation peut produire une homographie; un plan translaté peut en produire une aussi. La scène entière peut bouger de manière cohérente. L'acceptation signifie uniquement que l'alignement visuel satisfait ces contrôles. Elle ne sépare pas rotation et translation métrique, ne certifie jamais `cameraFixed` et ne déverrouille pas `SpeedEstimator`. Le résidu d'un centre véhicule après alignement n'est pas une vitesse physique.

| Source | Temps utilisé | Capteur associé dans cet incrément |
|---|---|---|
| Fichier | PTS de décodage | Aucun IMU embarqué disponible dans le contrat |
| CameraX téléphone | Horodatage des images | Aucun échantillon IMU collecté/synchronisé |
| Lunettes Meta | Pas d'adaptateur intégré | Aucune disponibilité ou synchronisation revendiquée |

L'IMU du téléphone n'est jamais assimilée à celle des lunettes. Aucun calcul ne combine temps source et horloge de calcul. Les boîtes masquent seulement les véhicules détectés : piétons, végétation, véhicules manqués, reflets et autres objets mobiles peuvent rester. Parallaxe, flou, distorsion, rolling shutter et variations lumineuses peuvent entraîner un rejet ou une homographie trompeuse. Les portes métriques restent donc fermées même après acceptation visuelle.

## Validation

Validation locale du 24 septembre 2026 : **39 tests JVM, 5 tests Python et 25 tests Android réussis**, zéro échec et zéro test ignoré (`requireModels=true`, Android 15/API 35 ARM64). Formatage, lint, benchmark de vitesse et assemblage des APK réussis. Logs locaux `.tools/sprint6-check.log` et `.tools/sprint6-device.log`; rapports XML dans les répertoires `build`.

[CI distante du commit f80a8c1](https://github.com/Valmdatascientest/speedvision/actions/runs/36053668409) **verte** : jobs `build` et `device-tests` réussis. Le job optionnel `model-validation` est ignoré; la CI générique sans poids ignore les trois tests ONNX qui les exigent. La suite locale ci-dessus les a tous exécutés.

Contrôle visuel sur émulateur : la vidéo locale de smoke test (image publique répétée, aucune vérité terrain) affiche « Alignement visuel uniquement », 250/250 correspondances, 12/16 cellules et 13 ms sur sa dernière paire. Cette durée ponctuelle n’est pas un benchmark téléphone. La vitesse reste « — » et les limites rotation/translation/IMU sont lisibles. Capture locale `.tools/sprint6-diagnostic.png`, non publiée. Après passage en arrière-plan puis retour, le diagnostic est bien effacé.

Sept tests Android réels, sans simulation d'OpenCV : fond immobile/translation, rotation autour de l'axe optique et retour à résolution native, véhicule déplacé avec masques aux deux positions, deux plans en mouvement opposé, manque de texture/masque/couverture, rupture temporelle/source/fermeture, scène mobile cohérente non certifiée immobile. Images synthétiques déterministes; aucune précision terrain déduite.

[Protocole reproductible et essais matériels à compléter](../testing/motion/README.md).

## Réserves et revue

L'incrément logiciel livre le flot masqué et le rejet de toute interprétation métrique non observable. La séparation physique rotation/translation, une compensation métrique, les essais de mouvements de tête sur matériel, la synchronisation de capteurs et la validation de scènes indépendantes restent ouverts. L'app ne propose toujours aucune vitesse live. Le Sprint 7 commencera par la politique de stabilité/anti-spam; le routage audio matériel ne peut être validé sur cet émulateur.

## Sources primaires

- [OpenCV 4.12 — Optical Flow](https://docs.opencv.org/4.12.0/d4/dee/tutorial_optical_flow.html), consulté le 24 septembre 2026 : coins et flot pyramidal.
- [OpenCV 4.12 — Homography](https://docs.opencv.org/4.12.0/d9/dab/tutorial_homography.html), consulté le 24 septembre 2026 : modèles planaires et rotation de caméra; une homographie seule ne fournit pas une translation métrique générale.
