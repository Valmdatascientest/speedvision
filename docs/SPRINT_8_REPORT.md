# Sprint 8 — intégration caméra Meta

## Incrément

SDK officiel DAT 1.0.0 figé. Build Meta opt-in (`-PmetaEnabled=true`), Android 10 minimum; build local sans SDK conservé sur Android 9. Version application 0.8.0. Panneau de connexion/inscription/consentement caméra, sélection spécifique des lunettes, session réelle et flux décodé raccordé à l'aperçu, détection et tracking existants.

Buffers SDK copiés, taille/format stricts, conversion I420, vrais PTS, traitement borné et arrêt sur interruption. Aucune reconnexion automatique, aucune image fictive, aucun accès microphone. Diagnostics batterie/thermique fournis par le SDK. [Procédure, API et limites](../META.md).

L'étude du Sprint 0 a été revalidée : la version 1.0.0 publiée le 24 septembre remplace 0.9.0, et les états appareil sont maintenant sur `Device`/`DeviceSession.deviceInfo`. Le nouveau module de mouvement reste expérimental et n'est pas intégré à cet incrément. Les anciennes conclusions d'absence d'API motion ne doivent donc plus être lues comme un état actuel du SDK.

## Intégration et confidentialité

Compilation conditionnelle de deux implémentations de `MetaSupport`, sans dépendance DAT dans le build local. Les identifiants de configuration sont lus dans `local.properties`, jamais suivis. Le build Meta ajoute réseau/Bluetooth et un service d'inscription SDK exporté; il désactive analytics/crash reporting optionnels. `audit_android_manifest.py` vérifie le manifeste réellement fusionné, le minimum SDK et l'absence de microphone/stockage global/localisation. Le contrôle ne lit ni n'affiche les tokens.

OpenCV et fbjni embarquent chacun `libc++_shared.so`. Le build Meta sélectionne une seule copie. Vérification locale ARM64 : les deux runtimes exposent les mêmes 2 336 symboles définis; les tests OpenCV et SDK dans le même APK complètent ce contrôle. Cela ne remplace pas une validation de toutes les fonctions natives sur téléphone.

La CI conserve le build local, ajoute un build/lint Meta et exécute les tests appareil avec/sans SDK. Aucun APK Meta n'est publié en artefact. Les poids locaux restent exclus du dépôt.

## Validation exécutée

Profil Meta : compilation, lint, 54 tests JVM et audit du manifeste fusionné réussis. Sur émulateur API 35 ARM64, 30 tests Android réussis (modèles réels provisionnés), un test vocal ignoré faute de voix française hors ligne. Le test d’export a d’abord échoué à cause d’une fenêtre « System UI ne répond pas » dans l’émulateur ; après redémarrage complet, le test d’origine passe sans modification.

Profil sans SDK : `scripts/check.sh` réussi (format, 54 tests JVM, 11 tests Python dont ceux du Sprint 9, benchmark synthétique, lint, APK et audit du manifeste). Sur le même émulateur : 27 tests Android réussis, un test vocal ignoré. CI distante déclenchée sur le commit `4765947` : [exécution 36212743854](https://github.com/Valmdatascientest/speedvision/actions/runs/36212743854). Son résultat n’est pas encore connu ; les résultats ci-dessus sont locaux.

## Matériel et réserves

Cible fournie : Samsung Z Flip7, Wayfarer 0015, version déclarée 68449720064700100. Aucun téléphone connecté via ADB pendant le développement; aucune transmission depuis ces lunettes n'a été observée ici. Identifiant d'application WDC non fourni, mode/compte et compatibilité à confirmer localement.

Restent ouverts : appairage/consentements sur matériel, format/couleur/orientation du flux 1.0 sur ces lunettes, validation PTS, déconnexion/reprise réelle, thermique/énergie et TTS simultané. Le test du convertisseur est synthétique, pas une validation du décodeur matériel SDK. Le SDK non compressé documente YUV; le format précis I420 est fondé sur l'exemple officiel antérieur et contrôlé strictement, sans heuristique de format. Voir [protocole](../testing/meta/README.md).

Le Sprint 8 ne peut pas être déclaré validé sur matériel. Les réserves des Sprints 4–7 demeurent : coins/profondeur automatiques, compensation métrique et annonces live ne sont pas activés. Le Sprint 9 est engagé avec un outil d’évaluation indépendante ; ses essais terrain dépendent toujours du matériel et du corpus.
