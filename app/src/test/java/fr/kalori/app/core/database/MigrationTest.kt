package fr.kalori.app.core.database

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Migrations réelles des anciennes bases (v1 : un plat par jour, v2 : sans budget ni organisation), sous Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `v1 to v2 keeps the profile and history and drops future planned meals`() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO recipe VALUES ('dahl', 'Dahl', 'PLANT', '[\"Cuire.\"]', 'BUNDLED', NULL, 1, 0, " +
                    "600.0, 25.0, 15.0, 80.0, 20.0, 0, 0, 4095)",
            )
            execSQL(
                "INSERT INTO user_profile VALUES (1, 'MALE', 1995, 3, 180.0, 70.0, 76.0, 'ACTIVE', 'GAIN', 0.25, " +
                    "2720, 112, 106, 324, 30, 0.4, 'METRIC', 0, 0)",
            )
            // Repas passé et mangé (conservé) ; repas futur seulement prévu (retiré pour être recomposé).
            execSQL("INSERT INTO meal_plan_entry VALUES (${today.minusDays(1).toEpochDay()}, 'dahl', 1.6, 1059.0, 48.0, 22.0, 147.0, 35.0, 'EATEN', '[]')")
            execSQL("INSERT INTO meal_plan_entry VALUES (${today.plusDays(2).toEpochDay()}, 'dahl', 1.6, 1059.0, 48.0, 22.0, 147.0, 35.0, 'PLANNED', '[]')")
            close()
        }

        // validateDroppedTables = true : le schéma obtenu doit être exactement celui attendu par Room en v2.
        val db = helper.runMigrationsAndValidate(DB, 2, true, migration1To2 { today })

        db.query("SELECT mealType FROM recipe WHERE id = 'dahl'").use {
            it.moveToFirst()
            assertEquals("MAIN", it.getString(0))
        }
        db.query("SELECT kcalTarget, enabledSlotsMask, snackMode FROM user_profile").use {
            it.moveToFirst()
            assertEquals(2720, it.getInt(0))
            assertEquals(7, it.getInt(1))
            assertEquals("AUTO", it.getString(2))
        }
        db.query("SELECT date, slot, status FROM meal_plan_entry").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(today.minusDays(1).toEpochDay(), it.getLong(0))
            assertEquals("NOON", it.getString(1))
            assertEquals("EATEN", it.getString(2))
        }
    }

    @Test
    fun `v2 to v3 adds budget and organisation with neutral defaults`() {
        helper.createDatabase(DB_V2, 2).apply {
            execSQL(
                "INSERT INTO recipe VALUES ('dahl', 'Dahl', 'PLANT', 'MAIN', '[\"Cuire.\"]', 'BUNDLED', NULL, 1, 0, " +
                    "600.0, 25.0, 15.0, 80.0, 20.0, 0, 0, 4095)",
            )
            execSQL(
                "INSERT INTO user_profile VALUES (1, 'MALE', 1995, 3, 180.0, 70.0, 76.0, 'ACTIVE', 'GAIN', 0.25, " +
                    "2720, 112, 106, 324, 30, 6, 'AUTO', 'METRIC', 0, 0)",
            )
            execSQL("INSERT INTO meal_plan_entry VALUES (${today.toEpochDay()}, 'NOON', 'dahl', 1.6, 1059.0, 48.0, 22.0, 147.0, 35.0, 'PLANNED', '[]')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V2, 3, true, MIGRATION_2_3)

        db.query("SELECT enabledSlotsMask, weeklyBudgetEur, cookingLevel, lunchLeftovers, weeklyBreakfast, weeklySnack FROM user_profile").use {
            it.moveToFirst()
            assertEquals(6, it.getInt(0))
            assertTrue(it.isNull(1))
            assertEquals("NORMAL", it.getString(2))
            assertEquals(0, it.getInt(3))
            assertEquals(0, it.getInt(4))
            assertEquals(0, it.getInt(5))
        }
        db.query("SELECT prepMinutes, batch FROM recipe").use {
            it.moveToFirst()
            assertEquals(30, it.getInt(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM meal_plan_entry").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM ingredient_price").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
        const val DB_V2 = "migration-test-v2.db"
    }
}
