package pe.edu.upc.sems.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class DashboardContent(
    val siteName: String,
    val aggregation: SiteZoneConsumption?,
    val meterCount: Int?,
    val error: FormError?,
) {
    val backendCostSoles: Double? get() {
        val costs = aggregation?.let { value ->
            value.zones.mapNotNull { it.estimatedEnergyCostSoles } +
                listOfNotNull(value.unassigned?.estimatedEnergyCostSoles)
        }.orEmpty()
        return costs.takeIf { it.isNotEmpty() }?.sum()
    }
}

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object NoSite : DashboardUiState
    data class Success(val content: DashboardContent) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val energy: EnergyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val site = sessionManager.session.first()?.selectedSite
            if (site == null) {
                _uiState.value = DashboardUiState.NoSite
                return@launch
            }
            val aggregate = async { energy.siteZoneConsumption(site.siteId, 30) }
            val meters = async { energy.listMetersBySite(site.siteId) }
            val aggregateResult = aggregate.await()
            val meterResult = meters.await()
            val error = (aggregateResult as? ApiResult.Failure)?.error?.toFormError()
                ?: (meterResult as? ApiResult.Failure)?.error?.toFormError()
            _uiState.value = DashboardUiState.Success(
                DashboardContent(
                    siteName = site.siteName,
                    aggregation = (aggregateResult as? ApiResult.Success)?.data,
                    meterCount = (meterResult as? ApiResult.Success)?.data?.size,
                    error = error,
                ),
            )
        }
    }
}
