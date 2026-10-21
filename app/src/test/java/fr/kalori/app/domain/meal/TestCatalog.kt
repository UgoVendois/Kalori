package fr.kalori.app.domain.meal

import fr.kalori.app.core.database.entity.RecipeWithIngredients
import fr.kalori.app.data.meal.PriceBook
import fr.kalori.app.data.meal.toCandidate
import fr.kalori.app.data.seed.SeedMapper
import java.io.File
import java.time.Instant

/** Catalogue réel embarqué dans l'app, avec les prix estimés, comme le voit le planificateur. */
object TestCatalog {
    val candidates: List<MealCandidate> by lazy {
        val bundle = SeedMapper.parse { File("src/main/assets/seed/$it").readText() }
        val ingredients = bundle.ingredients.map(SeedMapper::ingredient)
        val facts = ingredients.associate { it.id to SeedMapper.facts(it) }
        val prices = PriceBook(ingredients, emptyList())
        bundle.recipes.map { seed ->
            val (recipe, lines) = SeedMapper.recipe(seed, facts, Instant.EPOCH)
            RecipeWithIngredients(recipe, lines).toCandidate(prices.cost(lines))
        }
    }
}
