package fr.kalori.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class DisclaimerKind { Info, Warning }

/** Encadré d'avertissement réutilisé partout (pas un dispositif médical, allergènes, estimations). */
@Composable
fun DisclaimerCard(
    text: String,
    modifier: Modifier = Modifier,
    kind: DisclaimerKind = DisclaimerKind.Info,
) {
    val colors = when (kind) {
        DisclaimerKind.Info -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DisclaimerKind.Warning -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Card(modifier = modifier.fillMaxWidth(), colors = colors) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (kind == DisclaimerKind.Warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
            )
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
