package fr.kalori.app.feature.meals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.domain.meal.DietRules
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.Diet
import java.text.Normalizer

/** « Mes goûts et restrictions », accessible à tout moment depuis l'onglet Repas. */
@Composable
fun FoodPreferencesScreen(
    onDone: () -> Unit,
    viewModel: FoodPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onDone() } }

    KaloriScaffold(
        title = stringResource(R.string.meals_preferences),
        onBack = onDone,
        actions = {
            TextButton(onClick = viewModel::save, enabled = state.loaded && !state.saving) {
                Text(stringResource(R.string.save))
            }
        },
    ) { padding ->
        if (!state.loaded || state.saving) {
            Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@KaloriScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            foodPreferencesItems(
                state = state,
                onToggleAllergen = viewModel::toggleAllergen,
                onToggleDiet = viewModel::toggleDiet,
                onQueryChange = viewModel::setQuery,
                onToggleLike = viewModel::toggleLike,
                onToggleDislike = viewModel::toggleDislike,
            )
        }
    }
}

/** Contenu réutilisable (aussi utilisé par le premier lancement). */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.foodPreferencesItems(
    state: FoodPreferencesUiState,
    onToggleAllergen: (Allergen) -> Unit,
    onToggleDiet: (Diet) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onToggleDislike: (String) -> Unit,
) {
    val prefs = state.prefs

    item { SectionTitle(stringResource(R.string.prefs_allergens_title), stringResource(R.string.prefs_allergens_hint)) }
    items(Allergen.entries, key = { "allergen_${it.name}" }) { allergen ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleAllergen(allergen) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = allergen in prefs.allergens, onCheckedChange = { onToggleAllergen(allergen) })
            Text(allergen.label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item { SectionTitle(stringResource(R.string.prefs_diets_title), null) }
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Diet.entries.forEach { diet ->
                FilterChip(
                    selected = diet in prefs.diets,
                    onClick = { onToggleDiet(diet) },
                    label = { Text(diet.label) },
                )
            }
        }
    }
    if (prefs.diets.any { it in DietRules.requiresCertifiedProducts }) {
        item { DisclaimerCard(stringResource(R.string.prefs_certified_hint)) }
    }

    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item { SectionTitle(stringResource(R.string.prefs_tastes_title), stringResource(R.string.prefs_tastes_hint)) }
    item {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.prefs_search)) },
        )
    }
    // Sans recherche : les aliments déjà choisis. Avec recherche : les correspondances (30 au plus).
    val query = state.query.normalized()
    val shown = if (query.isBlank()) {
        state.ingredients.filter { it.id in prefs.liked || it.id in prefs.disliked }
    } else {
        state.ingredients.filter { it.name.normalized().contains(query) }.take(30)
    }
    items(shown, key = { "ingredient_${it.id}" }) { ingredient ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(ingredient.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            FilterChip(
                selected = ingredient.id in prefs.liked,
                onClick = { onToggleLike(ingredient.id) },
                label = { Text(stringResource(R.string.prefs_like)) },
            )
            FilterChip(
                selected = ingredient.id in prefs.disliked,
                onClick = { onToggleDislike(ingredient.id) },
                label = { Text(stringResource(R.string.prefs_dislike)) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Recherche insensible à la casse et aux accents (« oeuf » trouve « Œuf », « epinard » trouve « Épinard »). */
private fun String.normalized(): String =
    Normalizer.normalize(lowercase().replace("œ", "oe"), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()
