package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.PurchaseUnit
import org.junit.Test
import kotlin.test.assertEquals

class ShoppingListBuilderTest {

    private val ingredients = listOf(
        ShoppingIngredient("tomate", "Tomate", Aisle.FRUITS_VEGETABLES, PurchaseUnit.PIECE, 120.0, "pièce"),
        ShoppingIngredient("carotte", "Carotte", Aisle.FRUITS_VEGETABLES, PurchaseUnit.G, null, null),
        ShoppingIngredient("oeuf", "Œuf", Aisle.DAIRY_EGGS, PurchaseUnit.PIECE, 55.0, "œuf"),
        ShoppingIngredient("lait", "Lait demi-écrémé", Aisle.DAIRY_EGGS, PurchaseUnit.ML, null, null),
        ShoppingIngredient("riz", "Riz basmati", Aisle.STARCHES, PurchaseUnit.G, null, null),
        ShoppingIngredient("cumin", "Cumin", Aisle.SPICES, PurchaseUnit.G, null, null),
    ).associateBy { it.id }

    @Test
    fun `quantities are summed per ingredient and grouped by aisle in store order`() {
        val sections = ShoppingListBuilder.build(
            listOf(
                ShoppingLine("riz", 70.0), ShoppingLine("carotte", 150.0), ShoppingLine("riz", 84.0),
                ShoppingLine("oeuf", 110.0), ShoppingLine("carotte", 230.0),
            ),
            ingredients,
        )
        assertEquals(listOf(Aisle.FRUITS_VEGETABLES, Aisle.DAIRY_EGGS, Aisle.STARCHES), sections.map { it.aisle })
        assertEquals(380.0, sections[0].items.single().grams, 1e-9)
        assertEquals("150 g", sections[2].items.single().quantityText) // 154 g arrondis à la dizaine
    }

    @Test
    fun `pieces are rounded up with a small tolerance and pluralised`() {
        val tomato = ingredients.getValue("tomate")
        assertEquals("1 pièce", ShoppingListBuilder.formatQuantity(126.0, tomato))
        assertEquals("3 pièces", ShoppingListBuilder.formatQuantity(300.0, tomato))
        assertEquals("2 œufs", ShoppingListBuilder.formatQuantity(110.0, ingredients.getValue("oeuf")))
    }

    @Test
    fun `masses are rounded sensibly and switch to kg or L`() {
        val carrot = ingredients.getValue("carotte")
        assertEquals("3 g", ShoppingListBuilder.formatQuantity(2.6, ingredients.getValue("cumin")))
        assertEquals("45 g", ShoppingListBuilder.formatQuantity(43.0, carrot))
        assertEquals("1,2 kg", ShoppingListBuilder.formatQuantity(1240.0, carrot))
        assertEquals("2 kg", ShoppingListBuilder.formatQuantity(1990.0, carrot))
        assertEquals("1,5 L", ShoppingListBuilder.formatQuantity(1500.0, ingredients.getValue("lait")))
    }

    @Test
    fun `plain text has one line per item with its quantity`() {
        val items = ShoppingListBuilder.build(
            listOf(ShoppingLine("tomate", 600.0), ShoppingLine("riz", 210.0)),
            ingredients,
        ).flatMap { it.items }
        assertEquals("Tomate — 5 pièces\nRiz basmati — 210 g", ShoppingListBuilder.toPlainText(items))
    }
}
