package pe.edu.upc.sems.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.analytics.domain.repository.AnalyticsRepository
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.reports.domain.LIMA
import pe.edu.upc.sems.reports.domain.ReportData
import pe.edu.upc.sems.reports.domain.ReportFiles
import pe.edu.upc.sems.reports.domain.ReportRange
import pe.edu.upc.sems.reports.domain.buildReport
import pe.edu.upc.sems.reports.domain.reportFileName
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

sealed interface ReportPreviewUiState {
    data object Loading : ReportPreviewUiState
    data class Error(val error: FormError) : ReportPreviewUiState

    /** [fileUri] is where the PDF was saved; null if saving failed. */
    data class Ready(val data: ReportData, val fileName: String, val fileUri: String?) : ReportPreviewUiState
}

enum class ReportMessage { SAVED, SAVE_FAILED }

/** A notice for the snackbar. It is a class (not a value) so two equal notices in a row are still two events. */
class ReportEvent(val kind: ReportMessage)

/** "Vista previa" (Figma M22): builds the report of the chosen days, saves the PDF and shows its first page. */
@HiltViewModel
class ReportPreviewViewModel @Inject constructor(
    private val organizations: OrganizationRepository,
    private val energy: EnergyRepository,
    private val alerts: AlertRepository,
    private val analytics: AnalyticsRepository,
    private val files: ReportFiles,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportPreviewUiState>(ReportPreviewUiState.Loading)
    val uiState: StateFlow<ReportPreviewUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<ReportEvent?>(null)

    /** Each event is a new object, so saving again shows the notice again even if the last one is still up. */
    val message: StateFlow<ReportEvent?> = _message.asStateFlow()

    private var loaded = false

    /** Loads once per screen; "Reintentar" calls it again. */
    fun load(from: LocalDate, to: LocalDate, force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        _uiState.value = ReportPreviewUiState.Loading
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val range = ReportRange(from, to)

            val access = when (val result = organizations.getMyOrganization(userId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return@launch fail(result.error.toFormError())
            }
            if (access == null) return@launch fail(FormError.NOT_FOUND)

            val loaded = coroutineScope {
                val sites = async { organizations.listSites(access.organization.id) }
                val meters = async { energy.listMeters(userId) }
                val readings = async {
                    energy.userReadingsInRange(userId, from.atStartOfDay(LIMA).toInstant(), to.plusDays(1).atStartOfDay(LIMA).toInstant())
                }
                val alertList = async { alerts.listAlerts(userId) }
                val recommendations = async { analytics.recommendations(userId) }
                Loaded(sites.await(), meters.await(), readings.await(), alertList.await(), recommendations.await())
            }

            // The figures of the report are the point: if any of them is missing, it is better to say so.
            val sites = when (val r = loaded.sites) { is ApiResult.Success -> r.data; is ApiResult.Failure -> return@launch fail(r.error.toFormError()) }
            val meters = when (val r = loaded.meters) { is ApiResult.Success -> r.data; is ApiResult.Failure -> return@launch fail(r.error.toFormError()) }
            val readings = when (val r = loaded.readings) { is ApiResult.Success -> r.data; is ApiResult.Failure -> return@launch fail(r.error.toFormError()) }
            // Alerts and recommendations only add two lines to the page: without them those lines say nothing.
            val alertList = (loaded.alerts as? ApiResult.Success)?.data.orEmpty()
            val recommendations = (loaded.recommendations as? ApiResult.Success)?.data.orEmpty()

            val data = buildReport(
                organizationName = access.organization.tradeName ?: access.organization.legalName,
                sites = sites,
                meters = meters,
                readings = readings,
                alerts = alertList,
                recommendations = recommendations,
                range = range,
                today = LocalDate.now(LIMA),
            )
            val fileName = reportFileName(range)
            _uiState.value = ReportPreviewUiState.Ready(data, fileName, fileUri = null)
            save(data, fileName)
        }
    }

    /** The download icon: saves another copy. */
    fun onDownloadAgain() {
        val ready = _uiState.value as? ReportPreviewUiState.Ready ?: return
        viewModelScope.launch { save(ready.data, ready.fileName) }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private suspend fun save(data: ReportData, fileName: String) {
        val uri = files.savePdf(data, fileName)
        _uiState.update { state -> if (state is ReportPreviewUiState.Ready) state.copy(fileUri = uri ?: state.fileUri) else state }
        _message.value = ReportEvent(if (uri != null) ReportMessage.SAVED else ReportMessage.SAVE_FAILED)
    }

    private fun fail(error: FormError) {
        _uiState.value = ReportPreviewUiState.Error(error)
    }

    private class Loaded(
        val sites: ApiResult<List<pe.edu.upc.sems.organizations.domain.model.Site>>,
        val meters: ApiResult<List<pe.edu.upc.sems.energy.domain.model.EnergyMeter>>,
        val readings: ApiResult<List<pe.edu.upc.sems.energy.domain.model.EnergyReading>>,
        val alerts: ApiResult<List<pe.edu.upc.sems.alerts.domain.model.Alert>>,
        val recommendations: ApiResult<List<pe.edu.upc.sems.analytics.domain.model.Recommendation>>,
    )
}
