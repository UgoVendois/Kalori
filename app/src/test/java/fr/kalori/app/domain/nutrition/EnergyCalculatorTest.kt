package fr.kalori.app.domain.nutrition

import fr.kalori.app.domain.model.ActivityLevel
import fr.kalori.app.domain.model.Sex
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class EnergyCalculatorTest {

    @Test
    fun `Mifflin-St Jeor reference values`() {
        // Homme, 80 kg, 180 cm, 30 ans : 800 + 1125 − 150 + 5 = 1780
        assertEquals(1780.0, EnergyCalculator.basalMetabolicRate(Sex.MALE, 80.0, 180.0, 30), 1e-9)
        // Femme, 60 kg, 165 cm, 40 ans : 600 + 1031,25 − 200 − 161 = 1270,25
        assertEquals(1270.25, EnergyCalculator.basalMetabolicRate(Sex.FEMALE, 60.0, 165.0, 40), 1e-9)
    }

    @Test
    fun `unspecified sex uses the average of both equations`() {
        val male = EnergyCalculator.basalMetabolicRate(Sex.MALE, 70.0, 172.0, 35)
        val female = EnergyCalculator.basalMetabolicRate(Sex.FEMALE, 70.0, 172.0, 35)
        assertEquals((male + female) / 2, EnergyCalculator.basalMetabolicRate(Sex.UNSPECIFIED, 70.0, 172.0, 35), 1e-9)
    }

    @Test
    fun `total energy applies the activity factor`() {
        assertEquals(1780.0 * 1.55, EnergyCalculator.totalDailyEnergy(Sex.MALE, 80.0, 180.0, 30, ActivityLevel.MODERATE), 1e-9)
    }

    @Test
    fun `bmi and weight for bmi are inverse`() {
        assertEquals(24.69, EnergyCalculator.bmi(80.0, 180.0), 0.01)
        assertEquals(59.94, EnergyCalculator.weightForBmi(18.5, 180.0), 0.01)
    }

    @Test
    fun `age is computed conservatively without the day of birth`() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(18, EnergyCalculator.ageYears(2008, 9, today))
        // Anniversaire ce mois-ci : considéré comme non atteint (prudence pour le seuil des 18 ans).
        assertEquals(17, EnergyCalculator.ageYears(2008, 10, today))
        assertEquals(17, EnergyCalculator.ageYears(2008, 11, today))
    }
}
