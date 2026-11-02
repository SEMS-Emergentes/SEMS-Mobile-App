package pe.edu.upc.sems

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import pe.edu.upc.sems.shared.designsystem.SemsTheme
import pe.edu.upc.sems.shared.navigation.AppViewModel
import pe.edu.upc.sems.shared.navigation.SemsApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only for a fresh launch: after a rotation or process death the system hands back the original
        // intent, and the link was already processed (the ViewModel or the saved back stack keeps the result).
        if (savedInstanceState == null) consumeLaunchLink(intent)
        enableEdgeToEdge()
        setContent {
            SemsTheme {
                SemsApp()
            }
        }
    }

    /** singleTop: a link opened while the app is in front arrives here instead of creating another instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeLaunchLink(intent)
    }

    /**
     * Hands the link to AppViewModel and removes it from the intent. Each NavController reads
     * Activity.intent when its graph is set, and the access graph is recreated after every logout:
     * leaving the link there would reopen M30 instead of the login.
     */
    private fun consumeLaunchLink(intent: Intent) {
        val link = intent.dataString ?: return
        appViewModel.onLaunchLink(link)
        intent.data = null
        setIntent(intent)
    }
}
