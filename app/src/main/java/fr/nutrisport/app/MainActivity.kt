package fr.nutrisport.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.nutrisport.app.core.ui.theme.NutriSportTheme
import fr.nutrisport.app.navigation.NutriSportRoot

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriSportTheme {
                NutriSportRoot()
            }
        }
    }
}
