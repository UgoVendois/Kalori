package fr.kalori.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.kalori.app.core.ui.theme.Kalori

enum class DisclaimerKind { Info, Warning }

/** Encadré d'avertissement réutilisé partout (pas un dispositif médical, allergènes, estimations). */
@Composable
fun DisclaimerCard(
    text: String,
    modifier: Modifier = Modifier,
    kind: DisclaimerKind = DisclaimerKind.Info,
) {
    val accent = Kalori.colors.morning
    val (container, content) = when (kind) {
        DisclaimerKind.Info -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        DisclaimerKind.Warning -> accent.container to accent.content
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (kind == DisclaimerKind.Warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
