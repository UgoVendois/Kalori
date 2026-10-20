package fr.kalori.app.data.backup

import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.database.entity.UserRestrictionEntity
import fr.kalori.app.core.database.entity.WeightEntryEntity
import fr.kalori.app.core.database.entity.WorkoutSessionEntity
import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.RestrictionKind
import fr.kalori.app.domain.model.Sex
import fr.kalori.app.domain.model.UnitSystem
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupFormatTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val now = Instant.parse("2026-10-21T08:30:00Z")
    private val day = LocalDate.of(2026, 10, 21)

    private val backup = KaloriBackup(
        format = 1,
        exportedAt = now,
        appVersion = "0.1.0",
        profile = UserProfileEntity(
            sex = Sex.FEMALE, birthYear = 1990, birthMonth = 4, heightCm = 168.0, startWeightKg = 72.0,
            targetWeightKg = 66.0, activityLevel = ActivityLevel.MODERATE, goalType = GoalType.LOSE,
            paceKgPerWeek = 0.5, kcalTarget = 1780, proteinG = 115, fatG = 69, carbsG = 178, fiberG = 30,
            mealShare = 0.4, unitSystem = UnitSystem.METRIC, createdAt = now, updatedAt = now,
        ),
        weights = listOf(WeightEntryEntity(1, day, 71.4)),
        extraIntakes = emptyList(),
        restrictions = listOf(UserRestrictionEntity(RestrictionKind.ALLERGEN, "GLUTEN")),
        aiRecipes = emptyList(),
        aiRecipeIngredients = emptyList(),
        mealPlan = listOf(
            MealPlanEntryEntity(day, "dahl_lentilles_corail", 1.2, 700.0, 30.0, 20.0, 90.0, 20.0, MealStatus.EATEN, listOf("chili_sin_carne")),
        ),
        workoutTemplates = emptyList(),
        workoutTemplateExercises = emptyList(),
        sessions = listOf(WorkoutSessionEntity(3, null, "Street HIIT", now, now.plusSeconds(1800), true)),
        sessionSets = emptyList(),
    )

    @Test
    fun `backup survives a JSON round trip`() {
        val text = json.encodeToString(KaloriBackup.serializer(), backup)
        assertEquals(backup, json.decodeFromString(KaloriBackup.serializer(), text))
    }

    @Test
    fun `dates are readable ISO strings`() {
        val text = json.encodeToString(KaloriBackup.serializer(), backup)
        assertTrue("\"2026-10-21\"" in text)
        assertTrue("\"2026-10-21T08:30:00Z\"" in text)
        assertTrue("\"ALLERGEN\"" in text)
    }
}
