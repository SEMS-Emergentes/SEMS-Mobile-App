package pe.edu.upc.sems.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.reports.domain.LIMA
import pe.edu.upc.sems.reports.domain.ReportPreset
import pe.edu.upc.sems.reports.domain.ReportRange
import pe.edu.upc.sems.reports.domain.reportRange
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

data class ReportsUiState(
    val isLoading: Boolean = true,
    /** The plan does not include reports: the screen shows the lock. */
    val locked: Boolean = false,
    val error: FormError? = null,
    val preset: ReportPreset = ReportPreset.LAST_MONTH,
    val custom: ReportRange? = null,
    val today: LocalDate = LocalDate.now(LIMA),
) {
    /** The days the report will cover; null only for a custom range not picked yet. */
    val range: ReportRange? get() = reportRange(preset, today, custom)
}

/** The plan level reports need. Same rule as the web: Business or above. */
internal val REPORTS_REQUIRED_TIER = PlanTier.BUSINESS

/** "Reportes PDF" (Figma M20, M21; web: ReportsView.vue). */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    /** Called when the screen is shown: the plan may have changed since. */
    fun refresh() {
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            if (_uiState.value.isLoading) _uiState.update { it.copy(error = null) }
            when (val result = subscriptions.currentTier(userId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        locked = !result.data.includes(REPORTS_REQUIRED_TIER),
                        today = LocalDate.now(LIMA),
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error.toFormError()) }
            }
        }
    }

    fun onPresetChange(preset: ReportPreset) = _uiState.update { it.copy(preset = preset) }

    fun onCustomRange(from: LocalDate, to: LocalDate) =
        _uiState.update { it.copy(preset = ReportPreset.CUSTOM, custom = ReportRange(minOf(from, to), maxOf(from, to))) }
}
