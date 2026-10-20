package fr.kalori.app.domain.nutrition

import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.Sex
import java.time.LocalDate
import kotlin.math.pow

/**
 * Estimations énergétiques. Ce sont des ESTIMATIONS de population, pas des mesures individuelles.
 * Sources : docs/SOURCES.md §5.
 */
object EnergyCalculator {

    /** Énergie approximative d'un kilogramme de variation de poids corporel. */
    const val KCAL_PER_KG = 7700.0

    /**
     * Métabolisme de base, équation de Mifflin-St Jeor (1990).
     * Pour [Sex.UNSPECIFIED], moyenne des équations homme (+5) et femme (−161).
     */
    fun basalMetabolicRate(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        val sexConstant = when (sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.UNSPECIFIED -> (5.0 - 161.0) / 2.0
        }
        return base + sexConstant
    }

    /** Dépense énergétique totale estimée (métabolisme de base × facteur d'activité). */
    fun totalDailyEnergy(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        activity: ActivityLevel,
    ): Double = basalMetabolicRate(sex, weightKg, heightCm, ageYears) * activity.factor

    fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / (heightCm / 100.0).pow(2)

    /** Poids correspondant à un IMC donné pour une taille donnée. */
    fun weightForBmi(bmi: Double, heightCm: Double): Double = bmi * (heightCm / 100.0).pow(2)

    /**
     * Âge en années révolues à partir de l'année et du mois de naissance (le jour n'est pas collecté).
     * Hypothèse prudente : pendant le mois anniversaire, l'anniversaire est considéré comme non atteint.
     * Une personne qui a 18 ans ce mois-ci est donc traitée comme mineure jusqu'au mois suivant.
     */
    fun ageYears(birthYear: Int, birthMonth: Int, today: LocalDate): Int {
        require(birthMonth in 1..12) { "Mois de naissance invalide" }
        val years = today.year - birthYear
        return if (today.monthValue <= birthMonth) years - 1 else years
    }
}
