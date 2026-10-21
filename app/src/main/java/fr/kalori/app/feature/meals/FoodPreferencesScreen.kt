package fr.kalori.app.feature.meals

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BreakfastDining
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KebabDining
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.database.entity.IngredientEntity
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.KaloriCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.icon
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.core.ui.theme.accent
import fr.kalori.app.domain.meal.Budget
import fr.kalori.app.domain.meal.DietRules
import fr.kalori.app.domain.model.Aisle
import fr.kalori.app.domain.model.Allergen
import fr.kalori.app.domain.model.BudgetPeriod
import fr.kalori.app.domain.model.CookingLevel
import fr.kalori.app.domain.model.Diet
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.PriceTier
import fr.kalori.app.domain.model.SnackMode
import java.text.Normalizer

/** Actions de l'écran, regroupées pour pouvoir réutiliser le contenu (premier lancement, tests de rendu). */
class FoodPreferencesActions(
    val onToggleAllergen: (Allergen) -> Unit,
    val onToggleDiet: (Diet) -> Unit,
    val onCycleTaste: (String) -> Unit,
    val onClearTaste: (String) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onToggleAisle: (Aisle) -> Unit,
    val onToggleMainSlot: (MealSlot) -> Unit,
    val onSnackMode: (SnackMode) -> Unit,
    val onBudget: (String) -> Unit = {},
    val onBudgetPeriod: (BudgetPeriod) -> Unit = {},
    val onPriceTier: (PriceTier) -> Unit = {},
    val onCookingLevel: (CookingLevel) -> Unit = {},
    val onLunchLeftovers: (Boolean) -> Unit = {},
    val onWeeklyBreakfast: (Boolean) -> Unit = {},
    val onWeeklySnack: (Boolean) -> Unit = {},
)

fun FoodPreferencesViewModel.actions() = FoodPreferencesActions(
    onToggleAllergen = ::toggleAllergen,
    onToggleDiet = ::toggleDiet,
    onCycleTaste = ::cycleTaste,
    onClearTaste = ::clearTaste,
    onQueryChange = ::setQuery,
    onToggleAisle = ::toggleAisle,
    onToggleMainSlot = ::toggleMainSlot,
    onSnackMode = ::setSnackMode,
    onBudget = ::setBudget,
    onBudgetPeriod = ::setBudgetPeriod,
    onPriceTier = ::setPriceTier,
    onCookingLevel = ::setCookingLevel,
    onLunchLeftovers = ::setLunchLeftovers,
    onWeeklyBreakfast = ::setWeeklyBreakfast,
    onWeeklySnack = ::setWeeklySnack,
)

/** « Mes goûts et restrictions » : chaque modification est enregistrée automatiquement. */
@Composable
fun FoodPreferencesScreen(
    onBack: () -> Unit,
    viewModel: FoodPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KaloriScaffold(
        title = stringResource(R.string.meals_preferences),
        onBack = onBack,
        actions = { SaveIndicator(state.saveState) },
    ) { padding ->
        FoodPreferencesContent(state, viewModel.actions(), Modifier.padding(padding))
    }
}

@Composable
fun FoodPreferencesContent(state: FoodPreferencesUiState, actions: FoodPreferencesActions, modifier: Modifier = Modifier) {
    if (!state.loaded) {
        Row(modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        foodPreferencesItems(state, actions)
    }
}

/** Indicateur d'enregistrement automatique, dans la barre de titre (icône seule, pour laisser la place au titre). */
@Composable
fun SaveIndicator(saveState: SaveState) {
    Box(Modifier.padding(end = 16.dp).size(24.dp), contentAlignment = Alignment.Center) {
        when (saveState) {
            SaveState.SAVING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            SaveState.SAVED -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Enregistré", tint = Kalori.colors.snack.solid)
            SaveState.IDLE -> Unit
        }
    }
}

/** Contenu réutilisable (écran dédié et premier lancement). */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.foodPreferencesItems(state: FoodPreferencesUiState, actions: FoodPreferencesActions) {
    val prefs = state.prefs

    item(key = "autosave") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Hint("Chaque modification est enregistrée automatiquement.")
        }
    }

    if (state.schedule.available) {
        item(key = "schedule") {
            KaloriCard {
                CardTitle("Mes repas de la journée", "Ton objectif est réparti entre les repas que tu prends.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.mains.forEach { slot ->
                        val selected = slot in state.schedule.mains
                        FilterChip(
                            selected = selected,
                            onClick = { actions.onToggleMainSlot(slot) },
                            label = { Text(slot.label) },
                            leadingIcon = { Icon(slot.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = slot.accent().container,
                                selectedLabelColor = slot.accent().content,
                                selectedLeadingIconColor = slot.accent().content,
                            ),
                        )
                    }
                }
                Text("Collation", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SnackMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.schedule.snackMode == mode,
                            onClick = { actions.onSnackMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SnackMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
                Hint("« Si besoin » : une collation est ajoutée quand ton objectif est élevé ou que tu prends peu de repas.")
            }
        }
        item(key = "organization") { OrganizationCard(state.schedule, actions) }
    }

    item(key = "allergens") {
        KaloriCard {
            CardTitle(stringResource(R.string.prefs_allergens_title), stringResource(R.string.prefs_allergens_hint))
            BlockGroup(Allergen.entries, selected = { it in prefs.allergens }, label = { it.shortLabel }, onToggle = actions.onToggleAllergen)
        }
    }

    item(key = "diets") {
        KaloriCard {
            CardTitle(stringResource(R.string.prefs_diets_title), null)
            BlockGroup(Diet.entries, selected = { it in prefs.diets }, label = { it.label }, onToggle = actions.onToggleDiet)
            if (prefs.diets.any { it in DietRules.requiresCertifiedProducts }) {
                DisclaimerCard(stringResource(R.string.prefs_certified_hint))
            }
        }
    }

    item(key = "tastes") {
        KaloriCard {
            CardTitle("Mes goûts", "Touche un aliment : ♥ j'aime, une 2ᵉ fois : ✕ je n'aime pas, une 3ᵉ fois : neutre. Les aliments que tu n'aimes pas sont exclus.")
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.prefs_search)) },
            )
            val byId = state.ingredients.associateBy { it.id }
            val liked = prefs.liked.mapNotNull { byId[it] }.sortedBy { it.name }
            val disliked = prefs.disliked.mapNotNull { byId[it] }.sortedBy { it.name }
            if (liked.isNotEmpty()) SelectionRow("J'aime", liked, TasteState.LIKED, actions.onClearTaste)
            if (disliked.isNotEmpty()) SelectionRow("Je n'aime pas", disliked, TasteState.DISLIKED, actions.onClearTaste)
        }
    }

    val query = state.query.normalized()
    if (query.isNotBlank()) {
        val matches = state.ingredients.filter { it.name.normalized().contains(query) }.sortedBy { it.name }
        item(key = "search_results") {
            KaloriCard {
                if (matches.isEmpty()) {
                    Hint("Aucun aliment ne correspond à « ${state.query} ».")
                } else {
                    TasteGrid(matches, prefs.liked, prefs.disliked, actions.onCycleTaste)
                }
            }
        }
    } else {
        state.ingredients.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal }).forEach { (aisle, ingredients) ->
            item(key = "aisle_${aisle.name}") {
                val expanded = aisle in state.expandedAisles
                val selectedCount = ingredients.count { it.id in prefs.liked || it.id in prefs.disliked }
                KaloriCard(onClick = { actions.onToggleAisle(aisle) }, contentPadding = 14.dp, modifier = Modifier.animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(aisle.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(aisle.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (selectedCount == 0) "${ingredients.size} aliments" else "${ingredients.size} aliments · $selectedCount choisi(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                    if (expanded) TasteGrid(ingredients.sortedBy { it.name }, prefs.liked, prefs.disliked, actions.onCycleTaste)
                }
            }
        }
    }
}

/** Budget, niveau de cuisine, restes pour le midi et recettes de la semaine. */
@Composable
private fun OrganizationCard(schedule: ScheduleState, actions: FoodPreferencesActions) {
    val organization = schedule.organization
    KaloriCard {
        CardTitle("Budget et organisation", "Les repas à venir sont recomposés à chaque changement.")

        Text("Où tu fais tes courses", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PriceTier.entries.forEachIndexed { index, tier ->
                SegmentedButton(
                    selected = organization.priceTier == tier,
                    onClick = { actions.onPriceTier(tier) },
                    shape = SegmentedButtonDefaults.itemShape(index, PriceTier.entries.size),
                ) { Text(tier.label, maxLines = 1) }
            }
        }
        Hint(organization.priceTier.description)

        val period = organization.budgetPeriod
        OutlinedTextField(
            value = organization.budgetEur?.toString().orEmpty(),
            onValueChange = actions.onBudget,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            label = { Text("Budget courses") },
            placeholder = { Text("Pas de limite") },
            leadingIcon = { Icon(Icons.Outlined.Savings, contentDescription = null) },
            suffix = { Text("€ / ${period.short}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(
                    buildString {
                        schedule.cheapestWeek?.let { weekly ->
                            val minimum = kotlin.math.ceil(Budget(1, period).fromWeek(weekly) / 5.0).toInt() * 5
                            append("Pour ton objectif, compte au moins ~$minimum € par ${period.short} avec ces prix. ")
                        }
                        append("Prix moyens estimés, modifiables dans la liste de courses.")
                    },
                )
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            BudgetPeriod.entries.forEachIndexed { index, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = { actions.onBudgetPeriod(p) },
                    shape = SegmentedButtonDefaults.itemShape(index, BudgetPeriod.entries.size),
                ) { Text(p.label) }
            }
        }

        Text("Niveau de cuisine", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CookingLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = organization.cookingLevel == level,
                    onClick = { actions.onCookingLevel(level) },
                    shape = SegmentedButtonDefaults.itemShape(index, CookingLevel.entries.size),
                ) { Text(level.label) }
            }
        }
        Hint(organization.cookingLevel.description)

        val leftoversPossible = MealSlot.NOON in schedule.mains && MealSlot.EVENING in schedule.mains
        SwitchRow(
            icon = Icons.Outlined.LunchDining,
            title = "Midi : restes du dîner de la veille",
            text = if (leftoversPossible) {
                "Le dîner est cuisiné en double : la 2ᵉ portion part dans ta boîte pour le lendemain midi."
            } else {
                "Active le midi et le soir pour utiliser les restes."
            },
            checked = organization.lunchLeftovers && leftoversPossible,
            enabled = leftoversPossible,
            onChange = actions.onLunchLeftovers,
        )
        SwitchRow(
            icon = Icons.Outlined.Kitchen,
            title = "Même collation toute la semaine",
            text = "Une fournée préparée en début de semaine : cookies, barres, energy balls…",
            checked = organization.weeklySnack && schedule.snackMode != SnackMode.NEVER,
            enabled = schedule.snackMode != SnackMode.NEVER,
            onChange = actions.onWeeklySnack,
        )
        SwitchRow(
            icon = Icons.Outlined.BreakfastDining,
            title = "Même petit-déjeuner toute la semaine",
            text = if (MealSlot.MORNING in schedule.mains) "Préparé en une fois (granola, muffins…) ou sans cuisson." else "Active le matin pour l'utiliser.",
            checked = organization.weeklyBreakfast && MealSlot.MORNING in schedule.mains,
            enabled = MealSlot.MORNING in schedule.mains,
            onChange = actions.onWeeklyBreakfast,
        )
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(text)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun CardTitle(title: String, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Hint(hint)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Blocs à cocher : même présentation pour les allergènes et les régimes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> BlockGroup(items: List<T>, selected: (T) -> Boolean, label: (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val isSelected = selected(item)
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(item) },
                label = { Text(label(item)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

private enum class TasteState { NEUTRAL, LIKED, DISLIKED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteGrid(ingredients: List<IngredientEntity>, liked: Set<String>, disliked: Set<String>, onCycle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ingredients.forEach { ingredient ->
            val state = when (ingredient.id) {
                in liked -> TasteState.LIKED
                in disliked -> TasteState.DISLIKED
                else -> TasteState.NEUTRAL
            }
            TasteChip(ingredient.name, state, onClick = { onCycle(ingredient.id) })
        }
    }
}

@Composable
private fun TasteChip(name: String, state: TasteState, onClick: () -> Unit, trailingClose: Boolean = false) {
    val (container, content, icon) = when (state) {
        TasteState.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, null)
        TasteState.LIKED -> Triple(Kalori.colors.snack.container, Kalori.colors.snack.content, Icons.Filled.Favorite)
        TasteState.DISLIKED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Outlined.Block)
    }
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(name, style = MaterialTheme.typography.labelLarge)
            if (trailingClose) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Close, contentDescription = "Retirer", modifier = Modifier.size(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionRow(title: String, ingredients: List<IngredientEntity>, state: TasteState, onClear: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state == TasteState.LIKED) Icons.Outlined.FavoriteBorder else Icons.Outlined.Block,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (state == TasteState.LIKED) Kalori.colors.snack.solid else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(6.dp))
            Text("$title (${ingredients.size})", style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ingredients.forEach { TasteChip(it.name, state, onClick = { onClear(it.id) }, trailingClose = true) }
        }
    }
}

fun Aisle.icon(): ImageVector = when (this) {
    Aisle.FRUITS_VEGETABLES -> Icons.Outlined.Eco
    Aisle.BAKERY -> Icons.Outlined.BakeryDining
    Aisle.BUTCHER -> Icons.Outlined.KebabDining
    Aisle.FISHMONGER -> Icons.Outlined.SetMeal
    Aisle.DAIRY_EGGS -> Icons.Outlined.EggAlt
    Aisle.PLANT_PROTEINS -> Icons.Outlined.Spa
    Aisle.STARCHES -> Icons.Outlined.RiceBowl
    Aisle.CANNED_LEGUMES -> Icons.Outlined.Inventory2
    Aisle.NUTS_SEEDS -> Icons.Outlined.Grain
    Aisle.BREAKFAST_SWEET -> Icons.Outlined.BreakfastDining
    Aisle.PANTRY -> Icons.Outlined.WaterDrop
    Aisle.SPICES -> Icons.Outlined.Grass
    Aisle.DRINKS -> Icons.Outlined.WineBar
}

/** Recherche insensible à la casse et aux accents (« oeuf » trouve « Œuf », « epinard » trouve « Épinard »). */
private fun String.normalized(): String =
    Normalizer.normalize(lowercase().replace("œ", "oe"), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()
