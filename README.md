# Kalori

Application Android de nutrition et de sport : un plat par jour, la liste de courses de la semaine, des séances Salle et Street, et le suivi de la progression.

> Kalori ne remplace pas un médecin ni un diététicien et n'est pas un dispositif médical.

## Stack
Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room (chiffrée par SQLCipher), Navigation Compose (routes type-safe). Application **à usage personnel** : pas de compte, pas de serveur, pas de publication sur le Play Store. Toutes les données restent sur le téléphone, dans une base chiffrée.

## Ouvrir le projet
1. Android Studio (version stable récente) : **File > Open** puis sélectionner ce dossier.
2. Le wrapper Gradle est versionné (`./gradlew`). Le daemon Gradle exige un **JDK 21** (`gradle/gradle-daemon-jvm.properties`) : Gradle le choisit automatiquement s'il est installé (par exemple `~/.jdks/jbr-21…`, téléchargeable depuis Android Studio > Settings > Build Tools > Gradle > Gradle JDK). Le SDK Android doit contenir la plateforme **API 36** et les **build-tools 35.0.0**.
3. Si Android Studio propose des mises à jour d'AGP ou de bibliothèques, elles peuvent être acceptées : le catalogue `gradle/libs.versions.toml` centralise toutes les versions.
4. Installation rapide : brancher le téléphone (débogage USB activé) et lancer **Run** dans Android Studio (version debug).

## Documents
- `LEGAL_CHECKLIST.md` : ce qui s'applique à un usage personnel, et ce qu'il faudrait reprendre si l'app était un jour distribuée.
- `docs/SOURCES.md` : sources des données et des recommandations nutritionnelles.
- `ASSETS_LICENSES.md` : licences des données et bibliothèques.

## Données embarquées (catalogue alimentaire et sportif)
- `tools/ingredients.csv` : ingrédients avec leur code Ciqual, leur rayon, leurs allergènes, leurs régimes et leur saisonnalité (édité à la main).
- `tools/recipes.txt` : recettes originales (édité à la main).
- `app/src/main/assets/seed/exercises.json` et `workouts.json` : exercices et séances pré-faites (édités à la main).
- Régénérer `ingredients.json`, `recipes.json` et `meta.json` après toute modification :
  ```
  python3 tools/build_seed.py "chemin/vers/Table Ciqual 2025_FR_2025_11_03.xlsx"
  ```
  La table se télécharge sur https://doi.org/10.57745/RDMHWY (Licence Ouverte Etalab 2.0). Le script n'utilise que la bibliothèque standard de Python 3.10+. Il échoue si une recette est incohérente et affiche un rapport de couverture par mois et par régime.
- L'app réimporte le catalogue au démarrage dès que l'empreinte `seedHash` change. Les données de l'utilisateur ne sont pas touchées.

## Installer la version finale sur mon téléphone
1. Créer une fois la clé de signature (à conserver précieusement : sans elle, impossible de mettre à jour l'app installée) :
   ```
   keytool -genkeypair -v -keystore kalori-release.jks -alias kalori -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Copier `keystore.properties.example` en `keystore.properties` et le compléter. Ces deux fichiers sont exclus de git.
3. Construire et installer :
   ```
   ./gradlew testDebugUnitTest assembleRelease
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
4. Tester la release une première fois en entier (R8 est activé : premier lancement, plats, séance, graphiques, export, variante IA si utilisée).

## Sauvegarde
Les données n'existent que sur le téléphone (base chiffrée, aucune sauvegarde cloud automatique). Exporter régulièrement depuis Paramètres > Mes données, et garder le fichier en lieu sûr : il n'est pas chiffré. Sur un nouveau téléphone, « Restaurer une sauvegarde Kalori » dès l'écran d'accueil.
