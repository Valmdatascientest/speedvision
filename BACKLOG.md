# Backlog Agile

Sprints 0–1 livrés. Sprint 2 implémenté; validation sur téléphone et corpus indépendant encore ouverte. Voir docs/SPRINT_2_REPORT.md.

Definition of Done commune : critères satisfaits, implémentation réelle ou limite déclarée, tests pertinents verts, formatage/lint/build propres, revue de confidentialité, docs mises à jour et aucun résultat de précision inventé. Une story avec validation matérielle non réalisée reste à valider.

## S0-01 · Epic 0 — Fondation

- Description : Étudier faisabilité et définir les contrats.
- Sprint : 0.
- Critères d’acceptation : Sources SDK datées; axe/signe/unités définis; risques et limites explicites.
- Tests : Revue docs contre sources officielles.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S1-01 · Epic 1 — Video ingestion

- Description : Lire une vidéo choisie sans la copier.
- Sprint : 1.
- Critères d’acceptation : Sélecteur système; images réellement décodées; PTS croissants; absence de permissions globales.
- Tests : Vidéo H.264 synthétique et erreur de fichier; contrôle manifeste.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S1-02 · Epic 1 — Video ingestion

- Description : Contrôler le lecteur et diagnostiquer.
- Sprint : 1.
- Critères d’acceptation : START rejoue; STOP annule; EOF visible; fond stoppe; débit aperçu identifié; pas de mesure factice.
- Tests : UI sans fichier; replay; arrêt rapide; cycle de vie sur appareil.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S2-01 · Epic 1 — Video ingestion

- État : Implémenté et testé sur émulateur (permissions, flux, STOP/reprise). Rotation pixels testée; essais téléphones à compléter.

- Description : Brancher CameraX pour développement.
- Sprint : 2.
- Critères d’acceptation : Lifecycle lié; permission au besoin; stratégie latest-frame; transformation documentée.
- Tests : Permission refusée, rotation, mise en fond.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S2-02 · Epic 2 — Vehicle detection

- État : Implémenté, modèle/version/hash audités, inférence et timing CPU testés. Rappel indépendant et comparaison CPU/GPU sur téléphone non validés.

- Description : Détecter les véhicules sur téléphone.
- Sprint : 2.
- Critères d’acceptation : Poids préentraînés identifiés/licenciés; labels véhicules; mesures p50/p95 et rappel.
- Tests : Dataset annoté indépendant, benchmark CPU/GPU.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S2-03 · Epic 3 — License plate detection

- État : Implémenté et testé sur exemple positif/entrée négative. Corpus amont contaminé : validation indépendante des petites plaques et faux positifs encore requise.

- Description : Localiser les plaques dans les véhicules.
- Sprint : 2.
- Critères d’acceptation : Checkpoint spécialisé audité; pas de prétention plaque COCO; seuil/résolution explicites.
- Tests : Petites plaques, faux positifs, occultation, scènes négatives.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S3-01 · Epic 4 — Tracking

- Description : Conserver une identité temporelle.
- Sprint : 3.
- Critères d’acceptation : Tracker distinct du détecteur; prédiction/association; expiration; aucune fenêtre partagée entre pistes.
- Tests : Croisement, entrée/sortie, changement ID, détection intermittente.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S4-01 · Epic 5 — Geometry

- Description : Exploiter les coins et la pose.
- Sprint : 4.
- Critères d’acceptation : Quatre coins ordonnés; PnP métrique; rejet ambiguïté; pas de distance issue de largeur warp arbitraire.
- Tests : Projections synthétiques, yaw/roulis, coins bruités.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S4-02 · Epic 6 — Distance estimation

- Description : Estimer une profondeur avec incertitude.
- Sprint : 4.
- Critères d’acceptation : Calibration/format compatibles; Z positif fini; score et raisons de rejet; unités documentées.
- Tests : Distance connue, mauvais W/fx, distorsion, w trop petit.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S5-01 · Epic 7 — Speed estimation

- Description : Estimer la vitesse relative signée.
- Sprint : 5.
- Critères d’acceptation : Fenêtre multi-frame, temps source, reset, paramètres versionnés; comparaison des filtres.
- Tests : Vitesse constante, accélération, bruit/outliers, gaps, doublons, changement piste.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S6-01 · Epic 8 — Camera motion compensation

- Description : Distinguer rotation et mouvement relatif observable.
- Sprint : 6.
- Critères d’acceptation : Flot fond masque véhicules; qualité et limites; aucune IMU téléphone assimilée à celle des lunettes.
- Tests : Caméra immobile, rotation pure, translation, parallaxe et scène mobile.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S7-01 · Epic 9 — Voice feedback

- Description : Annoncer uniquement les résultats stables.
- Sprint : 7.
- Critères d’acceptation : Voix désactivable; seuil/délai/delta configurables; silence si rejet, périmé ou track changé.
- Tests : Horloge contrôlée, rafales, panne TTS, routage matériel.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S8-01 · Epic 10 — Meta integration

- Description : Recevoir le flux officiel des lunettes.
- Sprint : 8.
- Critères d’acceptation : SDK versionné; autorisations; états et erreurs; décodeur/PTS validés; aucun mock en production.
- Tests : Matériel identifié, déconnexion, reprise, batterie et chauffe.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S4-03 · Epic 11 — Calibration

- Description : Enregistrer/importer une calibration source.
- Sprint : 4.
- Critères d’acceptation : Assistant mire ou paramètres fiables; JSON versionné; fx/fy/cx/cy/distorsion/résolution; profils plaques.
- Tests : Import invalide, changement mode, validation distance hors entraînement.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S9-01 · Epic 12 — Validation

- Description : Mesurer l’erreur sur vérité terrain indépendante.
- Sprint : 9.
- Critères d’acceptation : Référence axiale synchronisée; splits sans fuite; MAE/RMSE/biais/écart-type et couverture/rejets.
- Tests : Calcul métriques vérifié sur exemples connus; essais négatifs inclus.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S9-02 · Epic 13 — Performance

- Description : Optimiser après benchmark de référence.
- Sprint : 9.
- Critères d’acceptation : Latence p50/p95, FPS effectifs, énergie et thermique; comparaison float/FP16/INT8.
- Tests : Session soutenue, variation taille/plaque/éclairage; régression de précision.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S5-02 · Epic 14 — Confidentialité et diagnostic

- Description : Exporter les mesures seulement sur demande.
- Sprint : 5.
- Critères d’acceptation : CSV timestamp/track/Z/v/score/rejet/latence; pas OCR/pixels; export explicite et révocable.
- Tests : Aucun fichier au démarrage; annulation export; schéma/échappement.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.

## S0-02 · Epic 15 — Qualité et CI

- Description : Rendre chaque incrément vérifiable.
- Sprint : 0.
- Critères d’acceptation : Gradle wrapper; CI build/lint/unit/format et tests appareil; aucun secret ou vidéo personnelle suivi.
- Tests : Exécuter check.sh; pipeline distant après connexion du dépôt.
- Definition of Done : DoD commune + preuves des critères/tests ci-dessus dans le rapport de sprint.
