package pe.edu.upc.sems.shared.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.SessionEvent
import pe.edu.upc.sems.shared.session.SessionEvents
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.session.SessionUser

sealed interface AuthState {
    /** Session not read from disk yet: show a loading screen, not the login (avoids a flash). */
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

/** App-level state: which graph to show, and the "session expired" message. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    sessionEvents: SessionEvents,
) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionManager.session
        .map { if (it == null) AuthState.LoggedOut else AuthState.LoggedIn }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _sessionExpired = MutableStateFlow(false)

    /** True until the login screen has shown the "Tu sesión expiró" message. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        // Single collector of SessionEvents for the whole app.
        viewModelScope.launch {
            sessionEvents.events.collect { event ->
                if (event is SessionEvent.Expired) _sessionExpired.value = true
            }
        }
    }

    fun onSessionExpiredShown() {
        _sessionExpired.value = false
    }

    /** Local logout only. POST /auth/logout is added with the iam context. */
    fun logout() {
        viewModelScope.launch { sessionManager.clear() }
    }

    /**
     * Debug-only shortcut to reach the main graph before the real login exists.
     * Temporary: removed when the iam login screen is built.
     */
    fun enterDebugSession() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            sessionManager.saveLogin(
                tokens = AuthTokens(token = "debug-token", refreshToken = "debug-refresh-token"),
                user = SessionUser(
                    userId = "debug-user",
                    emailAddress = "debug@sems.local",
                    fullName = null,
                    roles = emptySet(),
                    segment = null,
                ),
            )
        }
    }
}
