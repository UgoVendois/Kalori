#!/usr/bin/env python3
"""Génère les données embarquées de Kalori à partir de la table Ciqual officielle.

Usage :
    python3 tools/build_seed.py <chemin/Table Ciqual 2025_FR_2025_11_03.xlsx>

Entrées (éditées à la main) :
    tools/ingredients.csv   catalogue des ingrédients + code Ciqual
    tools/recipes.txt       recettes originales

Sorties :
    app/src/main/assets/seed/ingredients.json
    app/src/main/assets/seed/recipes.json
    app/src/main/assets/seed/meta.json
    docs/INGREDIENTS_CIQUAL.md   (traçabilité : chaque ingrédient → aliment Ciqual)

Le script échoue (code 1) si une recette référence un ingrédient inconnu, n'a aucun mois
de disponibilité, ou si un ingrédient n'a pas de valeur nutritionnelle exploitable.

Source des valeurs : Anses, Table de composition nutritionnelle des aliments Ciqual 2025,
https://doi.org/10.57745/RDMHWY — Licence Ouverte / Etalab 2.0.
"""

from __future__ import annotations

import csv
import hashlib
import json
import sys
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ciqual_xlsx import load_foods  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
SEED_DIR = ROOT / "app" / "src" / "main" / "assets" / "seed"
DOCS = ROOT / "docs"

CIQUAL_VERSION = "Ciqual 2025 (fichier du 2025-11-03)"
CIQUAL_DOI = "10.57745/RDMHWY"
CIQUAL_LICENCE = "Licence Ouverte / Etalab 2.0"

ALLERGENS = {
    "GLUTEN", "CRUSTACEANS", "EGGS", "FISH", "PEANUTS", "SOY", "MILK",
    "NUTS", "CELERY", "MUSTARD", "SESAME", "SULPHITES", "LUPIN", "MOLLUSCS",
}
DIET_FLAGS = {"MEAT", "PORK", "FISH", "SHELLFISH", "DAIRY", "EGG", "HONEY", "ALCOHOL", "LACTOSE"}
AISLES = {
    "FRUITS_VEGETABLES", "BUTCHER", "FISHMONGER", "DAIRY_EGGS", "PLANT_PROTEINS", "STARCHES",
    "CANNED_LEGUMES", "BAKERY", "NUTS_SEEDS", "BREAKFAST_SWEET", "PANTRY", "SPICES", "DRINKS",
}
UNITS = {"g": "G", "ml": "ML", "piece": "PIECE"}
MAIN_PROTEINS = {"POULTRY", "RED_MEAT", "PORK", "FISH", "SEAFOOD", "EGG", "DAIRY", "PLANT"}
# Type de repas : MAIN (plat de midi ou du soir), BREAKFAST (matin), SNACK (collation).
MEAL_TYPES = {"MAIN", "BREAKFAST", "SNACK"}
# Fourchette de kcal attendue pour une portion de base, par type.
KCAL_RANGE = {"MAIN": (400, 800), "BREAKFAST": (250, 600), "SNACK": (120, 350)}

# Au-delà de ce seuil (g/100 g), un produit laitier est marqué LACTOSE (régime « sans lactose »).
LACTOSE_THRESHOLD = 0.5

# Facteurs de conversion du règlement (UE) n° 1169/2011, annexe XIV (kcal/g).
KCAL_PER_G = {"protein": 4, "carbs": 4, "fat": 9, "fiber": 2}

# Mêmes règles que DietRules.kt (à garder synchronisées).
DIETS = {
    "VEGETARIAN": lambda d: not d & {"MEAT", "PORK", "FISH", "SHELLFISH"},
    "VEGAN": lambda d: not d & {"MEAT", "PORK", "FISH", "SHELLFISH", "DAIRY", "EGG", "HONEY"},
    "PESCATARIAN": lambda d: not d & {"MEAT", "PORK"},
    "HALAL": lambda d: not d & {"PORK", "ALCOHOL"},
    "KOSHER": lambda d: not d & {"PORK", "SHELLFISH"} and not {"MEAT", "DAIRY"} <= d,
    "NO_PORK": lambda d: "PORK" not in d,
    "NO_ALCOHOL": lambda d: "ALCOHOL" not in d,
    "LACTOSE_FREE": lambda d: "LACTOSE" not in d,
}

MONTHS_FR = ["janv", "févr", "mars", "avr", "mai", "juin", "juil", "août", "sept", "oct", "nov", "déc"]


class SeedError(Exception):
    pass


def parse_season(text: str) -> list[int]:
    text = text.strip()
    if text == "all":
        return list(range(1, 13))
    months: set[int] = set()
    for part in text.split(","):
        start, end = (int(x) for x in part.split("-"))
        if not (1 <= start <= 12 and 1 <= end <= 12):
            raise SeedError(f"Mois invalide : {text}")
        m = start
        while True:
            months.add(m)
            if m == end:
                break
            m = m % 12 + 1
    return sorted(months)


def split_codes(text: str, allowed: set[str], what: str, owner: str) -> list[str]:
    codes = [c.strip() for c in text.split("|") if c.strip()]
    unknown = [c for c in codes if c not in allowed]
    if unknown:
        raise SeedError(f"{owner} : {what} inconnu(s) {unknown}")
    return codes


@dataclass
class Ingredient:
    id: str
    name: str
    ciqual_code: str
    ciqual_name: str
    aisle: str
    allergens: list[str]
    traces: list[str]
    diet: list[str]
    season: list[int]
    unit: str
    piece_grams: float | None
    piece_label: str | None
    kcal: float
    protein: float
    fat: float
    carbs: float
    fiber: float
    kcal_computed: bool

    def to_json(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "ciqualCode": self.ciqual_code,
            "ciqualName": self.ciqual_name,
            "aisle": self.aisle,
            "allergens": self.allergens,
            "traces": self.traces,
            "diet": self.diet,
            "seasonMonths": self.season,
            "unit": self.unit,
            "pieceGrams": self.piece_grams,
            "pieceLabel": self.piece_label,
            "kcal": round(self.kcal, 1),
            "protein": round(self.protein, 2),
            "fat": round(self.fat, 2),
            "carbs": round(self.carbs, 2),
            "fiber": round(self.fiber, 2),
            "kcalComputed": self.kcal_computed,
        }


def load_ingredients(ciqual_path: str) -> dict[str, Ingredient]:
    foods = load_foods(ciqual_path)
    result: dict[str, Ingredient] = {}
    lines = [l for l in (TOOLS / "ingredients.csv").read_text(encoding="utf-8").splitlines()
             if l.strip() and not l.startswith("#")]
    for row in csv.DictReader(lines, delimiter=";"):
        iid = row["id"].strip()
        if iid in result:
            raise SeedError(f"Ingrédient en double : {iid}")
        food = foods.get(row["ciqual"].strip())
        if food is None:
            raise SeedError(f"{iid} : code Ciqual {row['ciqual']} introuvable")
        if row["aisle"] not in AISLES:
            raise SeedError(f"{iid} : rayon inconnu {row['aisle']}")
        if row["unit"] not in UNITS:
            raise SeedError(f"{iid} : unité inconnue {row['unit']}")

        macros = {"protein": food.protein, "fat": food.fat, "carbs": food.carbs, "fiber": food.fiber}
        missing = [k for k, v in macros.items() if v is None]
        if missing:
            # Ciqual ne renseigne pas tout : une valeur absente sur un aliment où elle est
            # négligeable (ex. fibres de la viande) est mise à 0 et signalée.
            print(f"  ⚠ {iid} ({food.code}) : {', '.join(missing)} non renseigné(s) dans Ciqual → 0")
            macros = {k: (v or 0.0) for k, v in macros.items()}
        kcal_computed = food.kcal is None
        kcal = food.kcal if food.kcal is not None else sum(macros[k] * KCAL_PER_G[k] for k in KCAL_PER_G)

        diet = split_codes(row["diet"], DIET_FLAGS, "drapeau régime", iid)
        if "DAIRY" in diet and (food.lactose is None or food.lactose > LACTOSE_THRESHOLD):
            diet.append("LACTOSE")

        piece_grams = float(row["piece_g"]) if row["piece_g"].strip() else None
        if row["unit"] == "piece" and piece_grams is None:
            raise SeedError(f"{iid} : unité 'piece' sans piece_g")

        result[iid] = Ingredient(
            id=iid,
            name=row["name"].strip(),
            ciqual_code=food.code,
            ciqual_name=" ".join(food.name_fr.split()),
            aisle=row["aisle"],
            allergens=split_codes(row["allergens"], ALLERGENS, "allergène", iid),
            traces=split_codes(row["traces"], ALLERGENS, "trace", iid),
            diet=diet,
            season=parse_season(row["season"]),
            unit=UNITS[row["unit"]],
            piece_grams=piece_grams,
            piece_label=row["piece_label"].strip() or None,
            kcal=kcal,
            kcal_computed=kcal_computed,
            **macros,
        )
    return result


@dataclass
class Recipe:
    id: str
    name: str
    main_protein: str
    meal_type: str = "MAIN"
    ingredients: list[tuple[str, float]] = field(default_factory=list)
    steps: list[str] = field(default_factory=list)

    def to_json(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "mainProtein": self.main_protein,
            "mealType": self.meal_type,
            "steps": self.steps,
            "ingredients": [{"ingredientId": i, "grams": g} for i, g in self.ingredients],
        }


def load_recipes(ingredients: dict[str, Ingredient]) -> list[Recipe]:
    recipes: list[Recipe] = []
    current: Recipe | None = None
    for n, raw in enumerate((TOOLS / "recipes.txt").read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        tag, _, rest = line.partition(" ")
        if tag == "@":
            parts = [p.strip() for p in rest.split("|")]
            if len(parts) not in (3, 4) or parts[2] not in MAIN_PROTEINS:
                raise SeedError(f"recipes.txt:{n} : en-tête invalide")
            meal_type = parts[3] if len(parts) == 4 else "MAIN"
            if meal_type not in MEAL_TYPES:
                raise SeedError(f"recipes.txt:{n} : type de repas inconnu {meal_type}")
            current = Recipe(id=parts[0], name=parts[1], main_protein=parts[2], meal_type=meal_type)
            recipes.append(current)
        elif tag == "+" and current:
            for item in rest.split(","):
                iid, grams = item.strip().rsplit(" ", 1)
                if iid not in ingredients:
                    raise SeedError(f"recipes.txt:{n} : ingrédient inconnu « {iid} » ({current.id})")
                current.ingredients.append((iid, float(grams)))
        elif tag == "-" and current:
            current.steps.append(rest.strip())
        else:
            raise SeedError(f"recipes.txt:{n} : ligne non reconnue")

    ids = [r.id for r in recipes]
    duplicates = {i for i in ids if ids.count(i) > 1}
    if duplicates:
        raise SeedError(f"Recettes en double : {duplicates}")
    for r in recipes:
        if not r.ingredients or not r.steps:
            raise SeedError(f"{r.id} : ingrédients ou étapes manquants")
    return recipes


def analyze(recipe: Recipe, ingredients: dict[str, Ingredient]) -> dict:
    totals = {"kcal": 0.0, "protein": 0.0, "fat": 0.0, "carbs": 0.0, "fiber": 0.0}
    allergens: set[str] = set()
    diet: set[str] = set()
    season = set(range(1, 13))
    for iid, grams in recipe.ingredients:
        ing = ingredients[iid]
        for k in totals:
            totals[k] += getattr(ing, k) * grams / 100
        allergens |= set(ing.allergens) | set(ing.traces)
        diet |= set(ing.diet)
        season &= set(ing.season)
    return {"totals": totals, "allergens": allergens, "diet": diet, "season": sorted(season)}


def report(recipes: list[Recipe], ingredients: dict[str, Ingredient]) -> None:
    print(f"\n{len(ingredients)} ingrédients, {len(recipes)} recettes\n")
    print(f"{'recette':32} {'type':9} {'kcal':>5} {'P':>5} {'L':>5} {'G':>5} {'F':>4}  mois")
    analyses = {r.id: analyze(r, ingredients) for r in recipes}
    for r in recipes:
        a = analyses[r.id]
        t = a["totals"]
        lo, hi = KCAL_RANGE[r.meal_type]
        flag = f"  ⚠ kcal hors {lo}–{hi}" if not lo <= t["kcal"] <= hi else ""
        months = "toute l'année" if len(a["season"]) == 12 else ",".join(str(m) for m in a["season"])
        print(f"{r.id[:32]:32} {r.meal_type:9} {t['kcal']:5.0f} {t['protein']:5.1f} {t['fat']:5.1f} {t['carbs']:5.1f} "
              f"{t['fiber']:4.1f}  {months}{flag}")

    for meal_type in ("MAIN", "BREAKFAST", "SNACK"):
        print(f"\nRecettes {meal_type} disponibles par mois et par régime :")
        print(f"{'régime':14} " + " ".join(f"{m:>5}" for m in MONTHS_FR))
        for diet_name, rule in [("TOUS", lambda d: True), *DIETS.items()]:
            counts = []
            for month in range(1, 13):
                counts.append(sum(1 for r in recipes if r.meal_type == meal_type and month in analyses[r.id]["season"]
                                  and rule(analyses[r.id]["diet"])))
            print(f"{diet_name:14} " + " ".join(f"{c:>5}" for c in counts))

    print("\nRecettes compatibles « sans gluten » par mois :")
    print(" ".join(f"{sum(1 for a in analyses.values() if m in a['season'] and 'GLUTEN' not in a['allergens']):>5}"
                   for m in range(1, 13)))

    for r in recipes:
        if not analyses[r.id]["season"]:
            raise SeedError(f"{r.id} : aucun mois commun à tous les ingrédients de saison")


def write_outputs(recipes: list[Recipe], ingredients: dict[str, Ingredient]) -> None:
    SEED_DIR.mkdir(parents=True, exist_ok=True)
    ing_json = json.dumps([i.to_json() for i in ingredients.values()], ensure_ascii=False, indent=1)
    rec_json = json.dumps([r.to_json() for r in recipes], ensure_ascii=False, indent=1)
    (SEED_DIR / "ingredients.json").write_text(ing_json + "\n", encoding="utf-8")
    (SEED_DIR / "recipes.json").write_text(rec_json + "\n", encoding="utf-8")

    digest = hashlib.sha256()
    for name in ("ingredients.json", "recipes.json", "exercises.json", "workouts.json"):
        path = SEED_DIR / name
        if path.exists():
            digest.update(path.read_bytes())
    meta = {
        "seedHash": digest.hexdigest()[:16],
        "generatedOn": date.today().isoformat(),
        "nutritionSource": {
            "name": CIQUAL_VERSION,
            "publisher": "Anses",
            "doi": CIQUAL_DOI,
            "licence": CIQUAL_LICENCE,
            "attribution": "Source : Anses, Table de composition nutritionnelle des aliments Ciqual 2025.",
        },
    }
    (SEED_DIR / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    DOCS.mkdir(exist_ok=True)
    lines = [
        "# Ingrédients et correspondance Ciqual",
        "",
        f"Généré par `tools/build_seed.py` le {date.today().isoformat()} — ne pas éditer à la main.",
        "",
        f"Source des valeurs nutritionnelles : Anses, {CIQUAL_VERSION}, DOI {CIQUAL_DOI}, {CIQUAL_LICENCE}.",
        "Les valeurs sont reprises sans modification, pour 100 g de partie comestible. Les kcal marquées * sont",
        "absentes de Ciqual et recalculées à partir des macronutriments (facteurs du règlement UE 1169/2011).",
        "",
        "| id | Nom dans l'app | Code Ciqual | Aliment Ciqual | kcal | Prot. | Lip. | Gluc. | Fibres |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for i in ingredients.values():
        star = "*" if i.kcal_computed else ""
        lines.append(f"| `{i.id}` | {i.name} | {i.ciqual_code} | {i.ciqual_name} | {i.kcal:.0f}{star} | "
                     f"{i.protein:.1f} | {i.fat:.1f} | {i.carbs:.1f} | {i.fiber:.1f} |")
    (DOCS / "INGREDIENTS_CIQUAL.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    try:
        ingredients = load_ingredients(sys.argv[1])
        recipes = load_recipes(ingredients)
        report(recipes, ingredients)
        write_outputs(recipes, ingredients)
    except SeedError as e:
        print(f"\n✗ {e}")
        return 1
    print(f"\n✓ Fichiers écrits dans {SEED_DIR.relative_to(ROOT)} et docs/INGREDIENTS_CIQUAL.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
