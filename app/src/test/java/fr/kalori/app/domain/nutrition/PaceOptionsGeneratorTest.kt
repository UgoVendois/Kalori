package fr.kalori.app.domain.nutrition

import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.Sex
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaceOptionsGeneratorTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun profile(
        sex: Sex = Sex.MALE,
        age: Int = 30,
        height: Double = 180.0,
        weight: Double = 90.0,
        target: Double = 80.0,
        activity: ActivityLevel = ActivityLevel.MODERATE,
        pregnant: Boolean = false,
    ) = BodyProfile(sex, age, height, weight, target, activity, pregnant)

    private fun available(p: BodyProfile) = assertIs<PacePlan.Available>(PaceOptionsGenerator.generate(p, today))

    // ── Refus ──

    @Test
    fun `minors are redirected to a professional`() {
        val plan = PaceOptionsGenerator.generate(profile(age = 17), today)
        assertEquals(PacePlan.Blocked(BlockReason.MINOR), plan)
    }

    @Test
    fun `pregnancy or breastfeeding blocks any target`() {
        val plan = PaceOptionsGenerator.generate(profile(sex = Sex.FEMALE, pregnant = true), today)
        assertEquals(PacePlan.Blocked(BlockReason.PREGNANCY_OR_BREASTFEEDING), plan)
    }

    @Test
    fun `target weight with BMI under 18,5 is refused and the minimum is given`() {
        // 180 cm : IMC 18,5 = 59,94 kg
        val plan = assertIs<PacePlan.Blocked>(PaceOptionsGenerator.generate(profile(weight = 70.0, target = 58.0), today))
        assertEquals(BlockReason.TARGET_BMI_TOO_LOW, plan.reason)
        assertEquals(60.0, plan.minTargetWeightKg)
    }

    @Test
    fun `implausible input is rejected`() {
        assertEquals(PacePlan.Blocked(BlockReason.INVALID_INPUT), PaceOptionsGenerator.generate(profile(height = 90.0), today))
        assertEquals(PacePlan.Blocked(BlockReason.INVALID_INPUT), PaceOptionsGenerator.generate(profile(weight = 500.0), today))
    }

    // ── Perte ──

    @Test
    fun `loss offers three paces when all are safe`() {
        val plan = available(profile(weight = 90.0, target = 80.0))
        assertEquals(GoalType.LOSE, plan.goal)
        assertEquals(listOf(0.25, 0.5, 0.75), plan.options.map { it.paceKgPerWeek })
        // Plus le rythme est soutenu, plus l'objectif calorique est bas.
        val kcal = plan.options.map { it.targets.kcal }
        assertEquals(kcal.sortedDescending(), kcal)
    }

    @Test
    fun `loss above 1 percent of body weight per week is hidden`() {
        // 60 kg → plafond 0,6 kg/sem : 0,75 est masqué.
        val plan = available(profile(sex = Sex.FEMALE, height = 165.0, weight = 60.0, target = 56.0, activity = ActivityLevel.ACTIVE))
        assertEquals(listOf(0.25, 0.5), plan.options.map { it.paceKgPerWeek })
        assertEquals(listOf(HiddenPace(0.75, HiddenReason.ABOVE_WEEKLY_CAP)), plan.hidden)
    }

    @Test
    fun `no option ever goes below the calorie floor`() {
        // Femme 155 cm, 62 kg, 45 ans, sédentaire : dépense ≈ 1 440 kcal.
        val plan = PaceOptionsGenerator.generate(
            profile(sex = Sex.FEMALE, age = 45, height = 155.0, weight = 62.0, target = 55.0, activity = ActivityLevel.SEDENTARY),
            today,
        )
        assertEquals(PacePlan.Blocked(BlockReason.NO_SAFE_LOSS_PACE), plan)
    }

    @Test
    fun `floor is respected for every sex across many profiles`() {
        for (sex in Sex.entries) for (weight in 50..140 step 5) for (activity in ActivityLevel.entries) {
            val plan = PaceOptionsGenerator.generate(
                profile(sex = sex, height = 170.0, weight = weight.toDouble(), target = 54.0, activity = activity),
                today,
            )
            if (plan is PacePlan.Available) {
                plan.options.forEach { option ->
                    assertTrue(option.targets.kcal >= PaceOptionsGenerator.calorieFloor(sex), "$sex $weight $activity")
                    assertTrue(option.paceKgPerWeek <= weight * 0.01 + 1e-9)
                }
            }
        }
    }

    @Test
    fun `duration and arrival date are estimated`() {
        val plan = available(profile(weight = 90.0, target = 80.0))
        val half = plan.options.first { it.paceKgPerWeek == 0.5 }
        assertEquals(20, half.weeksToTarget)
        assertEquals(today.plusWeeks(20), half.estimatedArrival)
        val quarter = plan.options.first { it.paceKgPerWeek == 0.25 }
        assertEquals(40, quarter.weeksToTarget)
    }

    @Test
    fun `daily deficit matches the pace`() {
        val plan = available(profile(weight = 90.0, target = 80.0))
        val half = plan.options.first { it.paceKgPerWeek == 0.5 }
        // 0,5 kg × 7 700 / 7 = 550 kcal/j (arrondis à la dizaine)
        assertTrue(kotlin.math.abs(plan.maintenanceKcal - half.targets.kcal - 550) <= 10)
    }

    // ── Prise et maintien ──

    @Test
    fun `gain above 0,5 percent of body weight per week is hidden`() {
        // 60 kg → plafond 0,3 kg/sem : 0,5 est masqué.
        val plan = available(profile(weight = 60.0, target = 66.0, height = 175.0))
        assertEquals(GoalType.GAIN, plan.goal)
        assertEquals(listOf(0.1, 0.25), plan.options.map { it.paceKgPerWeek })
        assertEquals(listOf(HiddenPace(0.5, HiddenReason.ABOVE_WEEKLY_CAP)), plan.hidden)
        assertTrue(plan.options.all { it.targets.kcal > plan.maintenanceKcal })
    }

    @Test
    fun `close target means a single maintenance option`() {
        val plan = available(profile(weight = 75.0, target = 75.5))
        assertEquals(GoalType.MAINTAIN, plan.goal)
        val only = plan.options.single()
        assertEquals(0.0, only.paceKgPerWeek)
        assertEquals(plan.maintenanceKcal, only.targets.kcal)
        assertNull(only.weeksToTarget)
    }

    @Test
    fun `maintenance below the floor is raised to the floor with a warning`() {
        // Femme 150 cm, 45 kg, 80 ans, sédentaire : dépense estimée ≈ 990 kcal.
        val plan = available(
            profile(sex = Sex.FEMALE, age = 80, height = 150.0, weight = 45.0, target = 45.0, activity = ActivityLevel.SEDENTARY),
        )
        assertEquals(GoalType.MAINTAIN, plan.goal)
        assertEquals(1200, plan.options.single().targets.kcal)
        assertTrue(PlanWarning.LOW_ESTIMATED_NEEDS in plan.warnings)
    }

    @Test
    fun `seniors get a muscle loss warning`() {
        val plan = available(profile(age = 72, weight = 85.0, target = 80.0))
        assertTrue(PlanWarning.SENIOR_MUSCLE_LOSS in plan.warnings)
        assertTrue(available(profile(age = 40)).warnings.isEmpty())
    }
}
