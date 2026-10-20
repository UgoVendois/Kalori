package fr.nutrisport.app.domain.model

/**
 * Énumération stockée en base sous forme de masque de bits.
 *
 * [bit] est figé : ne jamais réutiliser ni renuméroter un bit existant (les masques sont persistés).
 */
interface BitFlag {
    val bit: Int
}

fun <E> Iterable<E>.toMask(): Int where E : Enum<E>, E : BitFlag =
    fold(0) { mask, flag -> mask or (1 shl flag.bit) }

inline fun <reified E> Int.toFlagSet(): Set<E> where E : Enum<E>, E : BitFlag =
    enumValues<E>().filterTo(LinkedHashSet()) { this and (1 shl it.bit) != 0 }

fun Int.hasFlag(flag: BitFlag): Boolean = this and (1 shl flag.bit) != 0

/** Mois de disponibilité (bit 0 = janvier … bit 11 = décembre). */
object SeasonMask {
    const val ALL_YEAR: Int = 0xFFF

    fun of(months: Iterable<Int>): Int = months.fold(0) { mask, month ->
        require(month in 1..12) { "Mois invalide : $month" }
        mask or (1 shl (month - 1))
    }

    fun contains(mask: Int, month: Int): Boolean = mask and (1 shl (month - 1)) != 0

    fun months(mask: Int): List<Int> = (1..12).filter { contains(mask, it) }
}
