package fr.kalori.app.data.backup

import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.database.entity.UserRestrictionEntity
import fr.kalori.app.core.database.entity.WeightEntryEntity
import fr.kalori.app.core.database.entity.WorkoutSessionEntity
import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.MealSlot
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
            unitSystem = UnitSystem.METRIC, createdAt = now, updatedAt = now,
        ),
        weights = listOf(WeightEntryEntity(1, day, 71.4)),
        extraIntakes = emptyList(),
        restrictions = listOf(UserRestrictionEntity(RestrictionKind.ALLERGEN, "GLUTEN")),
        aiRecipes = emptyList(),
        aiRecipeIngredients = emptyList(),
        mealPlan = listOf(
            MealPlanEntryEntity(day, MealSlot.EVENING, "dahl_lentilles_corail", 1.2, 700.0, 30.0, 20.0, 90.0, 20.0, MealStatus.EATEN, listOf("chili_sin_carne")),
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
    fun `format 1 backups (one meal a day, meal share) are still readable`() {
        val v1 = """
            {"format":1,"exportedAt":"2026-10-21T08:30:00Z","appVersion":"0.1.0",
             "profile":{"id":1,"sex":"MALE","birthYear":1995,"birthMonth":3,"heightCm":180.0,"startWeightKg":70.0,
               "targetWeightKg":76.0,"activityLevel":"ACTIVE","goalType":"GAIN","paceKgPerWeek":0.25,"kcalTarget":2720,
               "proteinG":112,"fatG":106,"carbsG":324,"fiberG":30,"mealShare":0.4,"unitSystem":"METRIC",
               "createdAt":"2026-10-20T08:00:00Z","updatedAt":"2026-10-20T08:00:00Z"},
             "weights":[],"extraIntakes":[],"restrictions":[],"aiRecipes":[],"aiRecipeIngredients":[],
             "mealPlan":[{"date":"2026-10-20","recipeId":"dahl_lentilles_corail","portionFactor":1.6,"kcal":1059.0,
               "protein":48.6,"fat":22.6,"carbs":147.5,"fiber":35.8,"status":"EATEN","rejectedRecipeIds":[]}],
             "workoutTemplates":[],"workoutTemplateExercises":[],"sessions":[],"sessionSets":[]}
        """.trimIndent()
        val parsed = json.decodeFromString(KaloriBackup.serializer(), v1)
        assertEquals(MealSlot.NOON, parsed.mealPlan.single().slot)
        assertEquals(0b111, parsed.profile!!.enabledSlotsMask)
    }

    @Test
    fun `dates are readable ISO strings`() {
        val text = json.encodeToString(KaloriBackup.serializer(), backup)
        assertTrue("\"2026-10-21\"" in text)
        assertTrue("\"2026-10-21T08:30:00Z\"" in text)
        assertTrue("\"ALLERGEN\"" in text)
    }
}
