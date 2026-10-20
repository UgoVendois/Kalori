package fr.kalori.app.domain.tracking

import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.Nutrition
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackingAggregatorTest {

    private val today = LocalDate.of(2026, 10, 21) // mercredi
    private fun n(kcal: Double, p: Double = 0.0) = Nutrition(kcal, p, 0.0, 0.0, 0.0)

    @Test
    fun `daily intake adds the eaten meal and extra entries`() {
        val daily = TrackingAggregator.dailyIntake(
            eatenMeals = listOf(today to n(700.0, 40.0)),
            extras = listOf(today to n(1200.0, 50.0), today.minusDays(1) to n(1800.0)),
        )
        assertEquals(1900.0, daily.getValue(today).kcal, 1e-9)
        assertEquals(90.0, daily.getValue(today).protein, 1e-9)
        assertEquals(2, daily.size)
    }

    @Test
    fun `week shows the last 7 days and skips days without data`() {
        val daily = mapOf(
            today to n(2000.0),
            today.minusDays(6) to n(1800.0),
            today.minusDays(7) to n(1500.0), // hors fenêtre
            today.plusDays(1) to n(9999.0), // futur ignoré
        )
        val series = TrackingAggregator.intakeSeries(daily, Period.WEEK, today)
        assertEquals(listOf(today.minusDays(6), today), series.map { it.start })
    }

    @Test
    fun `all time averages each week over logged days only`() {
        val monday = LocalDate.of(2026, 10, 19)
        val daily = mapOf(
            monday to n(2000.0),
            monday.plusDays(1) to n(1600.0),
            monday.minusDays(7) to n(2200.0),
        )
        val series = TrackingAggregator.intakeSeries(daily, Period.ALL, today)
        assertEquals(listOf(monday.minusDays(7), monday), series.map { it.start })
        assertEquals(1800.0, series[1].nutrition.kcal, 1e-9)
        assertEquals(2, series[1].loggedDays)
    }

    @Test
    fun `weights are filtered by window or averaged per week`() {
        val weights = listOf(
            DatedValue(today.minusDays(40), 82.0),
            DatedValue(today.minusDays(10), 80.0),
            DatedValue(today.minusDays(2), 79.0),
            DatedValue(today.minusDays(1), 78.6),
        )
        assertEquals(2, TrackingAggregator.weightSeries(weights, Period.WEEK, today).size)
        assertEquals(3, TrackingAggregator.weightSeries(weights, Period.MONTH, today).size)
        val all = TrackingAggregator.weightSeries(weights, Period.ALL, today)
        assertEquals(78.8, all.last().value, 1e-9) // lundi 19 et mardi 20 : (79 + 78,6) / 2
    }

    @Test
    fun `sessions per week include empty weeks`() {
        val sessions = listOf(today, today.minusDays(1), today.minusDays(14))
        val series = TrackingAggregator.sessionsPerWeek(sessions, Period.WEEK, today)
        assertEquals(listOf(0, 1, 0, 2), series.map { it.count })
        assertEquals(5, TrackingAggregator.sessionsPerWeek(sessions, Period.MONTH, today).size)
        assertEquals(3, TrackingAggregator.sessionsPerWeek(sessions, Period.ALL, today).size)
    }

    @Test
    fun `goal progress is bounded and undefined for maintenance`() {
        assertEquals(0.5, TrackingAggregator.goalProgress(90.0, 80.0, 85.0, GoalType.LOSE)!!, 1e-9)
        assertEquals(0.0, TrackingAggregator.goalProgress(90.0, 80.0, 92.0, GoalType.LOSE)!!, 1e-9)
        assertEquals(1.0, TrackingAggregator.goalProgress(90.0, 80.0, 79.0, GoalType.LOSE)!!, 1e-9)
        assertEquals(0.25, TrackingAggregator.goalProgress(60.0, 64.0, 61.0, GoalType.GAIN)!!, 1e-9)
        assertNull(TrackingAggregator.goalProgress(70.0, 70.0, 71.0, GoalType.MAINTAIN))
    }
}
