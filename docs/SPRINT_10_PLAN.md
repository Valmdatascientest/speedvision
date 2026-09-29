# Sprint 10 — profondeur live et calibration guidée

Le dépôt passe de la profondeur manuelle isolée à une chaîne live contrôlée. Le premier incrément ajoute le contrat de profondeur par largeur de véhicule (`VehicleSizeDepthEstimator`) avec des profils de dimensions explicites. Ce calcul utilise `Z = fx × largeur_moyenne / largeur_pixels` et doit rester identifié comme un a priori de classe : la variation réelle des véhicules, la perspective, l’angle et la détection dominent l’erreur.

La suite du sprint doit rattacher ce résultat aux pistes confirmées, conserver les PTS source et alimenter la fenêtre Huber existante uniquement après vérification de l’identité, de la calibration, de la qualité et de l’immobilité déclarée de la caméra. Une fenêtre insuffisante reste silencieuse ; aucune vitesse n’est fabriquée pour remplir l’aperçu.

Le parcours de calibration sera simplifié autour de trois choix explicites : importer un profil vérifié, lancer une calibration guidée avec mire, ou utiliser le fallback véhicule avec avertissement. Les paramètres inconnus ne seront pas remplacés silencieusement par des valeurs plausibles.

## Portes de sortie

- tests de domaine du fallback verts et rejets déterministes ;
- test instrumenté d’une piste confirmée avec PTS irréguliers et réinitialisation ;
- comparaison plaque/PnP contre largeur véhicule sur un corpus tenu à part ;
- latence p50/p95, débit soutenu, température et énergie mesurés sur le Z Flip7 ;
- aucune vitesse live annoncée si la caméra, l’identité ou la géométrie sont invalides.
