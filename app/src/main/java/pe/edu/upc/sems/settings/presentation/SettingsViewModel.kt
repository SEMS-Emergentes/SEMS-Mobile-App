package pe.edu.upc.sems.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.settings.domain.AppearanceStore
import pe.edu.upc.sems.settings.domain.ThemeMode
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.session.SessionUser

/** Channel of the email preference, as the backend names it. */
internal const val EMAIL_CHANNEL = "email"

data class SettingsUiState(
    val user: SessionUser? = null,
    /** Null until the preference is read (or when it could not be read). */
    val emailEnabled: Boolean? = null,
    val emailSaving: Boolean = false,
    /** The last read or change of the email switch failed. */
    val emailFailed: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: pe.edu.upc.sems.settings.domain.AppLanguage = pe.edu.upc.sems.settings.domain.AppLanguage.DEFAULT,
)

/** "Configuración" (Figma M25; web: SettingsView.vue and the notification card of Alertas). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val alerts: AlertRepository,
    private val appearance: AppearanceStore,
    private val languages: pe.edu.upc.sems.settings.domain.LanguageStore,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Severity the saved preference already has, so changing the switch does not change it. */
    private var minSeverity: String? = null

    init {
        _uiState.update { it.copy(language = languages.get()) }
        viewModelScope.launch {
            sessionManager.session.collect { session -> _uiState.update { it.copy(user = session?.user) } }
        }
        viewModelScope.launch {
            appearance.themeMode.collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
    }

    /** Called when the screen is shown: the name may have changed in "Editar perfil". */
    fun refresh() {
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            when (val result = alerts.notificationPreferences(userId)) {
                is ApiResult.Success -> {
                    val email = result.data.firstOrNull { it.channel == EMAIL_CHANNEL }
                    minSeverity = email?.minSeverity
                    // Without a saved preference the backend sends its emails anyway, so that is "on".
                    _uiState.update { it.copy(emailEnabled = email?.enabled ?: true, emailFailed = false) }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(emailFailed = true) }
            }
        }
    }

    fun onEmailToggle(enabled: Boolean) {
        val previous = _uiState.value.emailEnabled ?: return
        if (_uiState.value.emailSaving) return
        _uiState.update { it.copy(emailEnabled = enabled, emailSaving = true, emailFailed = false) }
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            when (val result = alerts.setNotificationPreference(userId, EMAIL_CHANNEL, enabled, minSeverity)) {
                is ApiResult.Success -> {
                    minSeverity = result.data.minSeverity
                    _uiState.update { it.copy(emailEnabled = result.data.enabled, emailSaving = false) }
                }
                // The switch goes back to what is really saved, and the user is told.
                is ApiResult.Failure -> _uiState.update {
                    it.copy(emailEnabled = previous, emailSaving = false, emailFailed = true)
                }
            }
        }
    }

    /** Saves the language. The screen then recreates the activity so every text is read again in it. */
    fun onLanguageChange(language: pe.edu.upc.sems.settings.domain.AppLanguage) {
        languages.set(language)
        _uiState.update { it.copy(language = language) }
    }

    fun onThemeChange(mode: ThemeMode) {
        viewModelScope.launch { appearance.setThemeMode(mode) }
    }
}
