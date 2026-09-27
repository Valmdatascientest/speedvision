# Sprint 9 — évaluation reproductible et benchmark Android

Outil `scripts/evaluate_speed.py` ajouté pour les exports réels du laboratoire. Appariement strict à l'instant de référence de la fenêtre, identité complète, MAE/RMSE/biais/écart-type population, couverture, rejets et prédictions sans référence. JSON avec empreintes des entrées, sans écrasement de fichier. Absence de comparaison représentée par `null`.

Six tests dédiés vérifient les erreurs signées connues, la couverture incomplète, les mauvais instants/calibrations, les rejets, doublons, valeurs non finies, unités incohérentes, percentiles et exécution CLI sans écrasement. Ils sont intégrés à la découverte Python existante de `scripts/check.sh`.

[Protocole et commande](../testing/speed/EVALUATION.md). Les temps exportés concernent uniquement la régression du laboratoire. Aucun benchmark caméra→résultat ou énergétique n'est revendiqué.

Restent ouverts : acquisition indépendante synchronisée, validation du matériel Meta, baseline soutenue sur Z Flip7, énergie/thermique, puis comparaison float/FP16/INT8 avec contrôle de précision et couverture sur un split réservé. Aucun poids n'a été quantifié sans données de calibration admissibles. Cette tranche prépare la mesure ; elle ne démontre pas une précision terrain.

Validation locale de l’outil : six tests dédiés réussis, plus le test existant d’assemblage CSV. Aucune donnée de terrain utilisée.

## Deuxième tranche — baseline Android et comparaison

`DetectionBenchmarkTest` ajouté uniquement aux sources instrumentées. Activation explicite obligatoire, vrais poids locaux, fixture bus répété, empreintes modèle/image et contexte CPU/version/API/ABI. Premier appel à froid et warmup séparés, échantillons monotones avec durées véhicules/plaques/appel complet, ROI omises et états thermiques système. Capture dans les fichiers privés de l'application ; refus d'écrasement, aucun export automatique en production.

`analyze_performance.py` vérifie la cohérence de la capture et calcule p50/p95 nearest-rank, débit sur durée écoulée, compte des états thermiques et médianes début/fin. Quatre tests couvrent calcul connu, pauses, percentiles, état absent, mesures invalides et capture trop courte. [Exécution sur Z Flip7 et limites](../testing/performance/README.md).

`compare_speed_reports.py` exige le même SHA-256 de référence et des comptes cohérents. Il rapporte les deltas d'erreur et de couverture sans choisir automatiquement une variante, ni prétendre comparer les mêmes observations acceptées. Quatre tests supplémentaires couvrent perte de couverture, références différentes, absence de comparaisons et métriques invalides.

## Validation de cette tranche

- `scripts/check.sh` réussi : format, 54 tests JVM, tests Python existants et performance, benchmark synthétique, lint, assemblage APK/test et audit du manifeste sans SDK.
- Après ajout de la comparaison : 11 tests Python vitesse et 4 tests performance réussis (19 tests Python au total avec détection/calibration).
- Benchmark instrumenté opt-in réussi sur émulateur API 35 ARM64 : premier appel, un warmup, cinq appels mesurés ; récupération du JSON et analyse réellement exécutées. Aucun modèle de téléphone physique testé à ce stade ; essais ultérieurs ci-dessous. Cette capture courte constitue uniquement un test de l'outillage ; elle ne représente pas un benchmark soutenu.
- Compilation du nouveau test dans le profil Meta réussie. Exécution sans opt-in vérifiée : aucun nouveau benchmark. CI du commit `cce93ad` : [succès](https://github.com/Valmdatascientest/speedvision/actions/runs/36223878860), quatre jobs verts.

Aucune optimisation FP16/INT8 activée. La livraison logicielle du Sprint 9 est complète : évaluation reproductible, comparaison de rapports, benchmark opt-in et réduction des allocations répétées sont documentés et testés. Les portes scientifiques et matérielles restent explicitement séparées : sessions soutenues sur Z Flip7, corpus indépendant, énergie, thermique et contrôle de précision après optimisation. Le script ne mesure pas l'énergie et son débit n'est pas un FPS caméra.


## Troisième tranche — réutilisation du prétraitement

Le détecteur conserve son bitmap 640×640, ses tableaux ARGB/RGB et son objet Paint pendant sa session, au lieu de les allouer pour chaque image ou ROI. Le fond de letterbox et tous les pixels sont réécrits à chaque appel. Le tenseur ONNX et les sorties restent propres à chaque inférence. Le mutex de DetectionEngine continue de sérialiser les accès ; aucun tampon n'est partagé entre détecteurs. La fermeture recycle le bitmap et refuse les appels ultérieurs, même après une seconde fermeture.

Compromis mémoire : environ 26,6 Mio de bitmap/tableaux par détecteur sont conservés pendant sa vie (hors modèle, tenseurs et sorties), soit 53,1 Mio pour les deux détecteurs après utilisation. Ce sont des tailles calculées, pas une mesure du pic RSS. L'optimisation réduit ces allocations répétées, sans promettre une amélioration de latence ou d'autonomie sur téléphone.

Validation : `scripts/check.sh` réussi et cinq tests des vrais détecteurs réussis sur émulateur API 35 ARM64. Le nouveau test alterne trois fois un bus positif et une image noire panoramique, vérifie les détections identiques au retour au bus et le refus d'inférence après fermeture.

Le Z Flip7 a été autorisé via ADB et identifié comme SM-F761B sous Android 16. La capture de référence, restée dans l'application après une déconnexion, a été récupérée à la reconnexion. Les cinq tests des vrais détecteurs passent aussi sur ce téléphone. Comparaison de captures CPU sur le même fixture, mêmes empreintes de modèles, mêmes paramètres (100 appels, cinq warmups, deux threads intra/ un inter), sous alimentation USB.

Les quatre captures donnent : première référence p50/p95 **519/529 ms**, optimisation **321/431 ms** ; seconde référence **367/380 ms**, optimisation **512/519 ms**. Les états thermiques échantillonnés sont restés `0` et les modèles, fixture, ABI et paramètres sont identiques. La dispersion entre sessions dépasse l’effet observé ; aucune amélioration de latence ne peut donc être revendiquée. Le résultat fiable de cette tranche est la correction fonctionnelle et la réduction des allocations répétées, pas un gain de performance établi. Une comparaison contrôlée alternée et débranchée reste nécessaire.
