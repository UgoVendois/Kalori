package fr.nutrisport.app.domain.model

/** Les 14 allergènes à déclaration obligatoire — règlement (UE) n° 1169/2011, annexe II. */
enum class Allergen(override val bit: Int, val label: String) : BitFlag {
    GLUTEN(0, "Céréales contenant du gluten"),
    CRUSTACEANS(1, "Crustacés"),
    EGGS(2, "Œufs"),
    FISH(3, "Poissons"),
    PEANUTS(4, "Arachides"),
    SOY(5, "Soja"),
    MILK(6, "Lait"),
    NUTS(7, "Fruits à coque"),
    CELERY(8, "Céleri"),
    MUSTARD(9, "Moutarde"),
    SESAME(10, "Graines de sésame"),
    SULPHITES(11, "Anhydride sulfureux et sulfites"),
    LUPIN(12, "Lupin"),
    MOLLUSCS(13, "Mollusques"),
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

enum class MealStatus { PLANNED, EATEN, SKIPPED }

enum class RestrictionKind { ALLERGEN, DIET, LIKE, DISLIKE }
