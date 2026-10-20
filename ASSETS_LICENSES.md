# Licences des contenus et bibliothèques

| Version | Date |
|---|---|
| 1.0 | 2026-10-20 |

Ce fichier doit être mis à jour à chaque ajout de donnée, d'image, d'icône, de police ou de bibliothèque. Son contenu est repris dans l'écran Paramètres > Sources et licences.

## Données

| Contenu | Source | Licence | Obligation |
|---|---|---|---|
| Valeurs nutritionnelles (158 ingrédients) | Anses, Ciqual 2025, DOI 10.57745/RDMHWY | Licence Ouverte / Etalab 2.0 | Citer la source et la date (fait dans l'app et dans `docs/SOURCES.md`) |
| Recettes (141), descriptions d'exercices (43), séances pré-faites (7) | Rédaction originale NutriSport | Propriété de l'éditeur | — |
| Calendrier de saisonnalité | Saisie originale à partir de calendriers publics | Propriété de l'éditeur | ⚖️ voir `docs/SOURCES.md` §3 |

## Icônes et images

| Contenu | Source | Licence |
|---|---|---|
| Icône de l'application (feuille) | Création originale (`ic_launcher_foreground.xml`) | Propriété de l'éditeur |
| Icônes de navigation et d'interface | Material Icons (`androidx.compose.material:material-icons-extended`) | Apache 2.0 |
| Pictogrammes d'exercices (17 types de mouvement) | Création originale, prévue à l'étape 5 | Propriété de l'éditeur |

## Bibliothèques

| Bibliothèque | Licence | Remarque |
|---|---|---|
| AndroidX (Compose, Room, Navigation, DataStore, Lifecycle…) | Apache 2.0 | |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 | |
| Dagger / Hilt | Apache 2.0 | |
| Google Tink | Apache 2.0 | |
| SQLCipher for Android (Community Edition, Zetetic LLC) | Licence de type BSD | **L'avis de copyright et la licence doivent être reproduits** dans la documentation de l'app (écran Licences) |
| Vico (graphiques, étape 6) | Apache 2.0 | |
| Firebase Auth, Credential Manager, Play Billing (étapes 3 et 8) | Conditions Google / Apache 2.0 selon le composant | Conditions d'utilisation des API Google à accepter |

Pour la release, générer la liste exhaustive avec le plugin AboutLibraries ou `oss-licenses-plugin` (étape 7).
