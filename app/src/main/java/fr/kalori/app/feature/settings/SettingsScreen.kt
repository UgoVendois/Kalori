package fr.kalori.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.BuildConfig
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.IconBadge
import fr.kalori.app.core.ui.components.KaloriCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.core.ui.theme.Accent
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.data.ai.AiMealService
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.UnitSystem
import androidx.compose.foundation.layout.Row as LayoutRow

/** Actions de l'écran Paramètres (contenu sans état, testable en rendu). */
class SettingsActions(
    val onEditGoal: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onSetUnit: (UnitSystem) -> Unit,
    val onSetLock: (Boolean) -> Unit,
    val onSetAi: (Boolean) -> Unit,
    val onEditKey: () -> Unit,
    val onClearKey: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onWipe: () -> Unit,
)

@Composable
fun SettingsScreen(
    onEditGoal: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    KaloriScaffold(title = stringResource(R.string.tab_settings), snackbarHostState = snackbar) { padding ->
        SettingsContent(
            state = state,
            actions = SettingsActions(
                onEditGoal = onEditGoal,
                onOpenLicenses = onOpenLicenses,
                onSetUnit = { viewModel.setUnit(it) },
                onSetLock = { viewModel.setLock(it) },
                onSetAi = { viewModel.setAiEnabled(it) },
                onEditKey = { keyDialog = true },
                onClearKey = { viewModel.clearApiKey() },
                onExport = { exportLauncher.launch(viewModel.defaultExportName()) },
                onImport = { confirmImport = true },
                onWipe = { confirmWipe = true },
            ),
            modifier = Modifier.padding(padding),
        )
    }

    if (keyDialog) {
        ApiKeyDialog(onDismiss = { keyDialog = false }, onSave = { key -> keyDialog = false; viewModel.saveApiKey(key) })
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Importer une sauvegarde ?") },
            text = { Text("Toutes tes données actuelles seront remplacées par celles du fichier. Pense à exporter d'abord si besoin.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Choisir le fichier") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Annuler") } },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Profil, pesées, repas, goûts, séances : tout sera supprimé définitivement de ce téléphone.") },
            confirmButton = {
                TextButton(onClick = { confirmWipe = false; viewModel.wipeAll() }) {
                    Text("Tout effacer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        item {
            Group("Profil et objectif") {
                val profile = state.profile
                SettingRow(
                    icon = Icons.Outlined.Flag,
                    accent = Kalori.colors.noon,
                    title = "Mon profil et mon objectif",
                    subtitle = if (profile == null) {
                        "Non renseigné"
                    } else {
                        val goal = when (profile.goalType) {
                            GoalType.LOSE -> "Perte de ${Format.decimal(profile.paceKgPerWeek, 2)} kg/sem."
                            GoalType.GAIN -> "Prise de ${Format.decimal(profile.paceKgPerWeek, 2)} kg/sem."
                            GoalType.MAINTAIN -> "Maintien"
                        }
                        "$goal · ${profile.kcalTarget} kcal/j · cible ${Format.weight(profile.targetWeightKg, profile.unitSystem)}"
                    },
                    onClick = actions.onEditGoal,
                    trailing = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                )
                if (profile != null) {
                    RowDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LayoutRow(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.MonitorWeight, Kalori.colors.morning, size = 38.dp)
                            Text("Unité de poids", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            UnitSystem.entries.forEachIndexed { index, unit ->
                                SegmentedButton(
                                    selected = profile.unitSystem == unit,
                                    onClick = { actions.onSetUnit(unit) },
                                    shape = SegmentedButtonDefaults.itemShape(index, UnitSystem.entries.size),
                                ) { Text(if (unit == UnitSystem.METRIC) "Kilogrammes" else "Livres") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Les repas de la journée (matin, midi, soir, collation) se règlent dans Repas > Mes goûts et restrictions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        item {
            Group("Sécurité") {
                SettingRow(
                    icon = Icons.Outlined.Lock,
                    accent = Kalori.colors.evening,
                    title = "Verrouiller Kalori",
                    subtitle = if (state.lockAvailable) "Empreinte, visage ou code à l'ouverture" else "Configure d'abord un verrouillage d'écran",
                    trailing = { Switch(checked = state.lockEnabled, onCheckedChange = actions.onSetLock, enabled = state.lockAvailable || state.lockEnabled) },
                )
            }
        }

        item {
            Group("IA (optionnel)") {
                SettingRow(
                    icon = Icons.Outlined.AutoAwesome,
                    accent = Kalori.colors.evening,
                    title = "Variantes de repas par IA",
                    subtitle = if (state.aiKeySaved) "Bouton ✦ dans le détail d'un repas" else "Ajoute d'abord ta clé API Anthropic",
                    trailing = { Switch(checked = state.aiEnabled, onCheckedChange = actions.onSetAi, enabled = state.aiKeySaved) },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.Key,
                    accent = Kalori.colors.morning,
                    title = "Clé API",
                    subtitle = if (state.aiKeySaved) "Enregistrée, chiffrée sur ce téléphone" else "Non configurée",
                    onClick = actions.onEditKey,
                    trailing = { if (state.aiKeySaved) TextButton(onClick = actions.onClearKey) { Text("Supprimer") } },
                )
                Text(
                    "Modèle : ${AiMealService.MODEL}. Envoyé : la liste des ingrédients compatibles (déjà filtrée selon tes allergies, " +
                        "régimes, goûts et la saison), la cible et le budget du repas, ton niveau de cuisine, le mois et les repas de la semaine. Jamais ton poids, ton âge " +
                        "ni tes allergies. Chaque proposition est revérifiée par l'app. Quelques centimes par variante, sur ton compte Anthropic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }

        item {
            Group("Mes données") {
                SettingRow(Icons.Outlined.Download, Kalori.colors.snack, "Exporter une sauvegarde", "JSON lisible, non chiffré : garde-le en lieu sûr", onClick = actions.onExport, enabled = !state.busy)
                RowDivider()
                SettingRow(Icons.Outlined.Upload, Kalori.colors.snack, "Importer une sauvegarde", "Remplace les données actuelles", onClick = actions.onImport, enabled = !state.busy)
                RowDivider()
                SettingRow(
                    Icons.Outlined.DeleteForever,
                    Accent(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.error),
                    "Tout effacer",
                    "Supprime tout et revient au premier lancement",
                    onClick = actions.onWipe,
                    enabled = !state.busy,
                    titleColor = MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            Group("Informations") {
                SettingRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    accent = Kalori.colors.noon,
                    title = "Sources et licences",
                    subtitle = "Ciqual (Anses), recommandations, bibliothèques",
                    onClick = actions.onOpenLicenses,
                    trailing = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                )
            }
        }
        item { DisclaimerCard(stringResource(R.string.disclaimer_not_medical)) }
        item {
            Text(
                "Kalori ${BuildConfig.VERSION_NAME} · usage personnel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        KaloriCard(contentPadding = 0.dp, spacing = 0.dp, content = content)
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
private fun SettingRow(
    icon: ImageVector,
    accent: Accent,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    titleColor: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { IconBadge(icon, accent, size = 38.dp) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    )
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clé API Anthropic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Crée une clé sur console.anthropic.com. Elle est chiffrée sur ce téléphone et n'est jamais exportée.")
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    label = { Text("sk-ant-…") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }, enabled = key.length > 20) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
