package fr.kalori.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/**
 * v1 → v2 : journée complète (matin, midi, soir, collation) au lieu d'un seul plat.
 * - recipe : colonne mealType (les recettes existantes sont des plats, MAIN).
 * - user_profile : mealShare remplacé par les repas choisis (enabledSlotsMask) et le mode de collation.
 * - meal_plan_entry : clé (date, créneau). Les repas passés ou déjà pointés sont conservés au créneau
 *   « midi » ; les repas futurs encore « prévus » sont retirés pour être recomposés en journées complètes.
 */
fun migration1To2(today: () -> LocalDate = { LocalDate.now() }) = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recipe` ADD COLUMN `mealType` TEXT NOT NULL DEFAULT 'MAIN'")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_profile_new` (`id` INTEGER NOT NULL, `sex` TEXT NOT NULL, " +
                "`birthYear` INTEGER NOT NULL, `birthMonth` INTEGER NOT NULL, `heightCm` REAL NOT NULL, " +
                "`startWeightKg` REAL NOT NULL, `targetWeightKg` REAL NOT NULL, `activityLevel` TEXT NOT NULL, " +
                "`goalType` TEXT NOT NULL, `paceKgPerWeek` REAL NOT NULL, `kcalTarget` INTEGER NOT NULL, " +
                "`proteinG` INTEGER NOT NULL, `fatG` INTEGER NOT NULL, `carbsG` INTEGER NOT NULL, `fiberG` INTEGER NOT NULL, " +
                "`enabledSlotsMask` INTEGER NOT NULL, `snackMode` TEXT NOT NULL, `unitSystem` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `user_profile_new` (`id`, `sex`, `birthYear`, `birthMonth`, `heightCm`, `startWeightKg`, " +
                "`targetWeightKg`, `activityLevel`, `goalType`, `paceKgPerWeek`, `kcalTarget`, `proteinG`, `fatG`, `carbsG`, " +
                "`fiberG`, `enabledSlotsMask`, `snackMode`, `unitSystem`, `createdAt`, `updatedAt`) " +
                "SELECT `id`, `sex`, `birthYear`, `birthMonth`, `heightCm`, `startWeightKg`, `targetWeightKg`, `activityLevel`, " +
                "`goalType`, `paceKgPerWeek`, `kcalTarget`, `proteinG`, `fatG`, `carbsG`, `fiberG`, 7, 'AUTO', `unitSystem`, " +
                "`createdAt`, `updatedAt` FROM `user_profile`",
        )
        db.execSQL("DROP TABLE `user_profile`")
        db.execSQL("ALTER TABLE `user_profile_new` RENAME TO `user_profile`")

        db.execSQL("DELETE FROM `meal_plan_entry` WHERE `date` >= ? AND `status` = 'PLANNED'", arrayOf<Any>(today().toEpochDay()))
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meal_plan_entry_new` (`date` INTEGER NOT NULL, `slot` TEXT NOT NULL, " +
                "`recipeId` TEXT NOT NULL, `portionFactor` REAL NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, " +
                "`fat` REAL NOT NULL, `carbs` REAL NOT NULL, `fiber` REAL NOT NULL, `status` TEXT NOT NULL, " +
                "`rejectedRecipeIds` TEXT NOT NULL, PRIMARY KEY(`date`, `slot`), FOREIGN KEY(`recipeId`) " +
                "REFERENCES `recipe`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `meal_plan_entry_new` (`date`, `slot`, `recipeId`, `portionFactor`, `kcal`, `protein`, `fat`, " +
                "`carbs`, `fiber`, `status`, `rejectedRecipeIds`) SELECT `date`, 'NOON', `recipeId`, `portionFactor`, `kcal`, " +
                "`protein`, `fat`, `carbs`, `fiber`, `status`, `rejectedRecipeIds` FROM `meal_plan_entry`",
        )
        db.execSQL("DROP TABLE `meal_plan_entry`")
        db.execSQL("ALTER TABLE `meal_plan_entry_new` RENAME TO `meal_plan_entry`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plan_entry_recipeId` ON `meal_plan_entry` (`recipeId`)")
    }
}
