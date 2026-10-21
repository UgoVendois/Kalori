package fr.kalori.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.domain.model.Nutrition

/** Tableau détaillé des 5 valeurs, avec la répartition des macros. */
@Composable
fun NutritionTable(nutrition: Nutrition, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.kcal(nutrition.kcal).substringBefore(" "), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(4.dp))
            Text("kcal", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        MacroBar(nutrition)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Cell(Kalori.colors.protein, "Protéines", nutrition.protein)
            Cell(Kalori.colors.fat, "Lipides", nutrition.fat)
            Cell(Kalori.colors.carbs, "Glucides", nutrition.carbs)
            Cell(MaterialTheme.colorScheme.outline, "Fibres", nutrition.fiber)
        }
    }
}

@Composable
private fun Cell(color: Color, label: String, grams: Double) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Format.grams(grams), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
