# Sources des données et des recommandations

| Version | Date |
|---|---|
| 1.0 | 2026-10-20 |

À revérifier à chaque passage de `LEGAL_CHECKLIST.md` (section 4) et à chaque nouvelle version de Ciqual.

## 1. Composition nutritionnelle des aliments

**Anses, Table de composition nutritionnelle des aliments Ciqual 2025** (fichier du 03/11/2025, publié le 19/11/2025).
- DOI : [10.57745/RDMHWY](https://doi.org/10.57745/RDMHWY) — entrepôt recherche.data.gouv.fr
- Licence : **Licence Ouverte / Etalab 2.0** : réutilisation libre, y compris commerciale, sous réserve de mentionner la source et la date de mise à jour.
- Mention affichée dans l'app (Paramètres > Sources et licences) : « Source : Anses, Table de composition nutritionnelle des aliments Ciqual 2025. »
- Utilisation : énergie (kcal, règlement UE 1169/2011), protéines (N × facteur de Jones), lipides, glucides, fibres alimentaires et lactose (seuil du régime « sans lactose »), pour 100 g de partie comestible. Les valeurs sont reprises **sans modification** par `tools/build_seed.py`. La correspondance ingrédient → aliment Ciqual est publiée dans `docs/INGREDIENTS_CIQUAL.md`.
- Conventions documentées : « traces » = 0 ; « < x » = x/2 ; valeur absente = 0 avec un avertissement à la génération. Si l'énergie manque (cas unique : ricotta), elle est recalculée avec les coefficients de l'annexe XIV du règlement 1169/2011 (4/4/9/2 kcal/g).

**Open Food Facts** (ODbL / DbCL, images CC BY-SA) : non utilisé en v1.

## 2. Allergènes

- Liste des 14 allergènes : **règlement (UE) n° 1169/2011, annexe II** (INCO).
- Affectation des allergènes et des traces aux ingrédients : établie manuellement pour NutriSport (`tools/ingredients.csv`) à partir de la composition usuelle des produits. Exemples de choix prudents : moutarde = moutarde + sulfites ; bouillon cube = céleri + traces de gluten et de lait ; galette de sarrasin = traces de gluten, œufs et lait. **Ces données ne remplacent pas l'étiquette du produit acheté** (avertissement affiché dans l'app).

## 3. Saisonnalité

Table rédigée pour NutriSport (`tools/ingredients.csv`, colonne `season`), en recoupant des calendriers publics de fruits et légumes de saison en France métropolitaine, notamment ADEME, « Mes fruits et légumes de saison » (agirpourlatransition.ademe.fr) et l'API Impact CO2 (impactco2.fr). Aucune donnée brute de ces sources n'est copiée : seuls les mois de disponibilité, qui sont des faits, ont été saisis à la main. ⚖️ La licence des données ADEME reste à confirmer avant toute reprise automatique.

Les produits importés sans production française significative (banane, avocat, citron) sont indiqués selon leur période usuelle de disponibilité en magasin.

## 4. Recettes et exercices

- **141 recettes** et **43 descriptions d'exercices** : textes originaux rédigés pour NutriSport, sans reprise de sources tierces.
- Les valeurs nutritionnelles des recettes sont **calculées** à partir des ingrédients (section 1), jamais saisies à la main.

## 5. Recommandations utilisées pour les calculs (étape 2)

| Sujet | Source | Usage dans l'app |
|---|---|---|
| Rythme de perte de poids | NICE CG189 / NHS : 0,5 à 1 kg par semaine (déficit d'environ 600 kcal/j) ; HAS, *Surpoids et obésité de l'adulte* (2011) et parcours de soins (2022) : réduction modérée et personnalisée, régimes très restrictifs déconseillés | Options 0,25 / 0,5 / 0,75 kg/sem, plafond de 1 % du poids par semaine |
| Rythme de prise de poids | Iraki J. et al., *Nutrition Recommendations for Bodybuilders in the Off-Season: A Narrative Review*, Sports 2019, 7(7):154 : +0,25 à 0,5 % du poids par semaine | Options 0,1 / 0,25 / 0,5 kg/sem, plafond de 0,5 % du poids par semaine |
| Seuil de maigreur | OMS : IMC < 18,5 kg/m² | Refus d'un poids cible sous ce seuil |
| Métabolisme de base | Mifflin M.D., St Jeor S.T. et al., Am J Clin Nutr 1990;51:241-7 | Estimation de la dépense énergétique |
| Répartition des macronutriments | Anses 2016, *Actualisation des repères du PNNS : élaboration des références nutritionnelles* : protéines 10-20 %, lipides 35-40 %, glucides 40-55 % de l'énergie | Lipides 35 %, glucides ≥ 40 %, avertissement hors fourchette |
| Fibres | Anses 2016 : 30 g/j chez l'adulte | Objectif affiché (⚠ chiffre à confirmer dans le rapport) |
| Personnes âgées | HAS : vigilance sur la fonte musculaire et la dénutrition après 70 ans | Avertissement |
| Plancher calorique | Seuils usuels de 1 200 kcal (femmes) et 1 500 kcal (hommes) | Masquage des options en dessous ⚖️ (à faire valider par un diététicien) |

Ces sources fondent des **estimations**. L'app ne pose aucun diagnostic et ne remplace pas un professionnel de santé.
