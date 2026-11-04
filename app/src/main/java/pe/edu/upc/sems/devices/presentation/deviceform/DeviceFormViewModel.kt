package pe.edu.upc.sems.devices.presentation.deviceform

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
import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceDraft
import pe.edu.upc.sems.devices.domain.model.DeviceTypes
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.util.withSlowNotice

/** Create a device, or edit an existing one. */
sealed interface DeviceFormMode {
    data object Create : DeviceFormMode
    data class Edit(val deviceId: String) : DeviceFormMode
}

data class SiteOption(val id: String, val name: String)

data class ZoneOption(val id: String, val name: String)

data class DeviceFormUiState(
    val isEdit: Boolean = false,
    /** True while the sites (and, when editing, the device) are being loaded. */
    val isLoadingForm: Boolean = false,
    val loadError: FormError? = null,
    val sites: List<SiteOption> = emptyList(),
    val zones: List<ZoneOption> = emptyList(),
    val siteId: String? = null,
    /** Null = "Sin asignar". */
    val zoneId: String? = null,
    val name: String = "",
    val externalCode: String = "",
    val type: String = DeviceTypes.DEFAULT,
    val typeOptions: List<String> = DeviceTypes.all,
    val protocol: ConnectionProtocol = ConnectionProtocol.WIFI,
    val brand: String = "",
    val model: String = "",
    val nameError: OrgFieldError? = null,
    val externalCodeError: OrgFieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: FormError? = null,
    val confirmingDelete: Boolean = false,
    /** One-shot: saved or deleted; the screen goes back. */
    val done: Boolean = false,
) {
    /** A device needs a site, and sites are created in "Locales". */
    val hasNoSites: Boolean get() = !isLoadingForm && loadError == null && sites.isEmpty()
}

/** "Agregar equipo" / "Editar equipo" (web: DeviceModal.vue). Only fields the backend stores. */
@HiltViewModel
class DeviceFormViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val organizations: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFormUiState())
    val uiState: StateFlow<DeviceFormUiState> = _uiState.asStateFlow()

    private var mode: DeviceFormMode? = null

    fun start(mode: DeviceFormMode) {
        if (this.mode == mode) return
        this.mode = mode
        load()
    }

    fun retryLoad() = load()

    private fun load() {
        val mode = mode ?: return
        _uiState.update { it.copy(isEdit = mode is DeviceFormMode.Edit, isLoadingForm = true, loadError = null) }
        viewModelScope.launch {
            val session = sessionManager.session.first() ?: return@launch

            val device: Device? = when (mode) {
                DeviceFormMode.Create -> null
                is DeviceFormMode.Edit -> when (val result = devices.getDevice(mode.deviceId)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> return@launch fail(result.error.toFormError())
                }
            }

            val access = when (val result = organizations.getMyOrganization(session.user.userId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return@launch fail(result.error.toFormError())
            }
            val sites = if (access == null) {
                emptyList()
            } else {
                when (val result = organizations.listSites(access.organization.id)) {
                    is ApiResult.Success ->
                        result.data.filter { it.status != OrgStatus.ARCHIVED || it.id == device?.siteId }
                            .map { SiteOption(it.id, it.name) }
                    is ApiResult.Failure -> return@launch fail(result.error.toFormError())
                }
            }

            // Same default as the rest of the app: the active site, else the first one.
            val siteId = device?.siteId
                ?: session.selectedSite?.siteId?.takeIf { id -> sites.any { it.id == id } }
                ?: sites.firstOrNull()?.id
            val zones = siteId?.let { loadZones(it) }.orEmpty()

            _uiState.update {
                it.copy(
                    isLoadingForm = false,
                    sites = sites,
                    siteId = siteId,
                    zones = zones,
                    zoneId = device?.zoneId?.takeIf { id -> zones.any { z -> z.id == id } },
                    name = device?.name ?: it.name,
                    externalCode = device?.externalCode ?: it.externalCode,
                    type = device?.type?.ifBlank { null } ?: it.type,
                    typeOptions = DeviceTypes.optionsFor(device?.type),
                    protocol = device?.protocol ?: it.protocol,
                    brand = device?.brand.orEmpty().ifEmpty { it.brand },
                    model = device?.model.orEmpty().ifEmpty { it.model },
                )
            }
        }
    }

    private fun fail(error: FormError) {
        _uiState.update { it.copy(isLoadingForm = false, loadError = error) }
    }

    /** Zones only decorate the form (they are optional): if loading them fails, the list is empty. */
    private suspend fun loadZones(siteId: String): List<ZoneOption> =
        (organizations.listZones(siteId) as? ApiResult.Success)?.data
            ?.filter { it.status != OrgStatus.ARCHIVED }
            ?.map { ZoneOption(it.id, it.name) }
            .orEmpty()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null, error = null) }

    fun onExternalCodeChange(value: String) {
        if (_uiState.value.isEdit) return
        _uiState.update { it.copy(externalCode = value, externalCodeError = null, error = null) }
    }

    fun onSiteChange(siteId: String) {
        if (_uiState.value.isEdit || siteId == _uiState.value.siteId) return
        // The zone belongs to a site: changing the site clears it and loads that site's zones.
        _uiState.update { it.copy(siteId = siteId, zoneId = null, zones = emptyList(), error = null) }
        viewModelScope.launch {
            val zones = loadZones(siteId)
            _uiState.update { if (it.siteId == siteId) it.copy(zones = zones) else it }
        }
    }

    fun onZoneChange(zoneId: String?) = _uiState.update { it.copy(zoneId = zoneId, error = null) }

    fun onTypeChange(value: String) = _uiState.update { it.copy(type = value) }

    fun onProtocolChange(value: ConnectionProtocol) = _uiState.update { it.copy(protocol = value) }

    fun onBrandChange(value: String) = _uiState.update { it.copy(brand = value, error = null) }

    fun onModelChange(value: String) = _uiState.update { it.copy(model = value, error = null) }

    fun onSubmit() {
        val state = _uiState.value
        val mode = mode ?: return
        if (state.isLoading || state.isLoadingForm) return
        val siteId = state.siteId ?: return

        val nameError = if (state.name.isBlank()) OrgFieldError.REQUIRED else null
        val codeError = if (state.externalCode.isBlank()) OrgFieldError.REQUIRED else null
        if (nameError != null || codeError != null) {
            _uiState.update { it.copy(nameError = nameError, externalCodeError = codeError) }
            return
        }

        val draft = DeviceDraft(
            externalCode = state.externalCode,
            siteId = siteId,
            zoneId = state.zoneId,
            name = state.name,
            type = state.type,
            brand = state.brand,
            model = state.model,
            protocol = state.protocol,
        )
        _uiState.update { it.copy(isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                when (mode) {
                    DeviceFormMode.Create -> devices.createDevice(userId, draft)
                    is DeviceFormMode.Edit -> devices.updateDevice(mode.deviceId, draft)
                }
            }
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, isSlow = false, done = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, isSlow = false, error = result.error.toFormError()) }
            }
        }
    }

    fun onDeleteRequest() = _uiState.update { it.copy(confirmingDelete = true) }

    fun onDeleteDismiss() = _uiState.update { it.copy(confirmingDelete = false) }

    fun onDeleteConfirm() {
        val mode = mode as? DeviceFormMode.Edit ?: return
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(confirmingDelete = false, isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                devices.deleteDevice(mode.deviceId)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, isSlow = false, done = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, isSlow = false, error = result.error.toFormError()) }
            }
        }
    }

    fun onDoneHandled() = _uiState.update { it.copy(done = false) }
}
