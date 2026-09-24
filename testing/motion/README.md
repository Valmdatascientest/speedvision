# Validation du mouvement du fond

## Régression automatisée

Exécuter `BackgroundMotionTest` sur un appareil Android ou un émulateur :

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=fr.speedvision.BackgroundMotionTest
```

OpenCV est réellement exécuté. Les textures sont générées avec une seed fixe, les déplacements par `android.graphics.Matrix`/Canvas (indépendamment de l'estimateur). Les tests vérifient une correspondance connue dans le repère natif, le rejet de deux plans en mouvement opposé et l'invalidation des historiques. Ils n'utilisent ni modèle ni vidéo personnelle. La suite complète conserve en plus les tests de détection réelle quand les poids sont provisionnés.

## Essais matériels restant à réaliser

Utiliser une caméra et un mode optique identifiés, une scène autorisée et des annotations indépendantes. Conserver séparément les séquences d'ajustement des paramètres et celles de validation. Ne pas transformer une absence de mouvement apparent en vérité terrain d'immobilité.

| Scène | Référence indépendante à acquérir | Contrôle |
|---|---|---|
| Caméra et fond fixes, véhicule mobile | Support réellement immobilisé, boîtes annotées | Alignement du fond, véhicule exclu aux deux instants |
| Rotation caméra seule | Montage autour du centre optique, angles connus | Erreur de projection de points du fond; aucune translation métrique annoncée |
| Translation latérale/axiale | Rail/positions mesurées, plusieurs profondeurs connues | Ne pas certifier l'immobilité même si un plan domine |
| Mouvement de tête | Séquence contrôlée avec référence du même dispositif | Taux de rejet, rolling shutter, flou; aucun gyro téléphone assimilé aux lunettes |
| Parallaxe | Plans proches/lointains et points annotés par plan | Couverture des rejets et faux alignements acceptés |
| Scène mobile dominante | Écran/panneau déplacé devant une caméra fixe | Acceptation visuelle possible, interprétation métrique toujours indisponible |
| Occlusion, nuit, pluie/reflets | Boîtes et points inspectés indépendamment | Faux consensus, perte de texture et masque insuffisant |

Rapporter par séquence : nombre de paires, proportions initialisées/rejetées/acceptées, erreur de transfert sur points annotés non utilisés par l'estimateur, distribution des résidus par plan, durée p50/p95 du flot et durée totale, paramètres de prise de vue et détection. Comptabiliser aussi les paires rejetées; ne pas publier uniquement les cas acceptés. Une erreur en pixels ne constitue pas une MAE de vitesse.

La collecte et l'export d'un tel corpus ne sont pas automatiques dans l'application. Aucun corpus matériel de référence n'est fourni avec cet incrément. Les lectures du diagnostic sur l'émulateur sont des smoke tests, pas des mesures de précision ou d'énergie sur téléphone.
