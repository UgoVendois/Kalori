package fr.kalori.app.feature.tracking

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.kalori.app.R
import fr.kalori.app.core.ui.components.DisclaimerCard
import fr.kalori.app.core.ui.components.PlaceholderScreen

@Composable
fun TrackingScreen() {
    PlaceholderScreen(title = stringResource(R.string.tab_tracking)) {
        DisclaimerCard(text = stringResource(R.string.disclaimer_not_medical))
    }
}
