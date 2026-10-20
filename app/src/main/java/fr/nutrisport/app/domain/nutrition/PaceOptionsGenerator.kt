package fr.nutrisport.app.domain.nutrition

import fr.nutrisport.app.domain.model.ActivityLevel
import fr.nutrisport.app.domain.model.GoalType
import fr.nutrisport.app.domain.model.Sex
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Données saisies par l'utilisateur, en unités métriques. */
data class BodyProfile(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double,
    val activity: ActivityLevel,
    /** Réponse à la question oui/non de l'onboarding : utilisée pour le calcul, jamais stockée. */
    val pregnantOrBreastfeeding: Boolean,
)

sealed interface PacePlan {
    /** Aucun objectif proposé : l'app affiche l'explication et redirige vers un professionnel. */
    data class Blocked(val reason: BlockReason, val minTargetWeightKg: Double? = null) : PacePlan

    data class Available(
        val goal: GoalType,
        val maintenanceKcal: Int,
        /** Options proposées (1 pour le maintien, jusqu'à 3 sinon), du plus doux au plus soutenu. */
        val options: List<PaceOption>,
        /** Rythmes standards non proposés, avec la raison (affichée pour la transparence). */
        val hidden: List<HiddenPace>,
        val warnings: Set<PlanWarning>,
    ) : PacePlan
}

enum class BlockReason {
    INVALID_INPUT,
    /** Moins de 18 ans : pas de régime, redirection vers un médecin ou un diététicien. */
    MINOR,
    /** Grossesse ou allaitement : besoins spécifiques, suivi médical nécessaire. */
    PREGNANCY_OR_BREASTFEEDING,
    /** Le poids cible correspond à un IMC inférieur à 18,5 (seuil OMS de maigreur). */
    TARGET_BMI_TOO_LOW,
    /** Aucun rythme de perte ne respecte à la fois le plafond hebdomadaire et le plancher calorique. */
    NO_SAFE_LOSS_PACE,
}

data class PaceOption(
    /** kg par semaine, toujours positif ; 0 pour le maintien. */
    val paceKgPerWeek: Double,
    val targets: NutritionTargets,
    /** null pour le maintien. */
    val weeksToTarget: Int?,
    val estimatedArrival: LocalDate?,
)

data class HiddenPace(val paceKgPerWeek: Double, val reason: HiddenReason)

enum class HiddenReason {
    /** Perte > 1 % du poids corporel par semaine, ou prise > 0,5 %. */
    ABOVE_WEEKLY_CAP,
    /** L'objectif calorique passerait sous le plancher de sécurité. */
    BELOW_CALORIE_FLOOR,
}

enum class PlanWarning {
    /** 70 ans et plus : vigilance sur la fonte musculaire et la dénutrition (HAS). */
    SENIOR_MUSCLE_LOSS,
    /**
     * Dépense estimée inférieure au plancher de sécurité : l'objectif est relevé au plancher
     * et l'utilisateur est invité à faire évaluer ses besoins par un professionnel.
     */
    LOW_ESTIMATED_NEEDS,
}

/**
 * Propose les rythmes compatibles avec le profil. L'app ne choisit jamais à la place de l'utilisateur :
 * elle filtre les options dangereuses et affiche les autres avec leurs conséquences.
 * Sources et justification des seuils : docs/SOURCES.md §5.
 */
object PaceOptionsGenerator {

    val LOSS_PACES = listOf(0.25, 0.5, 0.75)
    val GAIN_PACES = listOf(0.1, 0.25, 0.5)

    const val ADULT_AGE = 18
    const val SENIOR_AGE = 70
    const val MIN_TARGET_BMI = 18.5
    const val MAX_WEEKLY_LOSS_SHARE = 0.01
    const val MAX_WEEKLY_GAIN_SHARE = 0.005
    /** En dessous de cet écart (kg) entre poids actuel et poids cible, l'objectif est le maintien. */
    const val MAINTAIN_TOLERANCE_KG = 1.0

    fun calorieFloor(sex: Sex): Int = when (sex) {
        Sex.FEMALE -> 1200
        Sex.MALE -> 1500
        Sex.UNSPECIFIED -> 1350
    }

    fun generate(profile: BodyProfile, today: LocalDate): PacePlan {
        if (!profile.isPlausible()) return PacePlan.Blocked(BlockReason.INVALID_INPUT)
        if (profile.ageYears < ADULT_AGE) return PacePlan.Blocked(BlockReason.MINOR)
        if (profile.pregnantOrBreastfeeding) return PacePlan.Blocked(BlockReason.PREGNANCY_OR_BREASTFEEDING)

        val minTarget = EnergyCalculator.weightForBmi(MIN_TARGET_BMI, profile.heightCm)
        if (EnergyCalculator.bmi(profile.targetWeightKg, profile.heightCm) < MIN_TARGET_BMI) {
            return PacePlan.Blocked(BlockReason.TARGET_BMI_TOO_LOW, minTargetWeightKg = ceilTenth(minTarget))
        }

        val tdee = EnergyCalculator.totalDailyEnergy(
            profile.sex, profile.weightKg, profile.heightCm, profile.ageYears, profile.activity,
        )
        val floor = calorieFloor(profile.sex)
        val maintenanceKcal = roundToTen(tdee)
        val warnings = buildSet {
            if (profile.ageYears >= SENIOR_AGE) add(PlanWarning.SENIOR_MUSCLE_LOSS)
            if (maintenanceKcal < floor) add(PlanWarning.LOW_ESTIMATED_NEEDS)
        }
        val difference = profile.targetWeightKg - profile.weightKg
        val goal = when {
            abs(difference) < MAINTAIN_TOLERANCE_KG -> GoalType.MAINTAIN
            difference < 0 -> GoalType.LOSE
            else -> GoalType.GAIN
        }

        if (goal == GoalType.MAINTAIN) {
            val option = PaceOption(
                paceKgPerWeek = 0.0,
                targets = MacroCalculator.targets(maxOf(maintenanceKcal, floor), profile.weightKg, goal),
                weeksToTarget = null,
                estimatedArrival = null,
            )
            return PacePlan.Available(goal, maintenanceKcal, listOf(option), emptyList(), warnings)
        }

        val paces = if (goal == GoalType.LOSE) LOSS_PACES else GAIN_PACES
        val weeklyCap = profile.weightKg * if (goal == GoalType.LOSE) MAX_WEEKLY_LOSS_SHARE else MAX_WEEKLY_GAIN_SHARE
        val options = mutableListOf<PaceOption>()
        val hidden = mutableListOf<HiddenPace>()

        for (pace in paces) {
            val dailyDelta = pace * EnergyCalculator.KCAL_PER_KG / 7.0
            val kcal = roundToTen(if (goal == GoalType.LOSE) tdee - dailyDelta else tdee + dailyDelta)
            when {
                pace > weeklyCap + 1e-9 -> hidden += HiddenPace(pace, HiddenReason.ABOVE_WEEKLY_CAP)
                goal == GoalType.LOSE && kcal < floor -> hidden += HiddenPace(pace, HiddenReason.BELOW_CALORIE_FLOOR)
                else -> {
                    val weeks = ceil(abs(difference) / pace).toInt()
                    options += PaceOption(
                        paceKgPerWeek = pace,
                        // En prise, la cible ne peut être sous le plancher que si la dépense l'est déjà.
                        targets = MacroCalculator.targets(maxOf(kcal, floor), profile.weightKg, goal),
                        weeksToTarget = weeks,
                        estimatedArrival = today.plusWeeks(weeks.toLong()),
                    )
                }
            }
        }

        if (options.isEmpty() && goal == GoalType.LOSE) {
            return PacePlan.Blocked(BlockReason.NO_SAFE_LOSS_PACE)
        }
        return PacePlan.Available(goal, maintenanceKcal, options, hidden, warnings)
    }

    private fun BodyProfile.isPlausible(): Boolean =
        ageYears in 0..110 &&
            heightCm in 120.0..230.0 &&
            weightKg in 30.0..300.0 &&
            targetWeightKg in 30.0..300.0

    private fun roundToTen(kcal: Double): Int = (kcal / 10.0).roundToInt() * 10

    private fun ceilTenth(value: Double): Double = ceil(value * 10.0) / 10.0
}
