package fr.kalori.app.feature.meals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.KaloriCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.NutritionTable
import fr.kalori.app.core.ui.components.Tag
import fr.kalori.app.core.ui.components.icon
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.core.ui.theme.accent
import fr.kalori.app.data.meal.MealDetail
import fr.kalori.app.data.meal.toShoppingIngredient
import fr.kalori.app.domain.meal.PrepInfo
import fr.kalori.app.domain.meal.PrepKind
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
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val editable = viewModel.editable && detail?.meal?.status != MealStatus.EATEN

    KaloriScaffold(
        title = "${viewModel.slot.label} · ${Format.dayLabel(viewModel.date, viewModel.today)}",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (editable && aiAvailable) {
                if (aiRunning) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::proposeAiVariant) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Variante proposée par l'IA")
                    }
                }
            }
        },
    ) { padding ->
        val current = detail
        if (current == null) {
            Box(Modifier.fillMaxWidth().padding(padding).padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            MealDetailContent(
                detail = current,
                editable = editable,
                canToggleEaten = !viewModel.date.isAfter(viewModel.today),
                onReplace = viewModel::replace,
                onToggleEaten = viewModel::toggleEaten,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealDetailContent(
    detail: MealDetail,
    editable: Boolean,
    canToggleEaten: Boolean,
    onReplace: () -> Unit,
    onToggleEaten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meal = detail.meal
    val accent = meal.slot.accent()
    val allergens = detail.allergensMask.toFlagSet<Allergen>()
    // Le jour où l'on cuisine pour plusieurs repas, les quantités à cuisiner sont affichées d'emblée.
    var showCook by remember(meal.date, meal.slot) { mutableStateOf(detail.cookIngredients != null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(color = accent.container, contentColor = accent.content, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(meal.slot.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(meal.slot.label.uppercase(), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(meal.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.meals_portion, Format.decimal(meal.portionFactor, 2)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (meal.aiGenerated) {
                        Tag(stringResource(R.string.meals_ai_label), accent.solid.copy(alpha = 0.15f), accent.content, icon = Icons.Outlined.AutoAwesome)
                    }
                }
            }
        }
        if (editable || canToggleEaten) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editable) {
                        OutlinedButton(onClick = onReplace, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.meals_replace))
                        }
                    }
                    if (canToggleEaten) {
                        val eaten = meal.status == MealStatus.EATEN
                        FilledTonalButton(onClick = onToggleEaten, modifier = Modifier.weight(1f)) {
                            Icon(
                                if (eaten) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (eaten) Kalori.colors.snack.solid else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (eaten) "Mangé" else "Je l'ai mangé")
                        }
                    }
                }
            }
        }
        item { PrepCard(detail) }
        if (meal.outOfSeason) {
            item { DisclaimerCard(stringResource(R.string.meals_out_of_season)) }
        }
        item {
            KaloriCard {
                Text(stringResource(R.string.meals_nutrition_title), style = MaterialTheme.typography.titleMedium)
                NutritionTable(meal.nutrition)
                Text(stringResource(R.string.meals_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            KaloriCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Kalori.colors.morning.solid, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Allergènes", style = MaterialTheme.typography.titleMedium)
                }
                if (allergens.isEmpty()) {
                    Text(stringResource(R.string.meals_allergens_none), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Contient ou peut contenir :", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allergens.forEach { Tag(it.shortLabel, Kalori.colors.morning.container, Kalori.colors.morning.content) }
                    }
                }
                DisclaimerCard(stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning)
            }
        }
        item {
            KaloriCard {
                Text(stringResource(R.string.meals_ingredients), style = MaterialTheme.typography.titleMedium)
                val cook = detail.cookIngredients
                if (cook != null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Ma portion", cookLabel(meal.prep)).forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = showCook == (index == 1),
                                onClick = { showCook = index == 1 },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
                val shown = if (showCook && cook != null) cook else detail.ingredients
                shown.forEachIndexed { index, (ingredient, grams) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            ShoppingListBuilder.formatQuantity(grams, ingredient.toShoppingIngredient()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            KaloriCard {
                Text(stringResource(R.string.meals_steps), style = MaterialTheme.typography.titleMedium)
                detail.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(accent.container),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = accent.content)
                        }
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun cookLabel(prep: PrepInfo) = when (prep.kind) {
    PrepKind.BATCH_DAY -> "Fournée (${prep.batchDays} j)"
    PrepKind.COOK_WITH_LEFTOVERS -> "Avec demain midi"
    else -> "À cuisiner"
}

/** Temps, coût, conservation et organisation (restes, fournée). */
@Composable
private fun PrepCard(detail: MealDetail) {
    val meal = detail.meal
    val kind = meal.prep.kind
    KaloriCard(spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                buildList {
                    add("${meal.prepMinutes} min")
                    add(difficultyLabel(meal.difficulty))
                    if (meal.unitCost > 0) add("≈ ${Format.euros(meal.cost)} la portion")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val advice = when (kind) {
            PrepKind.COOK_WITH_LEFTOVERS ->
                "Cuisine aussi la portion de demain midi : mets-la en boîte au réfrigérateur dès qu'elle a refroidi."
            PrepKind.LEFTOVERS ->
                "Restes du dîner d'hier : à garder au frais jusqu'au repas, puis à bien réchauffer."
            PrepKind.BATCH_DAY ->
                "Jour de fournée : prépare d'un coup les ${meal.prep.batchDays} jours de la semaine."
            PrepKind.FROM_BATCH -> "Pris dans ta fournée de la semaine : rien à préparer."
            PrepKind.COOK -> null
        }
        advice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val keep = buildList {
            if (detail.keepDays > 0) add("Se garde ${detail.keepDays} jour${if (detail.keepDays > 1) "s" else ""} une fois préparé")
            if (detail.freezable) add("se congèle")
        }
        if (keep.isNotEmpty()) {
            Text(
                keep.joinToString(", ").replaceFirstChar { it.uppercase() } + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

