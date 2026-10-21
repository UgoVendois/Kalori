package fr.kalori.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fr.kalori.app.core.ui.components.KaloriScaffold

/** Sources des données, recommandations utilisées et licences (dont l'avis exigé par SQLCipher). */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun asset(name: String) = runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
    val sqlcipher = remember { asset("sqlcipher.txt") }
    val jakarta = remember { asset("plus_jakarta_sans_ofl.txt") }

    KaloriScaffold(title = "Sources et licences", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Title("Données nutritionnelles")
            Body(
                "Source : Anses, Table de composition nutritionnelle des aliments Ciqual 2025 (fichier du 03/11/2025), " +
                    "DOI 10.57745/RDMHWY. Licence Ouverte / Etalab 2.0. Les valeurs sont reprises sans modification pour 100 g " +
                    "de partie comestible ; les valeurs des plats sont calculées à partir de leurs ingrédients.",
            )
            Title("Allergènes")
            Body(
                "Liste des 14 allergènes du règlement (UE) n° 1169/2011, annexe II. L'affectation aux ingrédients est faite " +
                    "pour Kalori, de façon prudente (traces fréquentes incluses). Elle ne remplace pas l'étiquette des produits.",
            )
            Title("Recommandations utilisées pour les objectifs")
            Body(
                "NICE CG189 et NHS (rythme de perte), HAS (surpoids et obésité de l'adulte), Anses 2016 (références " +
                    "nutritionnelles en macronutriments), OMS (IMC), Iraki et al. 2019, Sports 7(7):154 (rythme de prise), " +
                    "équation de Mifflin-St Jeor (1990). Ce sont des estimations : Kalori ne remplace pas un professionnel de santé.",
            )
            Title("Contenus")
            Body(
                "Recettes, descriptions d'exercices, séances pré-faites, calendrier de saisonnalité et pictogrammes : " +
                    "créations originales pour Kalori. Icônes d'interface : Material Icons (Apache 2.0).",
            )
            Title("Bibliothèques")
            Body(
                "AndroidX (Compose, Room, Navigation, DataStore, Lifecycle, Biometric), Kotlin et kotlinx " +
                    "(coroutines, serialization), Dagger/Hilt, Google Tink : licence Apache 2.0.",
            )
            Title("SQLCipher for Android")
            Text(sqlcipher, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Title("Police Plus Jakarta Sans")
            Text(jakarta, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)
