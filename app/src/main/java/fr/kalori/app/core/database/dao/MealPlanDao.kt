package fr.kalori.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.kalori.app.core.database.entity.MealPlanEntryEntity
import fr.kalori.app.core.database.entity.ShoppingCheckEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plan_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<MealPlanEntryEntity>

    @Query("SELECT * FROM meal_plan_entry WHERE date < :before ORDER BY date DESC")
    fun observeHistory(before: LocalDate): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry ORDER BY date")
    fun observeAll(): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entry WHERE date = :date")
    suspend fun get(date: LocalDate): MealPlanEntryEntity?

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
