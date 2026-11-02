package pe.edu.upc.sems.shared.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.ComingSoonScreen

/** Access graph (logged out). The iam context will add register, verify and password screens here. */
@Composable
fun AuthNavHost(
    sessionExpired: Boolean,
    onSessionExpiredShown: () -> Unit,
    onDebugEnter: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val expiredMessage = stringResource(R.string.session_expired)

    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            // Mark as shown only afterwards: changing the key first would cancel this effect
            // (and the snackbar) immediately.
            snackbarHostState.showSnackbar(expiredMessage)
            onSessionExpiredShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = LoginRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<LoginRoute> { LoginPlaceholderScreen(onDebugEnter = onDebugEnter) }
        }
    }
}

/** Temporary login placeholder; replaced by the real M00 screen in the iam task. */
@Composable
private fun LoginPlaceholderScreen(onDebugEnter: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SemsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SemsSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ComingSoonScreen(modifier = Modifier.weight(1f, fill = false))
        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = onDebugEnter) {
                Text(stringResource(R.string.login_debug_enter))
            }
        }
    }
}
