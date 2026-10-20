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
