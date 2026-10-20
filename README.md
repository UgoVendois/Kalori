# NutriSport

Application Android de nutrition et de sport : un plat par jour, la liste de courses de la semaine, des séances Salle et Street, et le suivi de la progression.

> NutriSport ne remplace pas un médecin ni un diététicien et n'est pas un dispositif médical.

## Stack
Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room (chiffrée par SQLCipher), Navigation Compose (routes type-safe) et Firebase Auth. Les données de santé restent stockées uniquement sur l'appareil.

## Ouvrir le projet
1. Android Studio (version stable récente) : **File > Open** puis sélectionner ce dossier.
2. Le fichier `gradle-wrapper.jar` n'est pas versionné. Android Studio télécharge Gradle à partir de `gradle/wrapper/gradle-wrapper.properties`. Pour utiliser `./gradlew` en ligne de commande, lancer une fois `gradle wrapper` (avec un Gradle installé) ou utiliser le terminal d'Android Studio.
3. Si Android Studio propose des mises à jour d'AGP ou de bibliothèques, elles peuvent être acceptées : le catalogue `gradle/libs.versions.toml` centralise toutes les versions.
4. À partir de l'étape 3 : ajouter `app/google-services.json` (console Firebase). Ce fichier n'est **jamais** versionné.

## Documents de conformité
- `LEGAL_CHECKLIST.md` : à repasser à chaque release, à chaque changement d'IA et à chaque changement de loi.
- `docs/` : documents légaux, sources, Data safety, AIPD, points à valider par un avocat ou le DPO (ajoutés au fil des étapes).

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
