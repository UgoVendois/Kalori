package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.PurchaseUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

data class ShoppingIngredient(
    val id: String,
    val name: String,
    val aisle: Aisle,
    val unit: PurchaseUnit,
    val pieceGrams: Double?,
    val pieceLabel: String?,
)

/** Une quantité d'ingrédient à acheter (déjà multipliée par le facteur de portion). */
data class ShoppingLine(val ingredientId: String, val grams: Double)

data class ShoppingItem(
    val ingredientId: String,
    val name: String,
    val aisle: Aisle,
    val grams: Double,
    val quantityText: String,
)

data class ShoppingSection(val aisle: Aisle, val items: List<ShoppingItem>)

object ShoppingListBuilder {

    /** Cumule les quantités par ingrédient et regroupe par rayon (ordre de [Aisle]), articles triés par nom. */
    fun build(lines: List<ShoppingLine>, ingredients: Map<String, ShoppingIngredient>): List<ShoppingSection> =
        lines.groupBy { it.ingredientId }
            .map { (id, group) ->
                val ingredient = requireNotNull(ingredients[id]) { "Ingrédient inconnu : $id" }
                val grams = group.sumOf { it.grams }
                ShoppingItem(id, ingredient.name, ingredient.aisle, grams, formatQuantity(grams, ingredient))
            }
            .groupBy { it.aisle }
            .toSortedMap(compareBy { it.ordinal })
            .map { (aisle, items) -> ShoppingSection(aisle, items.sortedBy { it.name }) }

    /** Texte brut pour le presse-papiers : une ligne par article avec sa quantité. */
    fun toPlainText(items: List<ShoppingItem>): String =
        items.joinToString("\n") { "${it.name} — ${it.quantityText}" }

    fun formatQuantity(grams: Double, ingredient: ShoppingIngredient): String = when (ingredient.unit) {
        PurchaseUnit.PIECE -> {
            val pieceGrams = requireNotNull(ingredient.pieceGrams)
            // Tolérance de 10 % : 1,05 pièce s'achète comme 1 pièce.
            val count = ceil(grams / pieceGrams - 0.1).toInt().coerceAtLeast(1)
            "$count ${plural(ingredient.pieceLabel ?: "pièce", count)}"
        }
        PurchaseUnit.G -> formatMass(grams, "g", "kg")
        PurchaseUnit.ML -> formatMass(grams, "ml", "L")
    }

    private fun formatMass(value: Double, small: String, large: String): String {
        if (value >= 1000) return "${frenchDecimal(value / 1000)} $large"
        val rounded = when {
            value < 20 -> value.roundToInt().coerceAtLeast(1)
            value < 100 -> (value / 5).roundToInt() * 5
            else -> (value / 10).roundToInt() * 10
        }
        return "$rounded $small"
    }

    private fun frenchDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
    }

    private fun plural(label: String, count: Int): String =
        if (count > 1 && !label.endsWith("s") && !label.endsWith("x")) "${label}s" else label
}
