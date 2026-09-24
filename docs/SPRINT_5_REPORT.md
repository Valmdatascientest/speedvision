# Sprint 5 — vitesse relative sur séries de profondeur

## Livré

- `SpeedEstimator` Kotlin pur : fenêtre bornée par durée et effectif, temps source, vitesse signée `−dZ/dt`, conversion km/h et instant central de référence.
- Régression Huber pondérée par qualité, initialisation robuste par médiane des pentes, contrôles de résidus/inliers/non-linéarité et score de qualité non probabiliste.
- Invalidation sur identité/calibration/source, temps dupliqué/inversé, gap, données périmées, perte de piste, géométrie invalide et caméra non fixe. Les rejets ne réutilisent pas une ancienne vitesse.
- Laboratoire Android d'import/relecture CSV, exemple synthétique explicitement choisi, inspection des observations/rejets, export explicite des entrées/résultats et effacement mémoire.
- Export d'une profondeur manuelle avec PTS réel, ID annoté et hash de calibration; outil d'assemblage explicite sans interpolation.
- Comparaison reproductible OLS, Kalman, polynôme local quadratique, consensus de paires et Huber. Version Android 0.5.0.

## Pourquoi un laboratoire séparé

Le Sprint 4 produit une profondeur manuelle isolée. Il manque encore des coins fiables suivis automatiquement pour alimenter une série métrique en direct. L'aperçu conserve donc « vitesse : — ». Le laboratoire calcule réellement sur une série fournie; aucune valeur fictive n'est attachée à une vidéo utilisateur. Le seul exemple intégré est nommé synthétique et n'est pas chargé automatiquement.

Une caméra fixe doit être déclarée et vérifiée dans le protocole d'acquisition. L'import ne peut pas vérifier cette affirmation. Le mouvement caméra non maîtrisé est refusé; la compensation reste au Sprint 6. L'incertitude métrique est non quantifiée et aucun score n'est présenté comme une probabilité de précision. Aucune vitesse absolue routière, voix ou intégration Meta ajoutée.

## Comparaison synthétique

49 fenêtres par scénario, seed 2026, 10 Hz. MAE en km/h, au temps de référence déclaré par chaque méthode. Les baselines sans contrôles acceptent toutes leurs fenêtres; seules les lignes de production incluent les rejets.

| Scénario | Huber brut | OLS | Kalman | Quadratique local | Consensus de paires | Huber avec contrôles / couverture |
|---|---:|---:|---:|---:|---:|---:|
| Bruit borné ±0,06 m | 0,101 | 0,098 | 0,110 | 0,098 | 0,098 | 0,101 / 100 % |
| Bruit + valeurs aberrantes de 3 m | 0,100 | 1,197 | 1,891 | 1,197 | 0,093 | 0,098 / 89,8 % |
| Accélération 1 m/s² | 0,110 | 0,098 | 1,823 | 0,098 | 0,100 | 0,110 / 98,0 % |
| Accélération 12 m/s² | 0,115 | 0,098 | 21,700 | 0,098 | 9,924 | aucune acceptation / 0 % |

Ces chiffres ne démontrent pas une supériorité générale de Huber : le consensus fait légèrement mieux sur les valeurs aberrantes de cette trace, OLS sur le bruit seul. Huber est retenu comme compromis à pondérations continues avec contrôles explicites. Le résultat linéaire représente la pente locale centrale, pas la vitesse instantanée en fin de fenêtre; les contrôles rejettent les courbures fortes même si la pente moyenne semble exacte. Les baselines et paramètres sont expérimentaux, non optimisés sur un corpus terrain.

Source reproductible : `domain/build/reports/speed/benchmark.csv`, généré par `:domain:benchmarkSpeed` et inclus dans les rapports CI. Voir [protocole de séries](../testing/speed/README.md).

## Validation

Validation locale du 24 septembre 2026 : **39 tests JVM, 5 tests Python et 18 tests Android réussis**, zéro échec et zéro test Android ignoré (`requireModels=true`, émulateur Android 15/API 35 ARM64). Les 12 nouveaux tests JVM couvrent signe/unité, bruit/outliers, accélération, PTS irréguliers et de grande amplitude, gaps/doublons/ordre, identité, géométrie/mouvement, fraîcheur, bornes mémoire, CSV et baselines.

Le test Compose du laboratoire charge explicitement l’exemple synthétique, vérifie +18 km/h, ouvre puis annule le sélecteur d’export et efface la série sans conserver de vitesse. Les tests PnP, CameraX, détection réelle et vidéo restent verts.

Formatage, compilation, lint, benchmark et assemblage des APK réussis. Preuves : `.tools/sprint5-check-final.log`, `.tools/sprint5-device.log`, rapports XML et CSV sous `build`. La [CI distante du commit `e682538`](https://github.com/Valmdatascientest/speedvision/actions/runs/36012253018) est **verte** : jobs `build` et `device-tests` réussis. Le job optionnel `model-validation` n’a pas été exécuté; la CI générique sans poids ignore les trois tests ONNX qui les exigent, tandis que la suite locale ci-dessus les exécute.

Contrôle visuel sur émulateur : exemple explicitement synthétique, +18 km/h en rapprochement, temps source 2,000 s et référence centrale 1,400 s affichés séparément, compteurs de rejets et incertitude non quantifiée visibles. Capture locale `.tools/sprint5-lab.png`, non publiée.

## Réserves

Pas de séries métriques de référence issues de vidéos réelles indépendantes, pas d'incertitude calibrée, pas de vitesse live ni de voix. Validation sur téléphone, énergie/thermique, détection des coins, précision de profondeur et stabilité du tracking terrain restent ouvertes. Les déclarations CSV de qualité/géométrie/identité/caméra ne sont pas certifiées automatiquement. Les réserves de distribution des modèles AGPL restent inchangées. Le Sprint 6 n'a pas été commencé.
