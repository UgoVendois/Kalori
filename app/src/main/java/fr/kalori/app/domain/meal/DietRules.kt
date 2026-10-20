package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.DietFlag
import fr.kalori.app.domain.model.hasFlag
import fr.kalori.app.domain.model.toMask

/**
 * Compatibilité d'une recette (via le masque de ses ingrédients) avec un régime.
 * Mêmes règles que tools/build_seed.py (rapport de couverture) : les garder synchronisées.
 */
object DietRules {

    private val forbidden: Map<Diet, Int> = mapOf(
        Diet.VEGETARIAN to listOf(DietFlag.MEAT, DietFlag.PORK, DietFlag.FISH, DietFlag.SHELLFISH).toMask(),
        Diet.VEGAN to listOf(
            DietFlag.MEAT, DietFlag.PORK, DietFlag.FISH, DietFlag.SHELLFISH,
            DietFlag.DAIRY, DietFlag.EGG, DietFlag.HONEY,
        ).toMask(),
        Diet.PESCATARIAN to listOf(DietFlag.MEAT, DietFlag.PORK).toMask(),
        Diet.HALAL to listOf(DietFlag.PORK, DietFlag.ALCOHOL).toMask(),
        Diet.KOSHER to listOf(DietFlag.PORK, DietFlag.SHELLFISH).toMask(),
        Diet.NO_PORK to listOf(DietFlag.PORK).toMask(),
        Diet.NO_ALCOHOL to listOf(DietFlag.ALCOHOL).toMask(),
        Diet.LACTOSE_FREE to listOf(DietFlag.LACTOSE).toMask(),
    )

    fun isCompatible(diet: Diet, recipeDietMask: Int): Boolean {
        if (recipeDietMask and forbidden.getValue(diet) != 0) return false
        // Casher : pas de mélange viande et produits laitiers dans un même plat.
        if (diet == Diet.KOSHER && recipeDietMask.hasFlag(DietFlag.MEAT) && recipeDietMask.hasFlag(DietFlag.DAIRY)) {
            return false
        }
        return true
    }

    fun isCompatible(diets: Set<Diet>, recipeDietMask: Int): Boolean = diets.all { isCompatible(it, recipeDietMask) }

    /** Régimes dont le respect dépend aussi de la certification des produits, que l'app ne peut pas vérifier. */
    val requiresCertifiedProducts: Set<Diet> = setOf(Diet.HALAL, Diet.KOSHER)
}
