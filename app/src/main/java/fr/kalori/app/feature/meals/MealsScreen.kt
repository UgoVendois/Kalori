package fr.kalori.app.feature.meals

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.EmptyState
import fr.kalori.app.core.ui.components.IconBadge
import fr.kalori.app.core.ui.components.KaloriCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.MacroBar
import fr.kalori.app.core.ui.components.NutritionLine
import fr.kalori.app.core.ui.components.SlotHeader
import fr.kalori.app.core.ui.components.SnackbarMessages
import fr.kalori.app.core.ui.components.Tag
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.data.meal.DayMeal
import fr.kalori.app.domain.meal.Budget
import fr.kalori.app.domain.meal.PrepKind
import fr.kalori.app.domain.model.BudgetPeriod
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.MealStatus
import fr.kalori.app.domain.model.Nutrition
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MealsScreen(
    onOpenMeal: (DayMeal) -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShopping: () -> Unit,
    viewModel: MealsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    SnackbarMessages(viewModel.messages, snackbar)

    KaloriScaffold(
        title = stringResource(R.string.tab_meals),
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = onOpenShopping) { Icon(Icons.Outlined.ShoppingCart, contentDescription = stringResource(R.string.meals_shopping)) }
            IconButton(onClick = onOpenHistory) { Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.meals_history)) }
            IconButton(onClick = onOpenPreferences) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.meals_preferences)) }
        },
    ) { padding ->
        MealsContent(
            state = state,
            onSelectDate = viewModel::selectDate,
            onOpenMeal = onOpenMeal,
            onReplace = { viewModel.replace(it) },
            onToggleEaten = { viewModel.toggleEaten(it) },
            onOpenPreferences = onOpenPreferences,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Contenu sans état (prévisualisable et capturé dans les tests de rendu). */
@Composable
fun MealsContent(
    state: MealsUiState,
    onSelectDate: (LocalDate) -> Unit,
    onOpenMeal: (DayMeal) -> Unit,
    onReplace: (DayMeal) -> Unit,
    onToggleEaten: (DayMeal) -> Unit,
    onOpenPreferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            !state.hasProfile -> item {
                EmptyState(
                    icon = Icons.Outlined.RestaurantMenu,
                    title = "Tes repas t'attendent",
                    text = stringResource(R.string.meals_no_profile),
                )
            }
            else -> {
                item { DaySelector(state.dates, state.selectedDate, state.today, state.meals, onSelectDate) }
                item { DaySummary(state.selectedMeals, state.dailyKcal) }
                item { WeekBudget(state.weekCost, state.budget, onOpenPreferences) }
                val meals = state.selectedMeals.associateBy { it.slot }
                state.slotTargets.keys.sortedBy { it.ordinal }.forEach { slot ->
                    item(key = "${state.selectedDate}_$slot") {
                        val meal = meals[slot]
                        if (meal != null) {
                            MealCard(meal, onOpen = { onOpenMeal(meal) }, onReplace = { onReplace(meal) }, onToggleEaten = { onToggleEaten(meal) })
                        } else {
                            PendingMealCard(slot, state.slotTargets[slot]?.kcal, state.planning)
                        }
                    }
                }
                item {
                    TextButton(onClick = onOpenPreferences, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.meals_preferences))
                    }
                }
                item { DisclaimerCard(stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning) }
            }
        }
    }
}

/** Bandeau des 7 jours. Un point vert signale un jour où tout a été mangé. */
@Composable
private fun DaySelector(
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    meals: Map<LocalDate, List<DayMeal>>,
    onSelect: (LocalDate) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(dates, key = { it.toEpochDay() }) { date ->
            val isSelected = date == selected
            val allEaten = meals[date].orEmpty().let { it.isNotEmpty() && it.all { m -> m.status == MealStatus.EATEN } }
            Surface(
                onClick = { onSelect(date) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.width(56.dp).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        if (date == today) "Auj." else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge)
                    Box(
                        Modifier.size(6.dp).clip(CircleShape).background(
                            when {
                                allEaten && isSelected -> MaterialTheme.colorScheme.onPrimary
                                allEaten -> Kalori.colors.snack.solid
                                else -> Color.Transparent
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** Récapitulatif de la journée : prévu, mangé, objectif, répartition des macros. */
@Composable
private fun DaySummary(meals: List<DayMeal>, dailyKcal: Int) {
    val planned = meals.fold(Nutrition.ZERO) { acc, m -> acc + m.nutrition }
    val eaten = meals.filter { it.status == MealStatus.EATEN }.sumOf { it.nutrition.kcal }
    KaloriCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Total de la journée", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${planned.kcal.roundToInt()}", style = MaterialTheme.typography.headlineLarge)
                    Text(" / $dailyKcal kcal", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Mangé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${eaten.roundToInt()} kcal", style = MaterialTheme.typography.titleMedium, color = Kalori.colors.snack.solid, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(
            progress = { if (dailyKcal > 0) (eaten / dailyKcal).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Kalori.colors.snack.solid,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        MacroBar(planned, height = 6.dp)
        NutritionLine(planned, showKcal = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(meal: DayMeal, onOpen: () -> Unit, onReplace: () -> Unit, onToggleEaten: () -> Unit) {
    val eaten = meal.status == MealStatus.EATEN
    KaloriCard(onClick = onOpen) {
        SlotHeader(meal.slot, Format.kcal(meal.nutrition.kcal))
        Text(meal.name, style = MaterialTheme.typography.titleLarge)
        PrepLine(meal)
        val prepTag = meal.prepTag()
        if (meal.aiGenerated || meal.outOfSeason || prepTag != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                prepTag?.let { (text, icon) -> Tag(text, Kalori.colors.snack.container, Kalori.colors.snack.content, icon = icon) }
                if (meal.aiGenerated) {
                    Tag(stringResource(R.string.meals_ai_label), Kalori.colors.evening.container, Kalori.colors.evening.content, icon = Icons.Outlined.AutoAwesome)
                }
                if (meal.outOfSeason) {
                    Tag(stringResource(R.string.meals_out_of_season_short), Kalori.colors.morning.container, Kalori.colors.morning.content, icon = Icons.Outlined.WbSunny)
                }
            }
        }
        NutritionLine(meal.nutrition, showKcal = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onReplace, enabled = !eaten) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.meals_replace))
            }
            Spacer(Modifier.weight(1f))
            if (eaten) {
                FilledTonalButton(onClick = onToggleEaten) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Kalori.colors.snack.solid)
                    Spacer(Modifier.width(6.dp))
                    Text("Mangé")
                }
            } else {
                OutlinedButton(onClick = onToggleEaten) {
                    Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Je l'ai mangé")
                }
            }
        }
    }
}

/** Temps, difficulté et coût de la portion ; rien à cuisiner pour des restes ou une fournée déjà prête. */
@Composable
private fun PrepLine(meal: DayMeal) {
    val nothingToCook = meal.prep.kind == PrepKind.LEFTOVERS || meal.prep.kind == PrepKind.FROM_BATCH
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            if (nothingToCook) Icons.Outlined.LunchDining else Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val parts = buildList {
            if (nothingToCook) add("Rien à cuisiner") else add("${meal.prepMinutes} min · ${difficultyLabel(meal.difficulty)}")
            if (meal.unitCost > 0) add("≈ ${Format.euros(meal.cost)}")
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun difficultyLabel(difficulty: Int) = when (difficulty) {
    1 -> "simple"
    2 -> "normal"
    else -> "élaboré"
}

/** Étiquette d'organisation : restes, portion pour demain midi, fournée. */
fun DayMeal.prepTag(): Pair<String, ImageVector>? = when (prep.kind) {
    PrepKind.COOK -> null
    PrepKind.COOK_WITH_LEFTOVERS -> "+ 1 portion pour demain midi" to Icons.Outlined.LunchDining
    PrepKind.LEFTOVERS -> "Restes d'hier soir" to Icons.Outlined.LunchDining
    PrepKind.BATCH_DAY -> "Fournée pour ${prep.batchDays} jours" to Icons.Outlined.Kitchen
    PrepKind.FROM_BATCH -> "De ta fournée" to Icons.Outlined.Kitchen
}

/** Courses estimées des 7 jours, comparées au budget (à la semaine ou ramené au mois). */
@Composable
private fun WeekBudget(cost: Double, budget: Budget?, onOpenPreferences: () -> Unit) {
    if (cost <= 0.0) return
    val over = budget != null && cost > budget.perWeek
    KaloriCard(contentPadding = 14.dp, spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.ShoppingCart, if (over) Kalori.colors.noon else Kalori.colors.snack, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Courses des 7 jours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("≈ ${Format.eurosRounded(cost)}", style = MaterialTheme.typography.titleLarge)
                if (budget != null) {
                    val text = when (budget.period) {
                        BudgetPeriod.WEEK -> "sur ${budget.amountEur} € de budget par semaine"
                        BudgetPeriod.MONTH -> "soit ~${Format.eurosRounded(budget.fromWeek(cost))} par mois, sur ${budget.amountEur} € de budget"
                    }
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (budget == null) TextButton(onClick = onOpenPreferences) { Text("Fixer un budget") }
        }
        if (budget != null) {
            LinearProgressIndicator(
                progress = { (cost / budget.perWeek).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (over) MaterialTheme.colorScheme.error else Kalori.colors.snack.solid,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            if (over) {
                Text(
                    "Au-dessus de ton budget : vérifie la gamme de prix dans Mes goûts et restrictions, " +
                        "corrige les prix dans la liste de courses ou remplace un plat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            "Estimation à partir de prix moyens en supermarché.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PendingMealCard(slot: MealSlot, kcal: Int?, planning: Boolean) {
    KaloriCard {
        SlotHeader(slot, kcal?.let { "$it kcal" })
        if (planning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.meals_preparing), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(stringResource(R.string.meals_no_dish), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
        }
    }
}
