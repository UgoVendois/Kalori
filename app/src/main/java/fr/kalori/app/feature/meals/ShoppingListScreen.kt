package fr.kalori.app.feature.meals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.KaloriCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.components.SnackbarMessages
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.data.meal.ShoppingList
import fr.kalori.app.domain.meal.ShoppingItem
import fr.kalori.app.domain.model.BudgetPeriod

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    SnackbarMessages(viewModel.messages, snackbar)
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }

    KaloriScaffold(
        title = stringResource(R.string.meals_shopping),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            TextButton(onClick = viewModel::copy, enabled = list?.sections?.isNotEmpty() == true) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(" " + stringResource(R.string.shopping_copy))
            }
        },
    ) { padding ->
        val current = list
        if (current == null) {
            Row(Modifier.fillMaxWidth().padding(padding).padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@KaloriScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.shopping_period, Format.shortDate(current.from), Format.shortDate(current.to)),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (current.total > 0) {
                item { CostSummary(current, Modifier.padding(horizontal = 16.dp)) }
            }
            item {
                DisclaimerCard(
                    stringResource(R.string.disclaimer_allergens),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    kind = DisclaimerKind.Warning,
                )
            }
            if (current.sections.isEmpty()) {
                item {
                    Text(stringResource(R.string.shopping_empty), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            current.sections.forEach { section ->
                stickyHeader(key = "aisle_${section.aisle.name}") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(section.aisle.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(section.aisle.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            val remaining = section.items.count { it.ingredientId !in current.checked }
                            Text("$remaining / ${section.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "items_${section.aisle.name}") {
                    KaloriCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = 4.dp, spacing = 0.dp) {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            val checked = item.ingredientId in current.checked
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setChecked(item.ingredientId, !checked) }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { viewModel.setChecked(item.ingredientId, it) })
                                Text(
                                    item.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                                    Text(
                                        item.quantityText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    current.itemCosts[item.ingredientId]?.let { cost ->
                                        val custom = item.ingredientId in current.customPrices
                                        Text(
                                            (if (custom) "" else "≈ ") + Format.euros(cost),
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable { editing = item }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (custom) Kalori.colors.snack.solid else MaterialTheme.colorScheme.primary,
                                            textDecoration = TextDecoration.Underline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.shopping_copy_hint),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { item ->
        val current = list ?: return@let
        PriceDialog(
            item = item,
            pricePerKg = current.pricePerKg[item.ingredientId] ?: 0.0,
            custom = item.ingredientId in current.customPrices,
            onDismiss = { editing = null },
            onSave = { text -> viewModel.setPrice(item.ingredientId, text); editing = null },
            onReset = { viewModel.setPrice(item.ingredientId, ""); editing = null },
        )
    }
}

/** Total estimé de la liste, comparé au budget de la semaine. */
@Composable
private fun CostSummary(list: ShoppingList, modifier: Modifier = Modifier) {
    val budget = list.budget
    val over = budget != null && list.total > budget.perWeek
    KaloriCard(modifier = modifier, contentPadding = 14.dp, spacing = 6.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("≈ ${Format.eurosRounded(list.total)}", style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    budget == null -> "  estimés"
                    budget.period == BudgetPeriod.WEEK -> "  sur ${budget.amountEur} € de budget"
                    else -> "  (~${Format.eurosRounded(budget.fromWeek(list.total))}/mois sur ${budget.amountEur} €)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(
            "Prix moyens estimés. Touche un montant pour saisir le prix de ton magasin : " +
                "les plats suivants en tiennent compte.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PriceDialog(
    item: ShoppingItem,
    pricePerKg: Double,
    custom: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    val unit = if (item.quantityText.endsWith("ml") || item.quantityText.endsWith(" L")) "€/L" else "€/kg"
    var text by remember { mutableStateOf(Format.decimal(pricePerKg, 2)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prix : ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (custom) "Prix que tu as saisi." else "Estimation moyenne en supermarché.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                    singleLine = true,
                    suffix = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Enregistrer") } },
        dismissButton = {
            if (custom) {
                TextButton(onClick = onReset) { Text("Revenir à l'estimation") }
            } else {
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
