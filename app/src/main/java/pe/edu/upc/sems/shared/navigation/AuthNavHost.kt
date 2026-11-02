package pe.edu.upc.sems.shared.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pe.edu.upc.sems.R
import pe.edu.upc.sems.iam.presentation.login.LoginScreen
import pe.edu.upc.sems.iam.presentation.register.RegisterScreen
import pe.edu.upc.sems.iam.presentation.verify.VerifyAccountScreen
import pe.edu.upc.sems.shared.designsystem.components.ComingSoonScreen

/** Access graph (logged out). Login succeeds by storing the session; SemsApp then shows the main graph. */
@Composable
fun AuthNavHost(
    sessionExpired: Boolean,
    onSessionExpiredShown: () -> Unit,
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
            enterTransition = semsEnterTransition(),
            exitTransition = semsExitTransition(),
            popEnterTransition = semsEnterTransition(),
            popExitTransition = semsExitTransition(),
        ) {
            composable<LoginRoute> {
                LoginScreen(
                    onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                    onCreateAccount = { navController.navigate(RegisterRoute) { launchSingleTop = true } },
                    onVerifyAccount = { email -> navController.navigate(VerifyAccountRoute(email)) },
                )
            }
            // A successful sign-up stores the session, so the app leaves this graph on its own.
            composable<RegisterRoute> {
                RegisterScreen(
                    onBack = navController::popBackStack,
                    onLogin = { navController.popBackStack(LoginRoute, inclusive = false) },
                )
            }
            composable<VerifyAccountRoute> { entry ->
                VerifyAccountScreen(
                    email = entry.toRoute<VerifyAccountRoute>().email,
                    onClose = { navController.popBackStack(LoginRoute, inclusive = false) },
                )
            }
            // Placeholder until the password task of iam.
            composable<ForgotPasswordRoute> { AuthPlaceholder(onBack = navController::popBackStack) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthPlaceholder(onBack: () -> Unit) {
    Column {
        TopAppBar(
            // The access graph's Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            },
        )
        ComingSoonScreen()
    }
}
