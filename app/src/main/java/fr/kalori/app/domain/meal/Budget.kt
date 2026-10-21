package fr.kalori.app.domain.meal

import fr.kalori.app.domain.model.BudgetPeriod

/** Budget courses tel que saisi (montant et période) ; le planificateur raisonne par semaine. */
data class Budget(val amountEur: Int, val period: BudgetPeriod) {
    val perWeek: Double get() = toWeek(amountEur.toDouble(), period)

    /** Montant hebdomadaire exprimé dans la période de ce budget (affichage). */
    fun fromWeek(weekly: Double): Double = when (period) {
        BudgetPeriod.WEEK -> weekly
        BudgetPeriod.MONTH -> weekly * WEEKS_PER_YEAR / 12
    }

    companion object {
        const val WEEKS_PER_YEAR = 52.18

        fun toWeek(amount: Double, period: BudgetPeriod): Double = when (period) {
            BudgetPeriod.WEEK -> amount
            BudgetPeriod.MONTH -> amount * 12 / WEEKS_PER_YEAR
        }
    }
}
