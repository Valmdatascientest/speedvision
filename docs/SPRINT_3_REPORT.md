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

Résultats finaux des contrôles locaux et de la CI à consigner après exécution.

## Limites et protocole restant

Les tests synthétiques n'établissent pas IDF1/HOTA ou un taux de changements d'identité en conditions réelles. Sans apparence, une superposition prolongée, un mouvement brusque ou une classe instable peut provoquer un changement d'ID. Les covariances Kalman et seuils sont expérimentaux. Les plaques suivent le parent observé, sans identité indépendante ni conservation de leur géométrie entre images.

Pour la validation indépendante : annoter les identités véhicule/plaque image par image sur vidéos autorisées, inclure croisements, entrée/sortie, occlusions courtes/longues et caméra mobile; conserver les PTS et séparer réglage/test. Rapporter changements d'identité, fragments, taux de perte et latence sur téléphone cible. Aucun corpus ni résultat terrain n'est inventé ici.

Les réserves du Sprint 2 (licence de distribution des poids, précision indépendante et matériel cible) restent ouvertes. Aucun Sprint 4, calcul de distance/vitesse, sauvegarde de pixels ou export automatique ajouté.
