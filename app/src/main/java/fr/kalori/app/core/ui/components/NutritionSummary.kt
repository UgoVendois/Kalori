package fr.kalori.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.kalori.app.core.ui.Format
import fr.kalori.app.domain.model.Nutrition

/** Ligne compacte : kcal + 4 nutriments (protéines, lipides, glucides, fibres). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionChips(nutrition: Nutrition, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip(Format.kcal(nutrition.kcal), emphasized = true)
        Chip("Prot. ${Format.grams(nutrition.protein)}")
        Chip("Lip. ${Format.grams(nutrition.fat)}")
        Chip("Gluc. ${Format.grams(nutrition.carbs)}")
        Chip("Fibres ${Format.grams(nutrition.fiber)}")
    }
}

@Composable
private fun Chip(text: String, emphasized: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Tableau détaillé des 5 valeurs. */
@Composable
fun NutritionTable(nutrition: Nutrition, modifier: Modifier = Modifier) {
    val rows = listOf(
        "Énergie" to Format.kcal(nutrition.kcal),
        "Protéines" to Format.grams(nutrition.protein),
        "Lipides" to Format.grams(nutrition.fat),
        "Glucides" to Format.grams(nutrition.carbs),
        "Fibres" to Format.grams(nutrition.fiber),
    )
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
