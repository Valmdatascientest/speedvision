# Sprint 3 — suivi temporel

## Livraison

- Tracker Kotlin indépendant du détecteur, Kalman à vitesse constante sur centre et dimensions, association globale Hungarian avec seuil IoU et classe.
- IDs non réutilisés pendant la vie du tracker, confirmation en 3 observations, perte temporaire de 500 ms maximum, expiration et limite de 60 pistes.
- Plaques rattachées aux véhicules confirmés de la même image, sans réutilisation d'une ancienne plaque ni mesure issue d'une prédiction.
- Réinitialisation sur transitions de session, détection, erreur source, changements de géométrie et discontinuités de temps. Protection contre les résultats d'inférence antérieurs à une transition.
- Overlay des IDs/états, pistes perdues grises, compteurs de suivi. Version Android 0.3.0.

Choix inspiré de [SORT, Bewley et al.](https://arxiv.org/abs/1602.00763), implémentation propre sans nouvelle dépendance. Variante : centre/largeur/hauteur, pas de temps en secondes source et seuils temporels, plutôt qu'une reproduction du modèle aire/ratio original. Aucun modèle d'apparence ou OCR ajouté.

## Validation

11 nouveaux tests JVM : confirmation consécutive, croisement et ordre variable, occultation/prédiction/expiration, classes distinctes, reset temporel/résolution/explicite, parent plaque courant, optimum global non glouton, PTS irréguliers, capacité bornée, indépendance des états et rejet des plaques provisoires, Hungarian comparé à une recherche exhaustive sur 100 petites matrices déterministes.

Le test Android d'inférence réelle du bus vérifie aussi la permanence de l'ID et la confirmation sur huit passages. C'est une intégration sur image répétée, pas un benchmark de tracking terrain. Un test Compose vérifie les compteurs et l'absence de vitesse/distance inventées.

Validation locale du 23 septembre 2026 : **23 tests JVM, 3 tests Python et 11 tests Android réussis**, zéro échec et zéro test Android ignoré (`requireModels=true`, Android 15/API 35 ARM64). Formatage, compilation, lint et assemblage des deux APK réussis via `scripts/check.sh`. Les journaux locaux sont `.tools/sprint3-check.log` et `.tools/sprint3-device.log`; rapports détaillés dans les répertoires `build`.

Contrôle visuel sur émulateur : vidéo MP4 construite à partir de la photo publique du bus, cadre et ID confirmé alignés; désactiver la détection efface les compteurs de suivi, puis rejouer crée une nouvelle identité (#2 après #1). Capture locale `.tools/sprint3-screen.png` (non publiée).

La [CI distante du commit `461afea`](https://github.com/Valmdatascientest/speedvision/actions/runs/35907706443) est **verte** : jobs `build` et `device-tests` réussis. Le job optionnel `model-validation` n’a pas été exécuté; les trois tests qui exigent les modèles sont ignorés dans la CI générique sans poids. La suite locale ci-dessus impose les modèles et exécute les onze tests Android.

## Limites et protocole restant

Les tests synthétiques n'établissent pas IDF1/HOTA ou un taux de changements d'identité en conditions réelles. Sans apparence, une superposition prolongée, un mouvement brusque ou une classe instable peut provoquer un changement d'ID. Les covariances Kalman et seuils sont expérimentaux. Les plaques suivent le parent observé, sans identité indépendante ni conservation de leur géométrie entre images.

Pour la validation indépendante : annoter les identités véhicule/plaque image par image sur vidéos autorisées, inclure croisements, entrée/sortie, occlusions courtes/longues et caméra mobile; conserver les PTS et séparer réglage/test. Rapporter changements d'identité, fragments, taux de perte et latence sur téléphone cible. Aucun corpus ni résultat terrain n'est inventé ici.

Les réserves du Sprint 2 (licence de distribution des poids, précision indépendante et matériel cible) restent ouvertes. Aucun Sprint 4, calcul de distance/vitesse, sauvegarde de pixels ou export automatique ajouté.
