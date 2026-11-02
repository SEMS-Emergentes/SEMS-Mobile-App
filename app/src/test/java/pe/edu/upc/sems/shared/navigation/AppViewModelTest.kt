package pe.edu.upc.sems.shared.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.iam.FakeAuthRepository
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.SessionEvents
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

/**
 * Regression test for: after opening the app with a reset-password link, logging in and logging out
 * showed M30 instead of the login, because the link stayed in Activity.intent and every new access
 * graph processed it again. The link is now handed to AppViewModel once and consumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sessionManager = FakeSessionManager()
    // Lazy: AppViewModel starts its flows on Dispatchers.Main in the constructor, after the rule replaces it.
    private val viewModel by lazy { AppViewModel(sessionManager, SessionEvents(), FakeAuthRepository()) }

    private val resetLink = "${BuildConfig.WEB_APP_URL}/reset-password?token=tok-1"
    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)

    @Test
    fun `reset link is delivered once and does not come back after login and logout`() = runTest(mainRule.dispatcher) {
        viewModel
        advanceUntilIdle()
        assertEquals(AuthState.LoggedOut, viewModel.authState.value)

        viewModel.onLaunchLink(resetLink)
        assertEquals("tok-1", viewModel.pendingResetToken.value)

        // AuthNavHost opens M30 and consumes it.
        viewModel.onResetTokenConsumed()
        assertNull(viewModel.pendingResetToken.value)

        // Log in, then log out: the access graph is recreated and must start at the login.
        sessionManager.saveLogin(AuthTokens("jwt", "refresh"), user)
        advanceUntilIdle()
        assertEquals(AuthState.LoggedIn, viewModel.authState.value)
        logOut()

        assertEquals(AuthState.LoggedOut, viewModel.authState.value)
        assertNull(viewModel.pendingResetToken.value)
    }

    /** What AuthRepositoryImpl.logout() does locally: clear the session (the fake repository does nothing). */
    private suspend fun kotlinx.coroutines.test.TestScope.logOut() {
        sessionManager.clear()
        advanceUntilIdle()
    }

    @Test
    fun `reset link received while logged in is dropped`() = runTest(mainRule.dispatcher) {
        viewModel
        sessionManager.saveLogin(AuthTokens("jwt", "refresh"), user)
        advanceUntilIdle()
        assertEquals(AuthState.LoggedIn, viewModel.authState.value)

        viewModel.onLaunchLink(resetLink)
        advanceUntilIdle()
        // Still logged in: the token must not wait for the next logout.
        assertNull(viewModel.pendingResetToken.value)

        logOut()
        assertEquals(AuthState.LoggedOut, viewModel.authState.value)
        assertNull(viewModel.pendingResetToken.value)
    }

    @Test
    fun `links that are not for the app are ignored`() = runTest(mainRule.dispatcher) {
        viewModel
        advanceUntilIdle()
        viewModel.onLaunchLink("${BuildConfig.WEB_APP_URL}/login")
        viewModel.onLaunchLink(null)
        assertNull(viewModel.pendingResetToken.value)
    }
}
