package fr.kalori.app.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.kalori.app.R
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.DisclaimerKind
import fr.kalori.app.core.ui.components.PlaceholderScreen

@Composable
fun MealsScreen() {
    PlaceholderScreen(title = stringResource(R.string.tab_meals)) {
        DisclaimerCard(text = stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning)
    }
}
