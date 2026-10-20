# Licences des contenus et bibliothèques

| Version | Date |
|---|---|
| 1.0 | 2026-10-20 |

Ce fichier doit être mis à jour à chaque ajout de donnée, d'image, d'icône, de police ou de bibliothèque. Son contenu est repris dans l'écran Paramètres > Sources et licences.

## Données

| Contenu | Source | Licence | Obligation |
|---|---|---|---|
| Valeurs nutritionnelles (158 ingrédients) | Anses, Ciqual 2025, DOI 10.57745/RDMHWY | Licence Ouverte / Etalab 2.0 | Citer la source et la date (fait dans l'app et dans `docs/SOURCES.md`) |
| Recettes (141), descriptions d'exercices (43), séances pré-faites (7) | Rédaction originale Kalori | Création personnelle | — |
| Calendrier de saisonnalité | Saisie originale à partir de calendriers publics | Création personnelle | ⚖️ voir `docs/SOURCES.md` §3 |

## Icônes et images

| Contenu | Source | Licence |
|---|---|---|
| Icône de l'application (feuille) | Création originale (`ic_launcher_foreground.xml`) | Création personnelle |
| Icônes de navigation et d'interface | Material Icons (`androidx.compose.material:material-icons-extended`) | Apache 2.0 |
| Pictogrammes d'exercices (17 types de mouvement) | Création originale (`tools/gen_exercise_icons.py`) | Création personnelle |

## Bibliothèques

| Bibliothèque | Licence | Remarque |
|---|---|---|
| AndroidX (Compose, Room, Navigation, DataStore, Lifecycle…) | Apache 2.0 | |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 | |
| Dagger / Hilt | Apache 2.0 | |
| Google Tink | Apache 2.0 | |
| SQLCipher for Android (Community Edition, Zetetic LLC) | Licence de type BSD | **L'avis de copyright et la licence doivent être reproduits** dans la documentation de l'app (écran Licences) |
| Vico (graphiques, étape 6) | Apache 2.0 | |
| AndroidX Biometric (verrou de l'app) | Apache 2.0 | |
| SDK Java Anthropic `com.anthropic:anthropic-java` 2.68.0 (IA optionnelle) et ses dépendances (OkHttp, Jackson) | MIT / Apache 2.0 | Vérifier la licence exacte du SDK lors des mises à jour |

Pour la release, générer la liste exhaustive avec le plugin AboutLibraries ou `oss-licenses-plugin` (étape 7).
