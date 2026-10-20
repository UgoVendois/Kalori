package fr.kalori.app.domain.tracking

import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.Nutrition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Période affichée sur le tableau de bord. */
enum class Period(val label: String) {
    WEEK("Semaine"),
    MONTH("Mois"),
    ALL("Tout"),
}

/** Un point de série : une date (jour ou lundi de la semaine) et une valeur. */
data class DatedValue(val date: LocalDate, val value: Double)

/** Apports d'un jour ou d'une semaine (moyenne par jour renseigné). */
data class IntakeBucket(val start: LocalDate, val nutrition: Nutrition, val loggedDays: Int)

data class SessionBucket(val weekStart: LocalDate, val count: Int)

/**
 * Agrégations du tableau de bord.
 * Semaine = 7 derniers jours (un point par jour) ; Mois = 30 derniers jours (un point par jour) ;
 * Tout = une moyenne par semaine (lundi), depuis la première donnée.
 * Un jour sans aucune donnée n'est jamais compté comme « 0 kcal » : il est absent.
 */
object TrackingAggregator {

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun windowStart(period: Period, today: LocalDate): LocalDate? = when (period) {
        Period.WEEK -> today.minusDays(6)
        Period.MONTH -> today.minusDays(29)
        Period.ALL -> null
    }

    /** Apports par jour : plat du jour marqué « mangé » + apports saisis en plus. */
    fun dailyIntake(
        eatenMeals: List<Pair<LocalDate, Nutrition>>,
        extras: List<Pair<LocalDate, Nutrition>>,
    ): Map<LocalDate, Nutrition> =
        (eatenMeals + extras).groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.fold(Nutrition.ZERO) { acc, n -> acc + n } }

    fun intakeSeries(daily: Map<LocalDate, Nutrition>, period: Period, today: LocalDate): List<IntakeBucket> {
        val start = windowStart(period, today)
        val inRange = daily.filterKeys { !it.isAfter(today) && (start == null || !it.isBefore(start)) }
        if (period != Period.ALL) {
            return inRange.toSortedMap().map { (date, n) -> IntakeBucket(date, n, 1) }
        }
        return inRange.entries.groupBy { weekStart(it.key) }
            .toSortedMap()
            .map { (week, days) ->
                val total = days.fold(Nutrition.ZERO) { acc, d -> acc + d.value }
                IntakeBucket(week, total * (1.0 / days.size), days.size)
            }
    }

    /** Pesées dans la fenêtre ; pour « Tout », moyenne hebdomadaire. */
    fun weightSeries(weights: List<DatedValue>, period: Period, today: LocalDate): List<DatedValue> {
        val start = windowStart(period, today)
        val inRange = weights.filter { !it.date.isAfter(today) && (start == null || !it.date.isBefore(start)) }
            .sortedBy { it.date }
        if (period != Period.ALL) return inRange
        return inRange.groupBy { weekStart(it.date) }
            .toSortedMap()
            .map { (week, list) -> DatedValue(week, list.map { it.value }.average()) }
    }

    /** Séances terminées par semaine : 4 dernières semaines (Semaine), 5 (Mois), toutes (Tout). Les semaines vides comptent 0. */
    fun sessionsPerWeek(sessionDates: List<LocalDate>, period: Period, today: LocalDate): List<SessionBucket> {
        val currentWeek = weekStart(today)
        val firstWeek = when (period) {
            Period.WEEK -> currentWeek.minusWeeks(3)
            Period.MONTH -> currentWeek.minusWeeks(4)
            Period.ALL -> sessionDates.minOrNull()?.let(::weekStart) ?: currentWeek
        }
        val counts = sessionDates.groupingBy { weekStart(it) }.eachCount()
        val weeks = ChronoUnit.WEEKS.between(firstWeek, currentWeek).toInt()
        return (0..weeks).map { i ->
            val week = firstWeek.plusWeeks(i.toLong())
            SessionBucket(week, counts[week] ?: 0)
        }
    }

    /**
     * Part du chemin parcouru entre le poids de départ et le poids cible (0 à 1), ou null pour le maintien.
     * Un mouvement dans le mauvais sens donne 0 ; l'objectif dépassé donne 1.
     */
    fun goalProgress(startKg: Double, targetKg: Double, currentKg: Double, goal: GoalType): Double? {
        if (goal == GoalType.MAINTAIN) return null
        val total = targetKg - startKg
        if (total == 0.0) return 1.0
        return ((currentKg - startKg) / total).coerceIn(0.0, 1.0)
    }
}
