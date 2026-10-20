package fr.kalori.app.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BitFlagsTest {

    @Test
    fun `allergen mask round-trips`() {
        val set = setOf(Allergen.GLUTEN, Allergen.SESAME, Allergen.MOLLUSCS)
        assertEquals(set, set.toMask().toFlagSet<Allergen>())
    }

    @Test
    fun `bits are unique and stable`() {
        // Ces valeurs sont persistées : ce test protège contre une renumérotation accidentelle.
        assertEquals(14, Allergen.entries.size)
        assertEquals(Allergen.entries.size, Allergen.entries.map { it.bit }.toSet().size)
        assertEquals(DietFlag.entries.size, DietFlag.entries.map { it.bit }.toSet().size)
        assertEquals(0, Allergen.GLUTEN.bit)
        assertEquals(13, Allergen.MOLLUSCS.bit)
        assertEquals(8, DietFlag.LACTOSE.bit)
    }

    @Test
    fun `season mask handles ranges crossing the new year`() {
        val winter = SeasonMask.of(listOf(11, 12, 1, 2))
        assertEquals(listOf(1, 2, 11, 12), SeasonMask.months(winter))
        assertTrue(SeasonMask.contains(winter, 12))
        assertEquals(SeasonMask.ALL_YEAR, SeasonMask.of(1..12))
        assertFailsWith<IllegalArgumentException> { SeasonMask.of(listOf(13)) }
    }
}
