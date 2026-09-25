# Architecture SpeedVision — Sprint 5

## Modules et responsabilités

```text
app/                             Assemblage Android/Hilt
  camera/CameraXVideoSource       ImageAnalysis, permissions et lifecycle
  data/VideoFileSource            MediaExtractor + MediaCodec, vrais PTS
  vision/OnnxDetector             Prétraitement et session ONNX CPU
  geometry/DistanceEstimator     OpenCV IPPE, profondeur axiale et rejets
  geometry/CalibrationJson       Profil versionné, validation stricte
  presentation/SpeedWorkbench    Import/relecture et export CSV explicites
  presentation/CalibrationWorkbench  Image arrêtée, paramètres, coins et JSON
  vision/DetectionEngine          Véhicules → ROIs bornées → plaques
  presentation/PreviewViewModel   Coroutines, état, annulation, statistiques
  MainActivity.kt                 Compose, source, cadres et diagnostics
 domain/                         Kotlin/JVM sans Android
  VideoSource / VideoFrame        Contrat, pixels, PTS, origine, crop natif
  Detection / Letterbox           Coordonnées et inverse resize/padding
  YoloPostprocessor               Validation sortie, filtre classes, NMS
  SpeedEstimator / SpeedCsv      Huber, continuité, qualité, CSV borné
  Calibration / ImagePoint       Contrats métriques, source et coordonnées
  VehicleTracker                 Kalman, association IoU/Hungarian, identités
  LatencyWindow                  Statistiques bornées p50/p95
 models/                         Audit des poids et procédure de préparation
 scripts/                        Provisionnement vérifié, évaluation, checks
 testing/                        Protocoles et tests Python de métriques
```

Hilt injecte les factories et un moteur propre au ViewModel. Le domaine ne dépend ni d'Android ni du runtime ML. Détection et tracking sont distincts : `vehicleIndex` reste l'indice du parent dans une image. `VehicleTracker` fournit des `trackId` temporels et rattache les plaques uniquement aux parents confirmés observés.

```mermaid
flowchart LR
  F[VideoFileSource] --> V[Frame native et horodatée]
  C[CameraXVideoSource] --> V
  V --> U[Crop + rotation pixels]
  U --> D[YOLO11n véhicules]
  D --> R[4 ROI véhicule maximum]
  R --> P[YOLO11n plaques]
  P --> B[Retour aux coordonnées image]
  B --> T[VehicleTracker : PTS, Kalman, Hungarian]
  T --> UI[Image, cadres et identités du même instant]
```

## Géométrie et pixels

Le fichier conserve les pixels du crop natif, jusqu'à la limite d'entrée 1920 × 1920. CameraX demande 1280 × 720, mais la résolution effective dépend du matériel et est enregistrée. `FrameGeometry` conserve dimensions natives, origine du crop et origine temporelle. La rotation 0/90/180/270 est appliquée avant l'inférence. Les boîtes sont exprimées en pixels de cette image redressée.

Chaque réseau reçoit une entrée RGB float32 NCHW 640 × 640, après resize conservant le ratio et padding 114. `Letterbox` conserve les dimensions redimensionnées entières et inverse exactement scale/padding; l'étage plaque ajoute l'origine ROI. Les limites sont clampées, NaN/Inf et géométries dégénérées rejetées. L'overlay utilise le même ajustement `Fit` que l'image : marges et échelle sont identiques.

Le moteur géométrique ramène les coins observés dans le repère natif de K en inversant crop/rotation. Il ne réutilise pas la calibration native sur l'entrée 640 du réseau. Les cadres du détecteur ne sont pas des coins de plaque.

## Temps, ressources et cycle de vie

Vidéo : sélection par PTS à 10 images/s maximum. Le compteur garde la séquence décodée avant échantillonnage. Caméra : timestamps `imageInfo.timestamp` en microsecondes, distincts d'une horloge média; `KEEP_ONLY_LATEST` et fermeture de tous les `ImageProxy`. Les sources n'enregistrent rien.

`Flow.conflate()` conserve la dernière frame disponible pendant l'inférence. Pas de file illimitée; les écarts de séquence visibles sont comptés, sans prétendre compter les images perdues en amont du callback caméra. L'affichage attend le résultat correspondant à l'image traitée; un ancien résultat n'est pas superposé à une nouvelle image.

Un compteur de session invalide le travail en cours sur STOP/START/remplacement. Les erreurs et les boîtes sont effacées aux transitions appropriées. Les appels ONNX natifs ne sont pas interrompus au milieu d'un opérateur : leur résultat est abandonné si la coroutine/session est annulée. Un Mutex sérialise exécution et fermeture des sessions; les tenseurs/résultats natifs sont fermés à chaque passage. Le ViewModel ferme les sessions à sa destruction.

CameraX est lié au lifecycle de l'activité, utilise un executor unique et ne demande que CAMERA. Le passage en arrière-plan stoppe. La destruction détache la caméra, libère observer/executor et impose une nouvelle sélection, évitant de conserver une ancienne activité après rotation. Le lecteur de fichier peut conserver son ViewModel, sans redémarrage automatique.

## Performance et qualité

Détection CPU/2 threads, seuil 0,35, NMS 0,45 et 30 véhicules maximum. Au plus quatre ROI sont traitées par ordre de score; les omissions sont affichées. Conséquence assumée : une plaque sans véhicule détecté est manquée. Les durées incluent pré/post-traitement; le chargement initial des sessions est exclu de p50/p95. Fenêtre de 120 mesures, compteur visible. Le temps depuis décodage inclut attente et traitement, mais ne prétend pas mesurer exposition caméra → affichage.

Les assets absents ou incompatibles donnent un état explicite; jamais une détection factice. Le script vérifie les poids amont, l'application vérifie les hashes des exports avec leur manifeste. Aucune confiance de vitesse n'est calculée.

## Suite planifiée

Sprint 6 : alignement du fond implémenté, compensation métrique non disponible. Sprint 7 : politique d’annonces et AudioOutput/TTS en cours, voir ci-dessous. Sprint 8 : adaptateur Meta officiel revalidé. Sprint 9 : optimisation et validation indépendante sur matériel.

Voir [algorithme mathématique](docs/ALGORITHM.md) et [audit modèles](models/README.md). Le runtime ONNX a été choisi ici pour charger les poids réels disponibles sans ajouter une seconde conversion TFLite; ce choix devra être benchmarké face aux alternatives sur téléphone cible.

## Suivi Sprint 3

Tracker Kotlin pur, sérialisé sur le collecteur du ViewModel après acceptation de la génération courante. Quatre filtres Kalman indépendants position/vitesse portent centre X/Y et largeur/hauteur; le pas temporel vient des PTS en secondes. Association globale Hungarian sur coût 1−IoU, même classe et IoU ≥ 0,20, avec colonnes factices pour les pistes sans correspondance. Aucun code du dépôt SORT n'est copié.

Confirmation après 3 observations consécutives. Une piste confirmée peut rester perdue ≤ 500 ms depuis sa dernière observation; elle n'a alors ni indice de détection ni plaque attachée. Une piste provisoire manquée est supprimée. Maximum 60 pistes; les plus anciennes perdues sont évincées en priorité si nécessaire. IDs croissants non réutilisés pendant la vie du tracker, même après reset; ils ne sont pas persistants entre lancements.

STOP/START, source, détection, erreur source, crop/origine temporelle/rotation, dimensions, PTS dupliqués ou inversés et gap > 500 ms invalident les pistes. Une génération de détection empêche une inférence démarrée avant un changement de bouton de recréer des pistes. Les plaques ne sont pas prédites ni conservées : `TrackedPlate` indique le parent courant, pas une réidentification indépendante de la plaque. Aucune fenêtre de distance/vitesse n'existe encore.

Les covariances et seuils sont expérimentaux, sans confiance probabiliste affichée. Occlusion longue, changement de classe et mouvement brusque peuvent créer une nouvelle identité. Deux objets identiques superposés restent ambigus sans apparence. Validation synthétique et intégration sur image répétée ne prouvent pas la stabilité terrain.

## Géométrie Sprint 4

OpenCV Android 4.12.0 fournit IPPE et reprojection; le domaine reste sans dépendance Android/OpenCV. La calibration conserve les intrinsics et Brown5 natifs. Les coins observés sont remis dans ce repère par inversion exacte du crop/rotation. Toutes les matrices natives sont libérées en `finally`. Aucune homographie à échelle arbitraire n'est interprétée comme métrique.

L'assistant stoppe la source, conserve l'image/PTS en mémoire et travaille sur cette image seule. Le calcul s'exécute hors thread UI; une révision invalide son résultat si paramètres ou coins changent. Une calibration exportée est un JSON sans pixels, écrit seulement sur sélection explicite du document. Les profils importés ne sont jamais réaffectés silencieusement à une autre source. L'URI source est hachée pour l'identifiant local; ce n'est pas une preuve de contenu ou de mode optique.

`DistanceEstimate` est soit `Accepted(Z, reprojectionRmsPx, tiltDegrees)` soit `Rejected(reason)`. L'acceptation signifie que les contrôles géométriques ont passé, pas une précision statistique acquise. L'incertitude reste non quantifiée et affichée ainsi. Le résultat est indépendant du tracker : pas de fenêtre temporelle, pas d'attribution automatique d'une mesure manuelle à une piste. La profondeur en direct attend un détecteur de coins validé. Voir [procédure et seuils](CALIBRATION.md).

## Vitesse Sprint 5

Le domaine contient une fenêtre de profondeurs par instance `SpeedEstimator`, pour une seule identité active. Le laboratoire est séparé du ViewModel vidéo : il travaille sur un CSV explicitement importé, conserve les PTS et ne mélange pas les pistes. Les calculs tournent sur `Dispatchers.Default` avec contrôle d'annulation entre observations. L'import est borné; aucune persistance automatique. Un résultat rejeté comporte un motif et aucun champ vitesse.

La régression centrée utilise le temps source, une initialisation médiane des pentes et cinq itérations Huber pondérées par qualité. La médiane/MAD borne l'influence des valeurs aberrantes; les contrôles exigent un effectif et une durée d'inliers suffisants, un dernier point cohérent et des pentes de demi-fenêtres compatibles. Les identités, paramètres et critères exacts sont décrits dans [l'algorithme](docs/ALGORITHM.md). `qualityScore` est une heuristique, pas un intervalle ni une probabilité.

L'assistant PnP peut exporter une observation avec son vrai PTS, un ID annoté, une qualité explicitement évaluée et le hash complet du profil de calibration. Il n'attribue pas l'ID du tracker automatiquement et ne collecte pas de série de profondeurs en continu. La liaison automatique détecteur de coins → géométrie → piste → vitesse reste à faire après validation de la géométrie.

Le CSV de résultats indique algorithme, temps source/référence, identité, profondeur, vitesse signée, qualité, effectifs, résidu, latence de calcul et motif. Le temps de calcul ne comprend ni décodage, ni détection, ni annotation; il n'est pas une latence caméra → vitesse. Les fichiers n'apparaissent qu'après confirmation du sélecteur système, sans OCR ni image.

## Mouvement du fond Sprint 6

Le collecteur vidéo possède un `BackgroundMotionEstimator` sérialisé. Il traite les pixels redressés après détection, hors thread UI, puis ne publie que si source, lecture et génération sont encore valides. Le moteur conserve uniquement deux matrices réduites (gris/masque) entre paires. L’annulation du collecteur attend la fin du calcul natif avant libération. Les identités incluent lecture, activation de détection et géométrie; aucun historique ne traverse une reprise.

Le résultat est un diagnostic avec homographie optionnelle en pixels natifs redressés. La compensation `residual(previous,current)` soustrait la prédiction du fond, sans conversion métrique. Le tracker et le moteur de vitesse restent indépendants de cet alignement : une scène mobile cohérente est indiscernable d’un mouvement de caméra dans certains cas. Aucun accès IMU ajouté. Voir [seuils, capteurs et limites](docs/SPRINT_6_REPORT.md).

## Voix Sprint 7 — première tranche

`VoicePolicy` reçoit explicitement une horloge monotone, l’instant d’acquisition associé, le résultat et l’identité. Elle émet `Speak` ou `Silent(stopCurrent, reason)`; elle ne lit ni fichier ni capteur. Mémoire constante, paramètres validés, PTS distincts et contrôle de fraîcheur. Le consommateur futur devra appeler la politique pour chaque rejet et en cas de silence du flux; le flag live ne doit jamais être posé sur une relecture CSV.

`AudioOutput` est désormais séparé du contrat vidéo. Son adaptateur Android est détenu par le dialogue de test, sur le thread principal; les callbacks TTS sont repostés sur ce thread et filtrés par identifiant d’énoncé. Fermeture idempotente, arrêt/désactivation à ON_STOP, interruption sans reprise sur perte de focus ou changement de périphériques disponibles. Initialisation bornée à 10 s, énoncé à 15 s; pas de file de messages périmés. Seule la phrase explicite sans mesure est accessible dans l’interface actuelle. Les seuils du domaine attendent l’intégration des futures mesures live.

Le moteur choisit une voix déclarée française/hors ligne/installée et ne télécharge rien lui-même. Android gère la sortie média; l’inventaire des périphériques n’est pas une preuve de route active. [Réserves et validation](docs/SPRINT_7_REPORT.md).
