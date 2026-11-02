package pe.edu.upc.sems.shared.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.R
import pe.edu.upc.sems.iam.presentation.password.ForgotPasswordScreen
import pe.edu.upc.sems.iam.presentation.password.ResetLinkSentScreen
import pe.edu.upc.sems.iam.presentation.password.ResetPasswordScreen
import pe.edu.upc.sems.iam.presentation.login.LoginScreen
import pe.edu.upc.sems.iam.presentation.register.RegisterScreen
import pe.edu.upc.sems.iam.presentation.verify.VerifyAccountScreen

/** Access graph (logged out). Login succeeds by storing the session; SemsApp then shows the main graph. */
@Composable
fun AuthNavHost(
    sessionExpired: Boolean,
    onSessionExpiredShown: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val expiredMessage = stringResource(R.string.session_expired)
    val passwordChangedMessage = stringResource(R.string.reset_success)
    val scope = rememberCoroutineScope()

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
                    onForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email)) },
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
            composable<ForgotPasswordRoute> { entry ->
                ForgotPasswordScreen(
                    initialEmail = entry.toRoute<ForgotPasswordRoute>().email,
                    onBack = navController::popBackStack,
                    onLinkSent = { email ->
                        navController.navigate(ResetLinkSentRoute(email)) {
                            popUpTo<ForgotPasswordRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<ResetLinkSentRoute> { entry ->
                val email = entry.toRoute<ResetLinkSentRoute>().email
                ResetLinkSentScreen(
                    email = email,
                    onBackToLogin = { navController.popBackStack(LoginRoute, inclusive = false) },
                    onHaveLink = { navController.navigate(ResetPasswordRoute(email = email)) },
                )
            }
            composable<ResetPasswordRoute>(
                // Opens M30 from the email link once App Links are verified (assetlinks.json on the web app).
                deepLinks = listOf(navDeepLink<ResetPasswordRoute>(basePath = "${BuildConfig.WEB_APP_URL}/reset-password")),
            ) { entry ->
                val route = entry.toRoute<ResetPasswordRoute>()
                ResetPasswordScreen(
                    tokenFromLink = route.token,
                    email = route.email,
                    onBack = navController::popBackStack,
                    onPasswordChanged = {
                        navController.popBackStack(LoginRoute, inclusive = false)
                        scope.launch { snackbarHostState.showSnackbar(passwordChangedMessage) }
                    },
                )
            }
        }
    }
}
