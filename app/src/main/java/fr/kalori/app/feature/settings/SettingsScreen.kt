package fr.kalori.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kalori.app.BuildConfig
import fr.kalori.app.R
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.KaloriScaffold
import fr.kalori.app.data.ai.AiMealService
import fr.kalori.app.domain.model.GoalType
import fr.kalori.app.domain.model.UnitSystem
import fr.kalori.app.domain.nutrition.MacroCalculator
import kotlin.math.roundToInt

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
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item { Section("Profil et objectif") }
            item {
                val profile = state.profile
                ListItem(
                    headlineContent = { Text("Mon profil et mon objectif") },
                    supportingContent = {
                        Text(
                            if (profile == null) {
                                "Non renseigné"
                            } else {
                                val goal = when (profile.goalType) {
                                    GoalType.LOSE -> "Perte de ${Format.decimal(profile.paceKgPerWeek, 2)} kg/semaine"
                                    GoalType.GAIN -> "Prise de ${Format.decimal(profile.paceKgPerWeek, 2)} kg/semaine"
                                    GoalType.MAINTAIN -> "Maintien"
                                }
                                "$goal · ${profile.kcalTarget} kcal par jour · cible ${Format.weight(profile.targetWeightKg, profile.unitSystem)}"
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    modifier = Modifier.clickableRow(onClick = onEditGoal),
                )
            }
            state.profile?.let { profile ->
                item { MealShareSetting(profile.mealShare, profile.kcalTarget, viewModel::setMealShare) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unité de poids", style = MaterialTheme.typography.bodyLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            UnitSystem.entries.forEachIndexed { index, unit ->
                                SegmentedButton(
                                    selected = profile.unitSystem == unit,
                                    onClick = { viewModel.setUnit(unit) },
                                    shape = SegmentedButtonDefaults.itemShape(index, UnitSystem.entries.size),
                                ) { Text(if (unit == UnitSystem.METRIC) "Kilogrammes" else "Livres") }
                            }
                        }
                    }
                }
            }

            item { Section("Sécurité") }
            item {
                ListItem(
                    headlineContent = { Text("Verrouiller Kalori") },
                    supportingContent = {
                        Text(
                            if (state.lockAvailable) {
                                "Empreinte, visage ou code du téléphone à l'ouverture, et après 30 s en arrière-plan."
                            } else {
                                "Configure d'abord un verrouillage d'écran sur ce téléphone."
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = state.lockEnabled,
                            onCheckedChange = viewModel::setLock,
                            enabled = state.lockAvailable || state.lockEnabled,
                        )
                    },
                )
            }
            item {
                Text(
                    "Tes données restent sur ce téléphone, dans une base chiffrée, et ne sont pas sauvegardées dans le cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Section("IA (optionnel)") }
            item {
                ListItem(
                    headlineContent = { Text("Variantes de plats par IA") },
                    supportingContent = {
                        Text(
                            if (state.aiKeySaved) {
                                "Bouton « Variante IA » dans le détail d'un plat."
                            } else {
                                "Ajoute d'abord ta clé API Anthropic personnelle."
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = state.aiEnabled, onCheckedChange = viewModel::setAiEnabled, enabled = state.aiKeySaved)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clé API") },
                    supportingContent = { Text(if (state.aiKeySaved) "Enregistrée, chiffrée sur ce téléphone" else "Non configurée") },
                    leadingContent = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    trailingContent = {
                        if (state.aiKeySaved) TextButton(onClick = viewModel::clearApiKey) { Text("Supprimer") }
                    },
                    modifier = Modifier.clickableRow { keyDialog = true },
                )
            }
            item {
                Text(
                    "Modèle : ${AiMealService.MODEL}. Envoyé à Anthropic : la liste des ingrédients compatibles (déjà filtrée " +
                        "selon tes allergies, régimes, goûts et la saison), la cible du plat (kcal, protéines), le mois, tes aliments " +
                        "aimés et les noms des plats de la semaine. Jamais ton poids, ton âge ni tes allergies. Chaque proposition " +
                        "est revérifiée et recalculée par l'app avant d'être retenue, et marquée « Généré par IA ». " +
                        "Coût facturé sur ton compte Anthropic : quelques centimes par variante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Section("Mes données") }
            item {
                ListItem(
                    headlineContent = { Text("Exporter une sauvegarde") },
                    supportingContent = { Text("Fichier JSON lisible : profil, pesées, plats, séances. Non chiffré, garde-le en lieu sûr.") },
                    leadingContent = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    modifier = Modifier.clickableRow(enabled = !state.busy) { exportLauncher.launch(viewModel.defaultExportName()) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Importer une sauvegarde") },
                    supportingContent = { Text("Remplace les données actuelles par celles du fichier.") },
                    leadingContent = { Icon(Icons.Outlined.Upload, contentDescription = null) },
                    modifier = Modifier.clickableRow(enabled = !state.busy) { confirmImport = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Tout effacer", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Supprime toutes tes données de ce téléphone et revient au premier lancement.") },
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickableRow(enabled = !state.busy) { confirmWipe = true },
                )
            }

            item { Section("Informations") }
            item {
                ListItem(
                    headlineContent = { Text("Sources et licences") },
                    supportingContent = { Text("Données nutritionnelles Ciqual (Anses), recommandations, bibliothèques") },
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    modifier = Modifier.clickableRow(onClick = onOpenLicenses),
                )
            }
            item { DisclaimerCard(stringResource(R.string.disclaimer_not_medical), modifier = Modifier.padding(16.dp)) }
            item {
                Text(
                    "Kalori ${BuildConfig.VERSION_NAME} · usage personnel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (keyDialog) {
        ApiKeyDialog(
            onDismiss = { keyDialog = false },
            onSave = { key -> keyDialog = false; viewModel.saveApiKey(key) },
        )
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
            text = { Text("Profil, pesées, plats, goûts, séances : tout sera supprimé définitivement de ce téléphone.") },
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

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

/** Curseur 30–50 % : appliqué au relâchement (recalcul des portions des plats à venir). */
@Composable
private fun MealShareSetting(current: Double, dailyKcal: Int, onChange: (Double) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Part du plat du jour", style = MaterialTheme.typography.bodyLarge)
            Text("${(value * 100).roundToInt()} % · ${(dailyKcal * value).roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toDouble()) },
            valueRange = MacroCalculator.MIN_MEAL_SHARE.toFloat()..MacroCalculator.MAX_MEAL_SHARE.toFloat(),
            steps = 3,
        )
        Text(
            "Le reste de ta journée est libre : saisis-le dans Suivi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Modifier.clickableRow(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(enabled = enabled, onClick = onClick))
