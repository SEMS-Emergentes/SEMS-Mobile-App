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
import pe.edu.upc.sems.shared.util.Formatters
import pe.edu.upc.sems.shared.util.withSlowNotice

/** Create a site in an organization, or edit an existing one. */
sealed interface SiteFormMode {
    data class Create(val organizationId: String) : SiteFormMode
    data class Edit(val siteId: String) : SiteFormMode
}

data class SiteFormUiState(
    val isEdit: Boolean = false,
    /** Edit mode: true while the site is being loaded. */
    val isLoadingSite: Boolean = false,
    val loadError: FormError? = null,
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
 * "Nuevo local" / "Editar local" (web: SiteModal.vue). Supply data (contracted power, tariff category) belongs to the site.
 * The first site created becomes the active site.
 */
@HiltViewModel
class SiteFormViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteFormUiState())
    val uiState: StateFlow<SiteFormUiState> = _uiState.asStateFlow()

    private var mode: SiteFormMode? = null

    fun start(mode: SiteFormMode) {
        if (this.mode == mode) return
        this.mode = mode
        if (mode is SiteFormMode.Edit) loadSite(mode.siteId)
    }

    /** Edit mode: fills the form with the current values. The site code cannot be changed. */
    private fun loadSite(siteId: String) {
        _uiState.update { it.copy(isEdit = true, isLoadingSite = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.getSite(siteId)) {
                is ApiResult.Success -> {
                    val site = result.data
                    _uiState.update {
                        it.copy(
                            isLoadingSite = false,
                            siteCode = site.siteCode,
                            name = site.name,
                            address = site.address.orEmpty(),
                            district = site.district.orEmpty(),
                            floorAreaM2 = site.floorAreaM2?.let(Formatters::editable).orEmpty(),
                            contractedPowerKw = Formatters.editable(site.contractedPowerKw),
                            tariffCategory = site.tariffCategory,
                            excludesSundaysFromPeak = site.excludesSundaysFromPeak,
                        )
                    }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoadingSite = false, loadError = result.error.toFormError()) }
            }
        }
    }

    fun retryLoad() {
        (mode as? SiteFormMode.Edit)?.let { loadSite(it.siteId) }
    }

    fun onSiteCodeChange(value: String) {
        if (_uiState.value.isEdit) return
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
        val mode = mode ?: return
        if (state.isLoading || state.isLoadingSite) return

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
                when (mode) {
                    is SiteFormMode.Create -> repository.createSite(mode.organizationId, draft)
                    is SiteFormMode.Edit -> repository.updateSite(mode.siteId, draft)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    val site = result.data
                    val active = sessionManager.session.first()?.selectedSite
                    when {
                        // The first site becomes the active one, so the app always knows which site the user is in.
                        active == null -> sessionManager.selectSite(SelectedSite(site.organizationId, site.id, site.name))
                        // Renamed active site: keep the cached name in sync.
                        active.siteId == site.id && active.siteName != site.name ->
                            sessionManager.selectSite(active.copy(siteName = site.name))
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
