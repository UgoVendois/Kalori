package fr.nutrisport.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.nutrisport.app.R
import fr.nutrisport.app.core.ui.components.DisclaimerCard
import fr.nutrisport.app.core.ui.components.PlaceholderScreen

@Composable
fun SettingsScreen() {
    PlaceholderScreen(title = stringResource(R.string.tab_settings)) {
        DisclaimerCard(text = stringResource(R.string.disclaimer_not_medical))
    }
}
