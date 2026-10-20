package fr.nutrisport.app.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.nutrisport.app.R
import fr.nutrisport.app.core.ui.components.DisclaimerCard
import fr.nutrisport.app.core.ui.components.DisclaimerKind
import fr.nutrisport.app.core.ui.components.PlaceholderScreen

@Composable
fun MealsScreen() {
    PlaceholderScreen(title = stringResource(R.string.tab_meals)) {
        DisclaimerCard(text = stringResource(R.string.disclaimer_allergens), kind = DisclaimerKind.Warning)
    }
}
