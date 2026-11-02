package pe.edu.upc.sems.shared.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.shared.designsystem.SemsMotion
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen

/**
 * App root. The session decides which graph is shown; each graph has its own NavHost,
 * so switching graphs (login, logout, expired session) always starts with a clean back stack.
 */
@Composable
fun SemsApp(viewModel: AppViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val sessionExpired by viewModel.sessionExpired.collectAsStateWithLifecycle()
    val duration = SemsMotion.duration(SemsMotion.LONG_MS)

    Surface(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = authState,
            animationSpec = tween(duration, easing = SemsMotion.Emphasized),
            label = "authState",
        ) { state ->
            when (state) {
                AuthState.Loading -> LoadingScreen()
                AuthState.LoggedOut -> AuthNavHost(
                    sessionExpired = sessionExpired,
                    onSessionExpiredShown = viewModel::onSessionExpiredShown,
                    onDebugEnter = viewModel::enterDebugSession,
                )
                AuthState.LoggedIn -> MainNavHost(onLogout = viewModel::logout)
            }
        }
    }
}
