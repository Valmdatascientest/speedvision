# Lunettes Meta — Sprint 8

## État

L'adaptateur est compilé contre le SDK Android officiel DAT **1.0.0**, publié le 24 septembre 2026. Il utilise `Wearables.createSession`, `SpecificDeviceSelector`, `DeviceSession.addCamera` et `Camera.stream`. Les lunettes ne sont pas simulées en production. L'absence de compagnon, d'inscription, de permission ou de lunettes ne produit aucune image factice.

Matériel déclaré pour les essais : **Samsung Z Flip7**, lunettes **Wayfarer 0015**, version déclarée **68449720064700100**. Cette chaîne n'a pas été lue sur le matériel par l'application et ne suffit pas à identifier sûrement une génération. Version Android/One UI, version Meta AI, type/firmware renvoyés par DAT et compatibilité restent à relever. Aucun téléphone physique n'était visible via ADB pendant cette intégration.

## Compiler le bon build

Le build ordinaire reste sans SDK Meta, sans permission Internet/Bluetooth et compatible API 28. Le build Meta est explicite, exige API 29 minimum et ajoute Internet/Bluetooth. Il n'ajoute ni microphone, ni service de capture en arrière-plan.

```sh
# Variables JAVA_HOME / ANDROID_USER_HOME / GRADLE_USER_HOME : voir README
./gradlew :app:assembleDebug -PmetaEnabled=true
python3 scripts/audit_android_manifest.py --meta
```

Le chemin reste `app/build/outputs/apk/debug/app-debug.apk` : il contient le **dernier profil compilé**. Copier le fichier sous un nom explicite si l'on conserve les deux profils. Un APK local avec modèles provisionnés contient les poids soumis à leurs propres conditions; ne pas le publier sans résoudre leur licence.

Pour l'inscription hors mode développeur, renseigner uniquement dans `local.properties` (ignoré par Git) les valeurs de votre application Wearables Developer Center :

```properties
meta.applicationId=VOTRE_IDENTIFIANT
meta.clientToken=VOTRE_CLIENT_TOKEN
```

Ne pas les envoyer dans une conversation ni les committer. Ces valeurs servent de métadonnées du SDK dans l'APK; ce ne sont pas des secrets protégés par le build. N'y mettre aucun token administrateur/serveur. Sans ces valeurs, un essai nécessite le mode développeur Meta AI et ses conditions d'accès, comme indiqué dans l'exemple officiel; il n'est pas activé par SpeedVision. Aucun identifiant ni inscription inventé.

Le modèle des lunettes, la région, les comptes de test et les versions doivent être admis par Meta. Vérifier ces points dans [Wearables Developer Center](https://wearables.developer.meta.com/docs/) avant l'essai. L'application ne remplace pas l'appairage dans Meta AI.

## Parcours sur le Z Flip7

1. Installer le build Meta local; appairer les lunettes dans Meta AI et vérifier le firmware/compte requis.
2. Ouvrir **Lunettes Meta**, puis **Activer la connexion Meta**. Autoriser Bluetooth si Android le demande.
3. Choisir **Inscrire dans Meta AI** et suivre le consentement du compagnon. Revenir dans SpeedVision.
4. Choisir **Autoriser la caméra des lunettes**. Cette permission est distincte de celle de CameraX sur le téléphone.
5. Vérifier le nom/type/compatibilité affichés, choisir les lunettes, puis **START** dans l'aperçu.
6. STOP, passage en arrière-plan, interruption ou déconnexion arrêtent la session. Après retour de connexion, relancer manuellement START; aucune reprise caméra automatique.

La désinscription et les autorisations restent gérées par Meta AI. L'entrée d'inscription initiée depuis Meta AI (`RegistrationRequest`, nouvelle en 1.0.0) n'est pas ajoutée : cette tranche utilise l'inscription initiée par le bouton dans SpeedVision.

## Images, horloges et limites

Configuration demandée : qualité `MEDIUM`, 15 images/s, `compressVideo=false`, aucun audio. Le décodage vidéo est assuré par le SDK. Notre adaptateur copie le buffer reçu avant suspension, attend du YUV I420 compact à dimensions paires, convertit en ARGB BT.709 pour l'aperçu puis échantillonne au plus 10 images/s. Les dimensions réelles sont conservées, sans agrandissement de plaque. Toute taille/forme inattendue est refusée; plafond 1920 × 1920.

Le format I420/BT.709 est documenté dans l'ancien convertisseur officiel; la documentation 1.0 décrit le buffer décodé comme YUV sans exposer de stride/rotation. Ce contrat reste à confirmer sur le flux de vos lunettes. Aucun format concurrent n'est deviné. Rotation déclarée 0 signifie « pixels tels que livrés », pas une orientation de tête connue. La calibration doit être réalisée pour ce flux exact et réévaluée après changement de firmware/mode.

Le temps métrique provient de `VideoFrame.presentationTimeUs`, jamais de l'heure de réception. PTS négatifs, doublons ou régressions provoquent un arrêt; changement de lecture invalide les suivis. Le tampon borné ne supprime que des images **déjà décodées**, jamais des paquets HEVC interdépendants. La connexion expire après 15 s; un flux sans image pendant 10 s est interrompu. Les changements de session, pause/fermeture et erreurs interrompent le flux, y compris les avertissements SDK actuellement traités conservativement comme arrêts.

Les indications batterie/thermique viennent de `DeviceSession.deviceInfo`; elles ne sont pas une mesure d'énergie ou de température du téléphone. L'incrément ne calibre aucune focale automatiquement et n'annonce toujours aucune vitesse live. Le module `mwdat-motion` apparu en 1.0 est expérimental et **non intégré**; aucune IMU de lunettes ni synchronisation avec les images n'est revendiquée.

## Confidentialité et distribution

L'activation Meta autorise les communications du SDK avec le compagnon/services concernés. Les métadonnées `ANALYTICS_OPT_OUT=true` et `CRASH_REPORTING_OPT_OUT=true` sont présentes et contrôlées dans le manifeste fusionné. Aucun OCR, enregistrement vidéo/photo/audio ni export automatique n'est ajouté. Le SDK peut maintenir ses échanges de gestion après fermeture du panneau; l'arrêt garanti par notre adaptateur porte sur la session caméra. L'audit du trafic réel reste à faire.

La CI publie uniquement les rapports du build Meta, pas son APK. Le SDK relève des [conditions Meta](https://github.com/facebook/meta-wearables-dat-android/blob/d3159f738676221181577dd745d83d10b6ab46ba/LICENSE); l'inclusion technique ne constitue pas une validation de distribution. Le build local sans SDK reste disponible.

## Preuves et tests matériels

Voir [rapport Sprint 8](docs/SPRINT_8_REPORT.md) et [protocole matériel](testing/meta/README.md). Les tests JVM de pixels et les tests SDK sans compagnon ne constituent pas une réception vidéo depuis des lunettes.

Sources primaires figées : [release 1.0.0](https://github.com/facebook/meta-wearables-dat-android/tree/d3159f738676221181577dd745d83d10b6ab46ba), [changelog](https://github.com/facebook/meta-wearables-dat-android/blob/d3159f738676221181577dd745d83d10b6ab46ba/CHANGELOG.md), [contrat de streaming](https://github.com/facebook/meta-wearables-dat-android/blob/d3159f738676221181577dd745d83d10b6ab46ba/.cursor/rules/camera-streaming.mdc), [convertisseur I420 officiel antérieur](https://github.com/facebook/meta-wearables-dat-android/blob/4e56e1864a5e78194bababc3a68775c4196cbed0/samples/CameraAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/stream/YuvToBitmapConverter.kt). Consultation le 25 septembre 2026.
