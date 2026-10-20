package fr.kalori.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.RestrictionKind
import fr.kalori.app.domain.model.Sex
import fr.kalori.app.domain.model.UnitSystem
import java.time.Instant
import java.time.LocalDate

/**
 * Profil unique de l'utilisateur (une seule ligne, id = 1).
 * Minimisation : année et mois de naissance seulement, pas de date complète.
 * Les valeurs sont toujours stockées en unités métriques.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val sex: Sex,
    val birthYear: Int,
    val birthMonth: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activityLevel: ActivityLevel,
    val goalType: GoalType,
    /** Rythme choisi par l'utilisateur (kg/semaine, positif) ; 0 pour le maintien. */
    val paceKgPerWeek: Double,
    val kcalTarget: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val fiberG: Int,
    /** Part de l'objectif journalier couverte par le plat du jour (0,30 à 0,50). */
    val mealShare: Double,
    val unitSystem: UnitSystem,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

@Entity(tableName = "weight_entry", indices = [Index(value = ["date"], unique = true)])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
)

/** Apports saisis en plus du plat du jour (autres repas, collations), un total par jour. */
@Entity(tableName = "daily_intake")
data class DailyIntakeEntity(
    @PrimaryKey val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
)

/** Allergènes, régimes, aliments aimés et détestés. [code] = nom d'enum ou id d'ingrédient. */
@Entity(tableName = "user_restriction", primaryKeys = ["kind", "code"])
data class UserRestrictionEntity(
    val kind: RestrictionKind,
    val code: String,
)
