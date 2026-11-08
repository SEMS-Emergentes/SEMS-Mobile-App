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
import pe.edu.upc.sems.iam.domain.repository.ProfileRepository
import pe.edu.upc.sems.iam.domain.repository.UserSegment
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

/** The backend accepts up to 120 characters for the name. */
const val MAX_NAME_LENGTH = 120

/** Segment the web starts from when the user has not chosen one. */
internal val DEFAULT_SEGMENT = UserSegment.RETAIL_CHAIN

data class EditProfileUiState(
    val loaded: Boolean = false,
    val email: String = "",
    val name: String = "",
    val segment: UserSegment = DEFAULT_SEGMENT,
    /** Values the form started from, to know whether there is something to save. */
    val originalName: String = "",
    val originalSegment: UserSegment = DEFAULT_SEGMENT,
    val nameError: Boolean = false,
    val isSaving: Boolean = false,
    val error: FormError? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = loaded && !isSaving && (name.trim() != originalName || segment != originalSegment)
}

/** "Editar perfil": the name and segment of the user (web: SettingsView.vue, "Datos de la cuenta" and "Segmento"). */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = sessionManager.session.first()?.user ?: return@launch
            val segment = UserSegment.from(user.segment) ?: DEFAULT_SEGMENT
            _uiState.value = EditProfileUiState(
                loaded = true,
                email = user.emailAddress,
                name = user.fullName.orEmpty(),
                segment = segment,
                originalName = user.fullName.orEmpty(),
                originalSegment = segment,
            )
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value.take(MAX_NAME_LENGTH), nameError = false, error = null) }

    fun onSegmentChange(value: UserSegment) = _uiState.update { it.copy(segment = value, error = null) }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        val name = state.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = profile.updateProfile(name, state.segment)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, saved = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error.toFormError()) }
            }
        }
    }
}
