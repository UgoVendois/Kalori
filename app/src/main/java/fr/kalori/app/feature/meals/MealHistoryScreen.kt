package fr.kalori.app.feature.meals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.EmptyState
import fr.kalori.app.core.ui.components.IconBadge
import fr.kalori.app.core.ui.components.KaloriCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.icon
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.core.ui.theme.accent
import fr.kalori.app.data.meal.DayMeal
import fr.kalori.app.domain.model.MealStatus
import java.time.LocalDate
import kotlin.math.roundToInt

/** Repas des jours passés (masqués de l'écran principal), groupés par jour. */
@Composable
fun MealHistoryScreen(
    onBack: () -> Unit,
    onOpenMeal: (DayMeal) -> Unit,
    viewModel: MealHistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    KaloriScaffold(title = stringResource(R.string.meals_history), onBack = onBack) { padding ->
        val days = history
        when {
            days == null -> Box(Modifier.fillMaxWidth().padding(padding).padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            days.isEmpty() -> EmptyState(
                icon = Icons.Outlined.History,
                title = "Pas encore d'historique",
                text = stringResource(R.string.meals_history_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(days, key = { it.first.toEpochDay() }) { (date, meals) ->
                    HistoryDay(date, viewModel.today, meals, onOpenMeal)
                }
            }
        }
    }
}

@Composable
private fun HistoryDay(date: LocalDate, today: LocalDate, meals: List<DayMeal>, onOpenMeal: (DayMeal) -> Unit) {
    val eaten = meals.filter { it.status == MealStatus.EATEN }.sumOf { it.nutrition.kcal }
    KaloriCard(contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.dayLabel(date, today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${eaten.roundToInt()} kcal mangées", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        meals.forEach { meal ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenMeal(meal) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(meal.slot.icon(), meal.slot.accent(), size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${meal.slot.label} · ${Format.kcal(meal.nutrition.kcal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (meal.status == MealStatus.EATEN) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Mangé", tint = Kalori.colors.snack.solid, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
