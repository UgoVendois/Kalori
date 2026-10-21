package fr.kalori.app.domain.model

/** Les 14 allergènes à déclaration obligatoire — règlement (UE) n° 1169/2011, annexe II. */
enum class Allergen(override val bit: Int, val label: String, val shortLabel: String) : BitFlag {
    GLUTEN(0, "Céréales contenant du gluten", "Gluten"),
    CRUSTACEANS(1, "Crustacés", "Crustacés"),
    EGGS(2, "Œufs", "Œufs"),
    FISH(3, "Poissons", "Poissons"),
    PEANUTS(4, "Arachides", "Arachides"),
    SOY(5, "Soja", "Soja"),
    MILK(6, "Lait", "Lait"),
    NUTS(7, "Fruits à coque", "Fruits à coque"),
    CELERY(8, "Céleri", "Céleri"),
    MUSTARD(9, "Moutarde", "Moutarde"),
    SESAME(10, "Graines de sésame", "Sésame"),
    SULPHITES(11, "Anhydride sulfureux et sulfites", "Sulfites"),
    LUPIN(12, "Lupin", "Lupin"),
    MOLLUSCS(13, "Mollusques", "Mollusques"),
}

/** Caractéristiques d'un ingrédient utilisées par les règles de régime. */
enum class DietFlag(override val bit: Int) : BitFlag {
    MEAT(0),
    PORK(1),
    FISH(2),
    SHELLFISH(3),
    DAIRY(4),
    EGG(5),
    HONEY(6),
    ALCOHOL(7),
    /** Produit laitier dont la teneur en lactose (Ciqual) dépasse 0,5 g/100 g. */
    LACTOSE(8),
}

/** Régimes alimentaires sélectionnables par l'utilisateur (les règles sont dans DietRules). */
enum class Diet(val label: String) {
    VEGETARIAN("Végétarien"),
    VEGAN("Végétalien (vegan)"),
    PESCATARIAN("Pescétarien"),
    HALAL("Halal"),
    KOSHER("Casher"),
    NO_PORK("Sans porc"),
    NO_ALCOHOL("Sans alcool"),
    LACTOSE_FREE("Sans lactose"),
}

/** Rayons de la liste de courses, dans l'ordre d'un parcours de magasin classique. */
enum class Aisle(val label: String) {
    FRUITS_VEGETABLES("Fruits et légumes"),
    BAKERY("Boulangerie"),
    BUTCHER("Boucherie, volaille"),
    FISHMONGER("Poissonnerie"),
    DAIRY_EGGS("Crèmerie et œufs"),
    PLANT_PROTEINS("Produits végétaux"),
    STARCHES("Pâtes, riz et céréales"),
    CANNED_LEGUMES("Conserves et légumineuses"),
    NUTS_SEEDS("Fruits secs et graines"),
    BREAKFAST_SWEET("Petit-déjeuner, sucré"),
    PANTRY("Huiles, sauces et condiments"),
    SPICES("Épices et herbes séchées"),
    DRINKS("Vins et boissons"),
}

enum class PurchaseUnit { G, ML, PIECE }

/** Source principale de protéines d'un plat : sert à varier les repas d'un jour à l'autre. */
enum class MainProtein(val label: String) {
    POULTRY("Volaille"),
    RED_MEAT("Viande rouge"),
    PORK("Porc"),
    FISH("Poisson"),
    SEAFOOD("Fruits de mer"),
    EGG("Œufs"),
    DAIRY("Produits laitiers"),
    PLANT("Végétal"),
}

enum class RecipeOrigin { BUNDLED, AI }

/** Type de recette : plat (midi ou soir), petit-déjeuner ou collation. */
enum class MealType { BREAKFAST, MAIN, SNACK }

/**
 * Créneaux d'une journée. [weight] : part de l'objectif journalier, renormalisée sur les créneaux actifs.
 * Libellés « Matin / Midi / Soir » plutôt que déjeuner/dîner, dont le sens varie selon les régions.
 */
enum class MealSlot(override val bit: Int, val label: String, val mealType: MealType, val weight: Int) : BitFlag {
    MORNING(0, "Matin", MealType.BREAKFAST, 25),
    NOON(1, "Midi", MealType.MAIN, 35),
    EVENING(2, "Soir", MealType.MAIN, 30),
    SNACK(3, "Collation", MealType.SNACK, 10),
    ;

    companion object {
        /** Repas principaux que l'utilisateur peut activer ou non (la collation a son propre réglage). */
        val mains = listOf(MORNING, NOON, EVENING)
    }
}

/** Collation : proposée automatiquement si besoin, toujours, ou jamais. */
enum class SnackMode(val label: String) {
    AUTO("Si besoin"),
    ALWAYS("Toujours"),
    NEVER("Jamais"),
}

/** Niveau de cuisine souhaité : difficulté maximale des recettes proposées. */
enum class CookingLevel(val label: String, val maxDifficulty: Int, val description: String) {
    SIMPLE("Simple", 1, "Rapide, peu d'ingrédients"),
    NORMAL("Normal", 2, "Un peu plus de préparation"),
    ELABORATE("Élaboré", 3, "Plats mijotés et recettes travaillées"),
}

enum class MealStatus { PLANNED, EATEN, SKIPPED }

enum class RestrictionKind { ALLERGEN, DIET, LIKE, DISLIKE }
