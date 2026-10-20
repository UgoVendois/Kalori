package fr.nutrisport.app.feature.tracking

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.nutrisport.app.R
import fr.nutrisport.app.core.ui.components.DisclaimerCard
import fr.nutrisport.app.core.ui.components.PlaceholderScreen

@Composable
fun TrackingScreen() {
    PlaceholderScreen(title = stringResource(R.string.tab_tracking)) {
        DisclaimerCard(text = stringResource(R.string.disclaimer_not_medical))
    }
}
