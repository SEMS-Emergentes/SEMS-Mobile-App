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
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.shared.session.SessionEvent
import pe.edu.upc.sems.shared.session.SessionEvents
import pe.edu.upc.sems.shared.session.SessionManager

sealed interface AuthState {
    /** Session not read from disk yet: show a loading screen, not the login (avoids a flash). */
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

/**
 * Who is logged in, for the navigation drawer header and footer.
 * [organizationName] stays null until the organizations context exists ("Sin organización").
 * [isPlatformAdmin] is the IAM account role (ADMIN or STAFF); it will be replaced by the
 * membership role (Administrador, Supervisor, Operario) with organizations.
 */
data class DrawerAccount(
    val displayName: String,
    val isPlatformAdmin: Boolean,
    val organizationName: String? = null,
)

/** App-level state: which graph to show, the drawer account and the "session expired" message. */
@HiltViewModel
class AppViewModel @Inject constructor(
    sessionManager: SessionManager,
    sessionEvents: SessionEvents,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionManager.session
        .map { if (it == null) AuthState.LoggedOut else AuthState.LoggedIn }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    val drawerAccount: StateFlow<DrawerAccount?> = sessionManager.session
        .map { session ->
            session?.user?.let { user ->
                DrawerAccount(
                    displayName = user.fullName ?: user.emailAddress,
                    isPlatformAdmin = ADMIN_ROLE in user.roles,
                )
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessionExpired = MutableStateFlow(false)

    /** True until the login screen has shown the "Tu sesión expiró" message. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private val _pendingResetToken = MutableStateFlow<String?>(null)

    /**
     * Token of a reset-password link that opened the app, waiting to be shown once in M30.
     * Consumed with [onResetTokenConsumed]; never restored, so a later logout always lands on the login.
     */
    val pendingResetToken: StateFlow<String?> = _pendingResetToken.asStateFlow()

    init {
        // Single collector of SessionEvents for the whole app.
        viewModelScope.launch {
            sessionEvents.events.collect { event ->
                if (event is SessionEvent.Expired) _sessionExpired.value = true
            }
        }
        // A reset link that arrives while logged in is dropped: it must not show up after the next logout.
        viewModelScope.launch {
            authState.collect { state ->
                if (state == AuthState.LoggedIn) _pendingResetToken.value = null
            }
        }
    }

    /** Called by MainActivity once per launch intent (not on recreation). Ignores links that are not for the app. */
    fun onLaunchLink(link: String?) {
        // Already logged in: drop it now (the collector in init only reacts to a change to LoggedIn,
        // which covers a cold start where the session is still loading).
        if (authState.value == AuthState.LoggedIn) return
        AppLinks.resetPasswordToken(link)?.let { _pendingResetToken.value = it }
    }

    fun onResetTokenConsumed() {
        _pendingResetToken.value = null
    }

    fun onSessionExpiredShown() {
        _sessionExpired.value = false
    }

    /** Revokes the refresh token on the backend (best effort) and clears the session. */
    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private companion object {
        /** IAM RoleName values are ADMIN (platform administrator) and STAFF (default). */
        const val ADMIN_ROLE = "ADMIN"
    }
}
