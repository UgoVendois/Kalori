package fr.kalori.app.feature.meals

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.NutritionChips
import fr.kalori.app.core.ui.components.SnackbarMessages
import fr.kalori.app.data.meal.PlannedDay
import fr.kalori.app.domain.model.MealStatus
import java.time.LocalDate

@Composable
fun MealsScreen(
    onOpenDay: (LocalDate) -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShopping: () -> Unit,
    viewModel: MealsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    SnackbarMessages(viewModel.messages, snackbar)

    KaloriScaffold(title = stringResource(R.string.tab_meals), snackbarHostState = snackbar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FilledTonalButton(onClick = onOpenPreferences, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.meals_preferences))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenShopping, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.meals_shopping))
                    }
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.meals_history))
                    }
                }
            }
            item { DisclaimerCard(stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning) }

            when {
                state.loading -> item { Loading() }
                !state.hasProfile -> item { DisclaimerCard(stringResource(R.string.meals_no_profile)) }
                else -> items(state.dates, key = { it.toEpochDay() }) { date ->
                    val day = state.days[date]
                    if (day != null) {
                        DayCard(
                            day = day,
                            label = Format.dayLabel(date, state.today),
                            onOpen = { onOpenDay(date) },
                            onReplace = { viewModel.replace(date) },
                            onToggleEaten = { viewModel.toggleEaten(day) },
                        )
                    } else {
                        EmptyDayCard(Format.dayLabel(date, state.today), planning = state.planning)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    day: PlannedDay,
    label: String,
    onOpen: () -> Unit,
    onReplace: () -> Unit,
    onToggleEaten: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(day.name, style = MaterialTheme.typography.titleMedium)
            if (day.outOfSeason || day.aiGenerated) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (day.aiGenerated) AssistChip(onClick = {}, label = { Text(stringResource(R.string.meals_ai_label)) })
                    if (day.outOfSeason) AssistChip(onClick = {}, label = { Text(stringResource(R.string.meals_out_of_season_short)) })
                }
            }
            NutritionChips(day.nutrition)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReplace, enabled = day.status != MealStatus.EATEN) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.meals_replace))
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = day.status == MealStatus.EATEN,
                    onClick = onToggleEaten,
                    label = { Text(stringResource(R.string.meals_mark_eaten)) },
                    leadingIcon = if (day.status == MealStatus.EATEN) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyDayCard(label: String, planning: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (planning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.meals_preparing), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(stringResource(R.string.meals_no_dish), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Loading() {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}
