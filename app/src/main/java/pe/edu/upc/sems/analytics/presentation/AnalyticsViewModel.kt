package pe.edu.upc.sems.analytics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.analytics.domain.repository.AnalyticsRepository
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

/** The two tabs of the screen (M17 / M18 are Recomendaciones, M19 is Top consumidores). */
enum class AnalyticsTab { RECOMMENDATIONS, TOP_CONSUMERS }

/** The "Filtrar" options of the comparison between sites: same 7 / 30 / 90 days as the web. */
enum class ComparisonPeriod(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

/** One-shot messages shown in the snackbar. */
enum class AnalyticsMessage { APPLIED, ACTION_FAILED }

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    /** The plan does not include analytics: the screen shows the lock instead of the data. */
    val locked: Boolean = false,
    val error: FormError? = null,
    val tab: AnalyticsTab = AnalyticsTab.RECOMMENDATIONS,
    val forecast: MonthForecast? = null,
    /** Null when they could not be loaded. */
    val recommendations: List<Recommendation>? = null,
    /** Recommendations being applied: their buttons are disabled. */
    val busyIds: Set<String> = emptySet(),
    val period: ComparisonPeriod = ComparisonPeriod.MONTH,
    val comparisonLoading: Boolean = false,
    /** Null when they could not be loaded. */
    val siteRows: List<SiteRow>? = null,
    /** Null when they could not be loaded. */
    val ranking: List<RankingRow>? = null,
    val message: AnalyticsMessage? = null,
)

/** What analytics needs from the plan. Same rule as the web: Business or above. */
internal val REQUIRED_TIER = PlanTier.BUSINESS

/** "Analítica" (Figma M17, M18, M19; web: AnalyticsView.vue). Every figure is what the backend sent. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val subscriptions: SubscriptionRepository,
    private val devices: DeviceRepository,
    private val organizations: OrganizationRepository,
    private val energy: EnergyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var organizationId: String? = null
    private var siteNames: Map<String, String> = emptyMap()

    /** Called when the screen is shown, so it reflects what the backend generated since. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val firstLoad = _uiState.value.recommendations == null && !_uiState.value.locked
            if (firstLoad) _uiState.update { it.copy(isLoading = true, error = null) }

            val tier = when (val result = subscriptions.currentTier(userId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.toFormError()) }
                    return@launch
                }
            }
            if (!tier.includes(REQUIRED_TIER)) {
                _uiState.update { it.copy(isLoading = false, locked = true, error = null) }
                return@launch
            }

            val loaded = coroutineScope {
                val recommendations = async { analytics.recommendations(userId) }
                val predictions = async { analytics.billPredictions(userId) }
                val ranking = async { analytics.latestRanking(userId) }
                val deviceList = async { devices.listDevices(userId) }
                val organization = async { organizations.getMyOrganization(userId) }
                Loaded(recommendations.await(), predictions.await(), ranking.await(), deviceList.await(), organization.await())
            }

            val access = (loaded.organization as? ApiResult.Success)?.data
            organizationId = access?.organization?.id
            val sites = access?.let { (organizations.listSites(it.organization.id) as? ApiResult.Success)?.data }.orEmpty()
            siteNames = sites.associate { it.id to it.name }
            val deviceList = (loaded.devices as? ApiResult.Success)?.data.orEmpty()
                .filter { it.status != DeviceStatus.REMOVED }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    locked = false,
                    error = null,
                    forecast = (loaded.predictions as? ApiResult.Success)?.data?.let(::monthForecast),
                    recommendations = (loaded.recommendations as? ApiResult.Success)?.data
                        ?.sortedByDescending { r -> r.generatedAt ?: java.time.Instant.MIN },
                    ranking = (loaded.ranking as? ApiResult.Success)?.let { r -> rankingRows(r.data, deviceList, siteNames) },
                )
            }
            loadComparison(_uiState.value.period)
        }
    }

    fun onTabChange(tab: AnalyticsTab) = _uiState.update { it.copy(tab = tab) }

    fun onPeriodChange(period: ComparisonPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period) }
        viewModelScope.launch { loadComparison(period) }
    }

    /** "Aplicar": the backend marks the recommendation as applied. It cannot be undone, so there is no "Deshacer". */
    fun onApply(recommendationId: String) {
        if (recommendationId in _uiState.value.busyIds) return
        _uiState.update { it.copy(busyIds = it.busyIds + recommendationId) }
        viewModelScope.launch {
            when (val result = analytics.applyRecommendation(recommendationId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        busyIds = state.busyIds - recommendationId,
                        recommendations = state.recommendations?.map { if (it.id == recommendationId) result.data else it },
                        message = AnalyticsMessage.APPLIED,
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(busyIds = it.busyIds - recommendationId, message = AnalyticsMessage.ACTION_FAILED)
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** The last [period] days per site and, from twice as many days, the window before to measure the change. */
    private suspend fun loadComparison(period: ComparisonPeriod) {
        val organization = organizationId
        if (organization == null) {
            _uiState.update { it.copy(siteRows = emptyList(), comparisonLoading = false) }
            return
        }
        _uiState.update { it.copy(comparisonLoading = true) }
        val (recent, doubled) = coroutineScope {
            val recent = async { energy.siteComparison(organization, period.days) }
            val doubled = async { energy.siteComparison(organization, 2 * period.days) }
            recent.await() to doubled.await()
        }
        // The user may have picked another period while this one was loading.
        if (_uiState.value.period != period) return
        _uiState.update {
            it.copy(
                comparisonLoading = false,
                siteRows = (recent as? ApiResult.Success)?.let { r ->
                    siteRows(r.data, (doubled as? ApiResult.Success)?.data, siteNames)
                },
            )
        }
    }

    private class Loaded(
        val recommendations: ApiResult<List<Recommendation>>,
        val predictions: ApiResult<List<pe.edu.upc.sems.analytics.domain.model.BillPrediction>>,
        val ranking: ApiResult<pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking?>,
        val devices: ApiResult<List<pe.edu.upc.sems.devices.domain.model.Device>>,
        val organization: ApiResult<pe.edu.upc.sems.organizations.domain.model.OrganizationAccess?>,
    )
}
