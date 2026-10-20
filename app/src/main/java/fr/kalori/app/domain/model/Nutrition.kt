package fr.kalori.app.domain.model

/** Apports nutritionnels (kcal et grammes). Selon le contexte : pour 100 g, par portion ou par jour. */
data class Nutrition(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double,
) {
    operator fun plus(other: Nutrition) = Nutrition(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        fat = fat + other.fat,
        carbs = carbs + other.carbs,
        fiber = fiber + other.fiber,
    )

    operator fun times(factor: Double) = Nutrition(
        kcal = kcal * factor,
        protein = protein * factor,
        fat = fat * factor,
        carbs = carbs * factor,
        fiber = fiber * factor,
    )

    companion object {
        val ZERO = Nutrition(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
