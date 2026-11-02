package pe.edu.upc.sems.organizations.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.Member
import pe.edu.upc.sems.organizations.domain.model.MemberGrant
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.util.withSlowNotice

/** Which site a role may have (web: MembersCard.vue; the backend validates it too). */
enum class SiteScope { FORBIDDEN, REQUIRED, OPTIONAL }

val MembershipRole.siteScope: SiteScope
    get() = when (this) {
        // An administrator runs the whole organization and cannot be tied to one site.
        MembershipRole.ORG_ADMIN -> SiteScope.FORBIDDEN
        // A supervisor without a site supervises nothing.
        MembershipRole.SUPERVISOR -> SiteScope.REQUIRED
        MembershipRole.OPERATOR, MembershipRole.UNKNOWN -> SiteScope.OPTIONAL
    }

/** "Dar acceso" bottom sheet. */
data class GrantForm(
    val email: String = "",
    /** Same default as the web app. */
    val role: MembershipRole = MembershipRole.OPERATOR,
    val siteId: String? = null,
    val emailError: MemberFieldError? = null,
    val siteError: MemberFieldError? = null,
    val isSaving: Boolean = false,
    val isSlow: Boolean = false,
    val error: FormError? = null,
)

enum class MemberFieldError { REQUIRED, INVALID_EMAIL, SITE_REQUIRED }

data class MembersUiState(
    val isLoading: Boolean = true,
    val loadError: FormError? = null,
    val members: List<Member> = emptyList(),
    /** The signed-in user, to mark "Tú" and not offer removing their own access. */
    val currentUserId: String? = null,
    val grantForm: GrantForm? = null,
    /** Member waiting for the "Quitar acceso" confirmation. */
    val confirmingRevoke: Member? = null,
    val revokingId: String? = null,
    val revokeError: FormError? = null,
)

/** "Quién tiene acceso" (web: MembersCard.vue). Only shown to ORG_ADMIN, who can grant and revoke. */
@HiltViewModel
class MembersViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    private var organizationId: String? = null
    private var loadJob: Job? = null

    fun start(organizationId: String) {
        if (this.organizationId == organizationId) return
        this.organizationId = organizationId
        refresh()
    }

    fun refresh() {
        val organizationId = organizationId ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId
            _uiState.update { it.copy(isLoading = it.members.isEmpty(), loadError = null, currentUserId = userId) }
            when (val result = repository.listMembers(organizationId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, members = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.error.toFormError()) }
            }
        }
    }

    // region Grant access

    fun onGrantClick() {
        _uiState.update { it.copy(grantForm = GrantForm()) }
    }

    fun onGrantDismiss() {
        if (_uiState.value.grantForm?.isSaving == true) return
        _uiState.update { it.copy(grantForm = null) }
    }

    fun onEmailChange(value: String) = updateForm { it.copy(email = value, emailError = null, error = null) }

    fun onRoleChange(value: MembershipRole) = updateForm {
        // Switching to administrator clears the site: the backend rejects the combination.
        it.copy(
            role = value,
            siteId = if (value.siteScope == SiteScope.FORBIDDEN) null else it.siteId,
            siteError = null,
            error = null,
        )
    }

    fun onSiteChange(siteId: String?) = updateForm { it.copy(siteId = siteId, siteError = null, error = null) }

    fun onGrantSubmit() {
        val organizationId = organizationId ?: return
        val form = _uiState.value.grantForm ?: return
        if (form.isSaving) return

        val email = form.email.trim()
        val emailError = when {
            email.isEmpty() -> MemberFieldError.REQUIRED
            !EMAIL.matches(email) -> MemberFieldError.INVALID_EMAIL
            else -> null
        }
        val siteError = if (form.role.siteScope == SiteScope.REQUIRED && form.siteId == null) MemberFieldError.SITE_REQUIRED else null
        if (emailError != null || siteError != null) {
            updateForm { it.copy(emailError = emailError, siteError = siteError) }
            return
        }

        updateForm { it.copy(isSaving = true, isSlow = false, error = null) }
        val grant = MemberGrant(
            email = email,
            role = form.role,
            siteId = form.siteId.takeIf { form.role.siteScope != SiteScope.FORBIDDEN },
        )
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { updateForm { it.copy(isSlow = true) } }) {
                repository.grantMember(organizationId, grant)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(grantForm = null) }
                    reloadMembers(organizationId)
                }
                // 404: nobody is registered with that email.
                is ApiResult.Failure ->
                    updateForm { it.copy(isSaving = false, isSlow = false, error = result.error.toFormError()) }
            }
        }
    }

    private fun updateForm(transform: (GrantForm) -> GrantForm) {
        _uiState.update { state -> state.grantForm?.let { state.copy(grantForm = transform(it)) } ?: state }
    }

    // endregion

    // region Revoke access

    fun onRevokeRequest(member: Member) {
        // Removing your own access would lock you out of the organization.
        if (member.userId == _uiState.value.currentUserId) return
        _uiState.update { it.copy(confirmingRevoke = member, revokeError = null) }
    }

    fun onRevokeDismiss() {
        _uiState.update { it.copy(confirmingRevoke = null) }
    }

    fun onRevokeConfirm() {
        val organizationId = organizationId ?: return
        val member = _uiState.value.confirmingRevoke ?: return
        _uiState.update { it.copy(confirmingRevoke = null, revokingId = member.membershipId, revokeError = null) }
        viewModelScope.launch {
            when (val result = repository.revokeMember(organizationId, member.membershipId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(revokingId = null) }
                    reloadMembers(organizationId)
                }
                // 409: the organization would be left without an administrator.
                is ApiResult.Failure ->
                    _uiState.update { it.copy(revokingId = null, revokeError = result.error.toFormError()) }
            }
        }
    }

    // endregion

    private suspend fun reloadMembers(organizationId: String) {
        val members = repository.listMembers(organizationId)
        if (members is ApiResult.Success) _uiState.update { it.copy(members = members.data) }
    }

    private companion object {
        /** Same shape the backend's @Email accepts in practice: something@domain.tld, no spaces. */
        val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    }
}
