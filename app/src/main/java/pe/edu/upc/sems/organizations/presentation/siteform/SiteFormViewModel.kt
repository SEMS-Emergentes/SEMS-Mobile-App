package pe.edu.upc.sems.organizations.presentation.siteform

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
import pe.edu.upc.sems.organizations.domain.model.SiteDraft
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.parseDecimal
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.util.withSlowNotice

data class SiteFormUiState(
    val siteCode: String = "",
    val name: String = "",
    val address: String = "",
    val district: String = "",
    val floorAreaM2: String = "",
    val contractedPowerKw: String = "",
    /** Same default as the web app (SiteModal.vue). */
    val tariffCategory: TariffCategory = TariffCategory.BT5B,
    val excludesSundaysFromPeak: Boolean = false,
    val siteCodeError: OrgFieldError? = null,
    val nameError: OrgFieldError? = null,
    val floorAreaError: OrgFieldError? = null,
    val contractedPowerError: OrgFieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: FormError? = null,
    /** One-shot: the site was saved; the screen goes back. */
    val done: Boolean = false,
)

/**
 * "Nuevo local" (web: SiteModal.vue). Supply data (contracted power, tariff category) belongs to the site.
 * The first site created becomes the active site.
 */
@HiltViewModel
class SiteFormViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteFormUiState())
    val uiState: StateFlow<SiteFormUiState> = _uiState.asStateFlow()

    private var organizationId: String? = null

    fun start(organizationId: String) {
        this.organizationId = organizationId
    }

    fun onSiteCodeChange(value: String) {
        _uiState.update { it.copy(siteCode = value.uppercase(), siteCodeError = null, error = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = null, error = null) }
    }

    fun onAddressChange(value: String) {
        _uiState.update { it.copy(address = value, error = null) }
    }

    fun onDistrictChange(value: String) {
        _uiState.update { it.copy(district = value, error = null) }
    }

    fun onFloorAreaChange(value: String) {
        _uiState.update { it.copy(floorAreaM2 = value, floorAreaError = null, error = null) }
    }

    fun onContractedPowerChange(value: String) {
        _uiState.update { it.copy(contractedPowerKw = value, contractedPowerError = null, error = null) }
    }

    fun onTariffCategoryChange(value: TariffCategory) {
        _uiState.update { it.copy(tariffCategory = value) }
    }

    fun onExcludesSundaysChange(value: Boolean) {
        _uiState.update { it.copy(excludesSundaysFromPeak = value) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val organizationId = organizationId ?: return
        if (state.isLoading) return

        val power = parseDecimal(state.contractedPowerKw)
        val area = parseDecimal(state.floorAreaM2)
        val siteCodeError = if (state.siteCode.isBlank()) OrgFieldError.REQUIRED else null
        val nameError = if (state.name.isBlank()) OrgFieldError.REQUIRED else null
        val contractedPowerError = when {
            state.contractedPowerKw.isBlank() -> OrgFieldError.REQUIRED
            power == null -> OrgFieldError.INVALID_NUMBER
            power <= 0.0 -> OrgFieldError.MUST_BE_POSITIVE
            else -> null
        }
        val floorAreaError = when {
            state.floorAreaM2.isBlank() -> null
            area == null -> OrgFieldError.INVALID_NUMBER
            area <= 0.0 -> OrgFieldError.MUST_BE_POSITIVE
            else -> null
        }
        if (listOf(siteCodeError, nameError, contractedPowerError, floorAreaError).any { it != null }) {
            _uiState.update {
                it.copy(
                    siteCodeError = siteCodeError,
                    nameError = nameError,
                    contractedPowerError = contractedPowerError,
                    floorAreaError = floorAreaError,
                )
            }
            return
        }

        val draft = SiteDraft(
            siteCode = state.siteCode,
            name = state.name,
            address = state.address,
            district = state.district,
            floorAreaM2 = area,
            contractedPowerKw = power!!,
            tariffCategory = state.tariffCategory,
            excludesSundaysFromPeak = state.excludesSundaysFromPeak,
        )
        _uiState.update { it.copy(isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                repository.createSite(organizationId, draft)
            }
            when (result) {
                is ApiResult.Success -> {
                    val site = result.data
                    // The first site becomes the active one, so the app always knows which site the user is in.
                    if (sessionManager.session.first()?.selectedSite == null) {
                        sessionManager.selectSite(SelectedSite(site.organizationId, site.id, site.name))
                    }
                    _uiState.update { it.copy(isLoading = false, isSlow = false, done = true) }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, isSlow = false, error = result.error.toFormError()) }
            }
        }
    }

    fun onDoneHandled() {
        _uiState.update { it.copy(done = false) }
    }
}
