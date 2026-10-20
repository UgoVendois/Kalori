package fr.kalori.app.feature.meals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.NutritionTable
import fr.kalori.app.core.ui.components.SnackbarMessages
import fr.kalori.app.data.meal.toShoppingIngredient
import fr.kalori.app.domain.meal.ShoppingListBuilder
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.toFlagSet

@Composable
fun MealDetailScreen(
    onBack: () -> Unit,
    viewModel: MealDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val aiAvailable by viewModel.aiAvailable.collectAsStateWithLifecycle()
    val aiRunning by viewModel.aiRunning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    SnackbarMessages(viewModel.messages, snackbar)
    LaunchedEffect(viewModel) { viewModel.aiMessages.collect { snackbar.showSnackbar(it) } }

    KaloriScaffold(
        title = Format.dayLabel(viewModel.date, viewModel.today),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (viewModel.editable && detail?.day?.status != MealStatus.EATEN && aiAvailable) {
                if (aiRunning) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::proposeAiVariant) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Variante proposée par l'IA")
                    }
                }
            }
            if (viewModel.editable && detail?.day?.status != MealStatus.EATEN) {
                IconButton(onClick = viewModel::replace) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = stringResource(R.string.meals_replace))
                }
            }
        },
    ) { padding ->
        val current = detail
        if (current == null) {
            Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@KaloriScaffold
        }
        val allergens = current.allergensMask.toFlagSet<Allergen>()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current.day.name, style = MaterialTheme.typography.headlineSmall)
                    if (current.day.aiGenerated) {
                        Text(stringResource(R.string.meals_ai_label), color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(
                        stringResource(R.string.meals_portion, Format.decimal(current.day.portionFactor, 2)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (current.day.outOfSeason) {
                item { DisclaimerCard(stringResource(R.string.meals_out_of_season)) }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.meals_nutrition_title), style = MaterialTheme.typography.titleSmall)
                        NutritionTable(current.day.nutrition)
                        Text(
                            stringResource(R.string.meals_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                val text = if (allergens.isEmpty()) {
                    stringResource(R.string.meals_allergens_none)
                } else {
                    stringResource(R.string.meals_allergens_contains, allergens.joinToString(", ") { it.label.lowercase() })
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            item { DisclaimerCard(stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning) }
            item { Text(stringResource(R.string.meals_ingredients), style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(current.ingredients, key = { _, (ingredient, _) -> ingredient.id }) { _, (ingredient, grams) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ingredient.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ShoppingListBuilder.formatQuantity(grams, ingredient.toShoppingIngredient()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Text(stringResource(R.string.meals_steps), style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(current.steps) { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
