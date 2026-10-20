package fr.kalori.app.feature.meals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.NutritionChips
import fr.kalori.app.domain.model.MealStatus
import java.time.LocalDate

/** Plats des jours passés (masqués de la liste principale). */
@Composable
fun MealHistoryScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: MealHistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    KaloriScaffold(title = stringResource(R.string.meals_history), onBack = onBack) { padding ->
        val list = history
        when {
            list == null -> Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Text(
                stringResource(R.string.meals_history_empty),
                modifier = Modifier.padding(padding).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.date.toEpochDay() }) { day ->
                    Card(onClick = { onOpenDay(day.date) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Format.dayLabel(day.date, viewModel.today), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    when (day.status) {
                                        MealStatus.EATEN -> "Mangé"
                                        MealStatus.SKIPPED -> "Sauté"
                                        MealStatus.PLANNED -> "Non pointé"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(day.name, style = MaterialTheme.typography.titleSmall)
                            NutritionChips(day.nutrition)
                        }
                    }
                }
            }
        }
    }
}
