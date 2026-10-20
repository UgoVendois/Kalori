package fr.kalori.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.kalori.app.core.database.entity.DailyIntakeEntity
import fr.kalori.app.core.database.entity.UserProfileEntity
import fr.kalori.app.core.database.entity.UserRestrictionEntity
import fr.kalori.app.core.database.entity.WeightEntryEntity
import fr.kalori.app.domain.model.RestrictionKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface TrackingDao {
    @Query("SELECT * FROM weight_entry ORDER BY date")
    fun observeWeights(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry ORDER BY date DESC LIMIT 1")
    suspend fun latestWeight(): WeightEntryEntity?

    /** Une seule pesée par jour : une nouvelle saisie remplace celle du même jour. */
    @Query("INSERT OR REPLACE INTO weight_entry (id, date, weightKg) VALUES ((SELECT id FROM weight_entry WHERE date = :date), :date, :weightKg)")
    suspend fun setWeight(date: LocalDate, weightKg: Double)

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun deleteWeight(id: Long)

    @Query("SELECT * FROM daily_intake WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeIntakes(from: LocalDate, to: LocalDate): Flow<List<DailyIntakeEntity>>

    @Query("SELECT * FROM daily_intake ORDER BY date")
    fun observeAllIntakes(): Flow<List<DailyIntakeEntity>>

    @Upsert
    suspend fun upsertIntake(intake: DailyIntakeEntity)
}

@Dao
abstract class RestrictionDao {
    @Query("SELECT * FROM user_restriction")
    abstract fun observeAll(): Flow<List<UserRestrictionEntity>>

    @Query("SELECT * FROM user_restriction")
    abstract suspend fun getAll(): List<UserRestrictionEntity>

    @Query("DELETE FROM user_restriction WHERE kind = :kind")
    abstract suspend fun deleteKind(kind: RestrictionKind)

    @Insert
    abstract suspend fun insertAll(restrictions: List<UserRestrictionEntity>)

    /** Remplace toutes les restrictions d'un type en une seule transaction. */
    @Transaction
    open suspend fun replaceKind(kind: RestrictionKind, codes: Collection<String>) {
        deleteKind(kind)
        insertAll(codes.distinct().map { UserRestrictionEntity(kind, it) })
    }
}
