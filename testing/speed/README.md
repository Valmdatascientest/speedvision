# Séries de profondeur et laboratoire de vitesse

Le laboratoire Android lit des profondeurs axiales déjà mesurées/validées, **pas des largeurs de boîtes**. Il ne déduit ni la validité de la calibration, ni l'identité, ni l'immobilité de la caméra du CSV : ces déclarations doivent être justifiées par le protocole d'acquisition. Une caméra mobile ou un mouvement inconnu (`camera_fixed=false`) provoque un rejet; aucune compensation n'est encore implémentée.

## Import

UTF-8, virgule, point décimal, booléens exactement `true`/`false`, en-tête exact :

```csv
sequence_id,track_id,calibration_id,timestamp_us,depth_m,quality,geometry_valid,camera_fixed,track_observed
```

- `sequence_id` : source et session, à changer entre prises ou modes. `track_id` : entier ≥ 0 du véhicule annoté/suivi. Une seule identité active par estimateur; alterner les pistes réinitialise les fenêtres, sans mélanger les historiques.
- `calibration_id` : identifiant de la calibration complète, dimensions physiques comprises. L'assistant utilise le SHA-256 de son JSON. Modifier la calibration réinitialise la fenêtre.
- `timestamp_us` : PTS original en microsecondes. Ne pas utiliser le temps de saisie ou de fin d'inférence. Aucun tri, interpolation ni correction silencieuse dans le laboratoire.
- `depth_m` : profondeur axiale positive issue d'une géométrie validée. Pas une distance euclidienne, un pixel ou une prédiction de piste.
- `quality` : qualité géométrique évaluée selon un protocole explicite, entre 0 et 1; ce n'est pas une probabilité d'exactitude. Une mesure sans évaluation ne doit pas recevoir arbitrairement 1.
- `geometry_valid`, `camera_fixed`, `track_observed` : contrôles obligatoires pour accepter une observation. `false` efface la fenêtre et fournit un motif de rejet.

Au plus 5 000 observations et 512 Kio. Identifiants de 1 à 128 caractères, début alphanumérique, sans caractères de contrôle; cela évite notamment les identifiants interprétables comme formules dans un tableur. Virgules et guillemets sont échappés. Les nombres non finis, colonnes manquantes et schémas inconnus font refuser l'import. Les événements temporels et valeurs physiques invalides donnent des rejets par ligne, jamais une ancienne vitesse conservée.

`observations.template.csv` est volontairement vide et ne constitue pas un dataset. `synthetic-approach.csv` représente explicitement une profondeur qui décroît de 5 m/s (18 km/h) sans bruit. Il sert uniquement de test fonctionnel, pas de validation terrain.

## Depuis l'assistant de profondeur

Après une mesure PnP acceptée, renseigner un ID de véhicule **annoté manuellement**, une qualité justifiée et confirmer l'immobilité de la caméra pendant toute la prise. **Exporter cette observation CSV** écrit une ligne avec le vrai PTS de l'image arrêtée. Aucun lien automatique avec l'ID du tracker n'est supposé.

Assembler plusieurs exports du même véhicule, de la même prise et de la même calibration :

```sh
python3 scripts/merge_depth_observations.py --output serie.csv observation-1.csv observation-2.csv observation-3.csv
```

Cet outil trie explicitement par PTS, refuse doublons/changements d'identité et conserve les gaps. Il n'invente pas d'observations entre images. Il n'écrase pas une sortie existante. Il faut au moins 8 observations cohérentes sur ≥ 0,7 s, avec gaps ≤ 0,3 s; quelques arrêts manuels espacés ne suffisent donc pas. L'annotation externe image par image d'une vidéo à caméra fixe est actuellement la voie pratique pour constituer une série dense. Aucun jeu vidéo réel annoté n'est fourni.

## Résultats et export

Importer dans **Laboratoire de vitesse / CSV**, parcourir les observations avec le curseur, puis exporter seulement sur demande. Le résultat sélectionné est une donnée historique au PTS indiqué; il n'est pas présenté comme une mesure live. Positif = rapprochement axial, négatif = éloignement. L'estimation se rapporte au centre de sa fenêtre, avec un retard typique de 0,6 s, sans extrapolation vers maintenant.

Le CSV de résultats inclut version d'algorithme, identité, PTS, profondeur d'entrée, acceptation/rejet, motif, m/s, km/h, qualité, effectifs, instant de référence, durée, résidu et temps de calcul. Les champs vitesse/qualité restent vides en cas de rejet. Ce fichier de résultats n'est pas un fichier d'observations réimportable; utiliser **Exporter les observations CSV** pour cela.

Aucun export au démarrage ni en continu, aucun pixel/OCR. Annuler le sélecteur ne lance pas d'écriture. Effacer ou fermer supprime la série en mémoire; les fichiers déjà exportés restent à l'emplacement choisi et peuvent y être supprimés. Une erreur fournisseur peut laisser un fichier incomplet : ne pas le considérer comme un export réussi.

## Tests et benchmark

`./scripts/check.sh` exécute les tests puis `:domain:benchmarkSpeed`, qui écrit `domain/build/reports/speed/benchmark.csv`. Les traces synthétiques utilisent un seed fixe et des profondeurs analytiques. OLS, Kalman position/vitesse, polynôme local quadratique sur timestamps réels (analogue de Savitzky–Golay adapté à temps irréguliers), consensus exhaustif de paires type RANSAC et Huber reçoivent les mêmes fenêtres de 13 points sur 1,2 s. Les baselines résident uniquement dans les sources de test.

Les erreurs sont calculées à l'instant déclaré par chaque méthode : centre de fenêtre pour les régressions, dernier point pour Kalman. Cette différence de délai doit être conservée dans toute comparaison. La ligne `HUBER_GATED` ajoute les rejets du moteur de production; la MAE/RMSE porte uniquement sur les cas acceptés et doit être lue avec la couverture. Le warmup initial est exclu des 49 fenêtres comparées. Aucun objectif de précision terrain n'est considéré atteint par ces chiffres.
