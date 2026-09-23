# Faisabilité — revue du 23 septembre 2026

## Décision

Prototype expérimental réalisable sous conditions. Une plaque monoculaire de taille connue donne une profondeur métrique sous hypothèses de pose et de calibration. La dérivée est une vitesse relative de profondeur, pas une vitesse absolue routière. La disponibilité d'un flux ne garantit ni résolution utile, ni précision, ni temps réel soutenu. Aucun résultat terrain n'est encore disponible.

Le premier incrément doit lire une vidéo locale avec ses PTS réels. L'accès lunettes n'est pas une dépendance du Sprint 1. Trois verrous avant une annonce : pixels suffisants sur la plaque, calibration du flux réellement reçu, mouvement de caméra suffisamment contraint.

## SDK Meta vérifié

Le [dépôt Android officiel](https://github.com/facebook/meta-wearables-dat-android) annonce un Device Access Toolkit en developer preview, publié sur Maven Central en 0.9.0. L'inscription de l'application et les canaux de test passent par le Wearables Developer Center. Il faut revalider ces éléments au Sprint 8.

Le [changelog officiel](https://github.com/facebook/meta-wearables-dat-android/blob/main/CHANGELOG.md) date 0.9.0 du 3 août 2026 : la caméra s'attache désormais via `DeviceSession.addCamera`, le flux est accessible par `Camera.stream`. Les anciens exemples `addStream` ne conviennent plus. Il documente des PTS, des options de cadence et du flux compressé dans les versions antérieures. Cela constitue une piste d'intégration, pas une intégration effectuée ou testée sur nos lunettes.

La [FAQ Meta](https://developers.meta.com/wearables/faq/) décrit caméra, microphone et audio. Le routage TTS devra être vérifié comme sortie Android Bluetooth avec les lunettes appairées. Nous ne supposons aucune API Meta dédiée à `speak`, ni aucun accès à une IMU brute, à la focale calibrée ou au temps d'exposition : ces accès ne sont pas établis par les références consultées. L'IMU du téléphone dans une poche ne mesure pas la rotation de la tête.

Le modèle exact de lunettes, son firmware, le téléphone, sa version Android et la version de l'application compagnon restent à inventorier avec le matériel. Tester inscription, consentement caméra, déconnexion, reprise, batterie, température, Bluetooth simultané à la vidéo et latence TTS. La qualité enregistrée par l'application native ne prouve pas celle du flux SDK.

## Contraintes principales

- Petite plaque : avec fx = 900 px et W = 0,52 m (exemple configurable), w vaut seulement 15,6 px à 30 m. Une erreur de 1 px représente environ 6,4 % sur Z avant les autres erreurs.
- Perspective : la bbox axis-aligned confond pose, roulis et échelle. Une homographie qui impose une largeur arbitraire ne récupère pas la profondeur.
- Caméra portée : rotation et translation modifient Z. Une scène monoculaire seule ne sépare pas généralement les translations caméra et véhicule.
- Rolling shutter, flou et stabilisation numérique : modèle pinhole parfois invalide, intrinsics potentiellement variables. Une calibration photo n'est pas automatiquement valable pour le flux.
- SDK en preview : interfaces évolutives, disponibilité et fonctionnement soumis au matériel et aux services Meta.
- Chauffe, batterie, débit radio, décodeur et inférence : mesures nécessaires sur le téléphone cible. Aucune promesse de 24 fps.
- Plaques non standard, supports courbes, plaques occultées ou angles importants : rejet explicite.

## Modèle retenu pour le Sprint 2

Choix de départ : **YOLO11n préentraîné COCO pour les véhicules**, puis **détecteur léger de plaques spécialisé dans les ROI véhicules**. [YOLO11 officiel](https://docs.ultralytics.com/models/yolo11/) fournit une variante nano et des exports mobiles. Les [classes COCO](https://docs.ultralytics.com/datasets/detect/coco/) incluent des véhicules mais pas les plaques : aucun poids COCO ne sera présenté comme détecteur de plaques.

Le poids spécialisé plaque reste à sélectionner après audit de licence, provenance, métriques par taille de plaque et compatibilité des pays. Aucun checkpoint tiers non vérifié n'est embarqué. Si aucun poids admissible ne convient, fine-tuner un modèle préentraîné, jamais entraîner depuis zéro. Pour quatre coins, un modèle de keypoints nécessitera annotations adaptées et validation de pose.

Comparer YOLO11n et YOLOv8n à résolution/jeu identiques. Choisir TFLite float32 comme référence d'export Android, puis FP16 GPU et INT8 avec données représentatives; ONNX Runtime Mobile sert d'alternative si l'export TFLite échoue ou régresse. MediaPipe facilite l'orchestration, mais ne fournit pas à lui seul un détecteur de plaques validé pour ce cas. Décision runtime définitive par latence p50/p95, mémoire, chauffe et rappel sur petites plaques, pas par benchmark desktop. Vérifier les [conditions Ultralytics AGPL/Enterprise](https://www.ultralytics.com/license) avant distribution; aucune licence de poids n'est présumée compatible avec une distribution propriétaire.

## Confidentialité

Sprint 1 : aucun accès Internet, caméra, microphone ou stockage global dans le manifeste. Un URI choisi explicitement via le sélecteur Android est lu sans copie ni enregistrement; aucun OCR. Un fournisseur de documents peut lui-même être distant : choisir un fichier déjà présent sur le téléphone pour un essai hors ligne. Pas de conservation du droit URI après redémarrage du processus. Le SDK Meta n'est pas inclus. À son intégration, vérifier et désactiver les collectes optionnelles documentées, puis auditer le trafic et les conditions applicables.

## Limites de cette décision

Faisabilité logicielle : favorable. Métrologie : expérimentale. Vitesse absolue et homologation : hors périmètre. MAE < 5 km/h : objectif à tester, jamais garantie. Les Sprints 0–1 ne produisent aucune estimation.
