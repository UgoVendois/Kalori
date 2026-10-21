package fr.kalori.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.MealPlanEntryWithRecipe
import fr.kalori.app.core.database.entity.ShoppingCheckEntity
import fr.kalori.app.domain.model.MealSlot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plan_entry WHERE date BETWEEN :from AND :to ORDER BY date, slot")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry WHERE date BETWEEN :from AND :to ORDER BY date, slot")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<MealPlanEntryEntity>

    @Transaction
    @Query("SELECT * FROM meal_plan_entry WHERE date BETWEEN :from AND :to ORDER BY date, slot")
    fun observeRangeWithRecipe(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntryWithRecipe>>

    @Transaction
    @Query("SELECT * FROM meal_plan_entry WHERE date < :before ORDER BY date DESC")
    fun observeHistoryWithRecipe(before: LocalDate): Flow<List<MealPlanEntryWithRecipe>>

    @Transaction
    @Query("SELECT * FROM meal_plan_entry WHERE date = :date AND slot = :slot")
    fun observeEntryWithRecipe(date: LocalDate, slot: MealSlot): Flow<MealPlanEntryWithRecipe?>

    @Query("SELECT * FROM meal_plan_entry ORDER BY date")
    fun observeAll(): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry ORDER BY date")
    suspend fun getAll(): List<MealPlanEntryEntity>

    @Query("DELETE FROM meal_plan_entry")
    suspend fun deleteAll()

    @Query("DELETE FROM shopping_check")
    suspend fun deleteAllChecks()

    @Query("SELECT * FROM meal_plan_entry WHERE date = :date AND slot = :slot")
    suspend fun get(date: LocalDate, slot: MealSlot): MealPlanEntryEntity?

    @Query("DELETE FROM meal_plan_entry WHERE date >= :from AND status = 'PLANNED' AND slot NOT IN (:keep)")
    suspend fun deletePlannedSlotsNotIn(from: LocalDate, keep: Collection<MealSlot>)

    @Upsert
    suspend fun upsert(entry: MealPlanEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<MealPlanEntryEntity>)

    @Query("SELECT * FROM shopping_check WHERE weekStart = :weekStart")
    fun observeChecks(weekStart: LocalDate): Flow<List<ShoppingCheckEntity>>

    @Upsert
    suspend fun upsertCheck(check: ShoppingCheckEntity)

    @Query("DELETE FROM shopping_check WHERE weekStart < :before")
    suspend fun deleteChecksBefore(before: LocalDate)
}
