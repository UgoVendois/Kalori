package fr.kalori.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.kalori.app.core.ui.Format
import fr.kalori.app.core.ui.theme.Accent
import fr.kalori.app.core.ui.theme.Kalori
import fr.kalori.app.core.ui.theme.accent
import fr.kalori.app.domain.model.MealSlot
import fr.kalori.app.domain.model.Nutrition
import kotlin.math.roundToInt

/** Carte de base : fond clair, coins arrondis, fine bordure en mode clair, sans ombre. */
@Composable
fun KaloriCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding: Dp = 16.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = if (isSystemInDarkTheme()) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border, content = inner)
    }
}

/** Titre de section, avec action facultative à droite. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action()
    }
}

/** Pastille ronde colorée avec une icône. */
@Composable
fun IconBadge(icon: ImageVector, accent: Accent, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(accent.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent.content, modifier = Modifier.size(size * 0.55f))
    }
}

fun MealSlot.icon(): ImageVector = when (this) {
    MealSlot.MORNING -> Icons.Outlined.BakeryDining
    MealSlot.NOON -> Icons.Outlined.LightMode
    MealSlot.EVENING -> Icons.Outlined.DarkMode
    MealSlot.SNACK -> Icons.Outlined.Cookie
}

/** En-tête d'un repas : pastille colorée + nom du créneau + valeur à droite. */
@Composable
fun SlotHeader(slot: MealSlot, trailing: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(slot.icon(), slot.accent(), size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            slot.label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = slot.accent().solid,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ligne compacte des nutriments : kcal en gras, puis protéines, lipides, glucides et fibres. */
@Composable
fun NutritionLine(nutrition: Nutrition, modifier: Modifier = Modifier, showKcal: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showKcal) {
            Text(Format.kcal(nutrition.kcal), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        MacroDot(Kalori.colors.protein, "P", nutrition.protein)
        MacroDot(Kalori.colors.fat, "L", nutrition.fat)
        MacroDot(Kalori.colors.carbs, "G", nutrition.carbs)
        MacroDot(MaterialTheme.colorScheme.outline, "F", nutrition.fiber)
    }
}

@Composable
private fun MacroDot(color: Color, letter: String, grams: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$letter ${grams.roundToInt()} g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Barre de répartition de l'énergie entre protéines, lipides et glucides (avec écarts de 2 dp). */
@Composable
fun MacroBar(nutrition: Nutrition, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p = nutrition.protein * 4
    val f = nutrition.fat * 9
    val c = nutrition.carbs * 4
    val total = (p + f + c).takeIf { it > 0 } ?: return
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(p to Kalori.colors.protein, f to Kalori.colors.fat, c to Kalori.colors.carbs).forEach { (value, color) ->
            if (value > 0) {
                Box(Modifier.weight((value / total).toFloat()).height(height).clip(RoundedCornerShape(50)).background(color))
            }
        }
    }
}

/** Petite étiquette arrondie (statut, info). */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** État vide ou d'accueil : grande pastille, titre, texte. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, accent: Accent = Kalori.colors.noon) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon, accent, size = 72.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
