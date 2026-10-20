package fr.kalori.app.core.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Formatage pour l'affichage, en français. */
object Format {
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val shortFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
    private val numericFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH)

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        today.minusDays(1) -> "Hier"
        else -> date.format(dayFormatter).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }

    fun shortDate(date: LocalDate): String = date.format(shortFormatter)

    fun numericDate(date: LocalDate): String = date.format(numericFormatter)

    fun kcal(value: Double): String = "${value.roundToInt()} kcal"

    fun grams(value: Double): String = "${value.roundToInt()} g"

    /** 1.25 → « 1,25 » ; 1.0 → « 1 ». */
    fun decimal(value: Double, decimals: Int = 1): String {
        val text = "%.${decimals}f".format(Locale.FRENCH, value)
        return if (text.contains(',')) text.trimEnd('0').trimEnd(',') else text
    }
}
