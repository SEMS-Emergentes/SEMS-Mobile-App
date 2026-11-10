package pe.edu.upc.sems.subscriptions.presentation

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
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.subscriptions.domain.model.CardInput
import pe.edu.upc.sems.subscriptions.domain.model.DeclineReason
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PurchaseOutcome
import pe.edu.upc.sems.subscriptions.domain.model.Subscription
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

/** What the screen shows once loaded. */
data class SubscriptionContent(
    val subscription: Subscription?,
    /** The plan of the subscription, if it is in the catalog. */
    val currentPlan: Plan?,
    /** Every other plan of the catalog, cheapest first. */
    val otherPlans: List<Plan>,
    /** Active sites of the organization, for "Locales usados". Null if it could not be read. */
    val sitesUsed: Int?,
)

enum class SubscriptionMessage { PLAN_CHANGED, ACTION_FAILED }

/** Por que no se acepto la tarjeta; el dialogo lo muestra sin cerrarse. */
enum class CardError { INCOMPLETE_NUMBER, BAD_EXPIRY, BAD_CVC, INVALID, DECLINED, INSUFFICIENT_FUNDS, FAILED }

data class SubscriptionUiState(
    val isLoading: Boolean = true,
    val error: FormError? = null,
    val content: SubscriptionContent? = null,
    /** The free plan whose button was pressed and is being applied. */
    val busyPlanId: String? = null,
    /** Plan de pago cuyo formulario de tarjeta esta abierto. */
    val paying: Plan? = null,
    val purchasing: Boolean = false,
    val cardError: CardError? = null,
    val message: SubscriptionMessage? = null,
)

/** "Suscripción" (Figma M24; web: SubscriptionView.vue). Prices, limits and features are the backend's. */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val organizations: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val userId = sessionManager.session.first()?.user?.userId ?: return
        if (_uiState.value.content == null) _uiState.update { it.copy(isLoading = true, error = null) }

        val data = fetch(userId)
        _uiState.update {
            when {
                data != null -> it.copy(isLoading = false, error = null, content = data)
                it.content == null -> it.copy(isLoading = false, error = lastError)
                else -> it.copy(isLoading = false)
            }
        }
    }

    private var lastError: FormError = FormError.GENERIC

    private suspend fun fetch(userId: String): SubscriptionContent? = coroutineScope {
        val plans = async { subscriptions.plans() }
        val subscription = async { subscriptions.subscription(userId) }
        val sites = async { activeSites(userId) }

        val catalog = when (val result = plans.await()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> { lastError = result.error.toFormError(); return@coroutineScope null }
        }
        val current = when (val result = subscription.await()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> { lastError = result.error.toFormError(); return@coroutineScope null }
        }
        val currentPlan = current?.let { s -> catalog.firstOrNull { it.id == s.planId } }
        SubscriptionContent(
            subscription = current,
            currentPlan = currentPlan,
            otherPlans = catalog.filter { it.id != current?.planId }.sortedBy { it.priceSoles },
            sitesUsed = sites.await(),
        )
    }

    private suspend fun activeSites(userId: String): Int? {
        val access = (organizations.getMyOrganization(userId) as? ApiResult.Success)?.data ?: return null
        val sites = (organizations.listSites(access.organization.id) as? ApiResult.Success)?.data ?: return null
        return sites.count { it.status == OrgStatus.ACTIVE }
    }

    /**
     * "Cambiar a …" / "Mejorar plan". Un plan de pago abre el formulario de tarjeta; uno gratuito se contrata
     * directo. Antes el de pago salia a Stripe y se aplicaba al volver, con lo que bastaba volver para tenerlo.
     */
    fun onChoosePlan(plan: Plan) {
        if (_uiState.value.busyPlanId != null || _uiState.value.purchasing) return
        if (!plan.isFree) {
            _uiState.update { it.copy(paying = plan, cardError = null) }
            return
        }
        _uiState.update { it.copy(busyPlanId = plan.id) }
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            when (subscriptions.purchase(userId, plan.id, card = null)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busyPlanId = null, message = SubscriptionMessage.PLAN_CHANGED) }
                    load()
                }
                is ApiResult.Failure -> _uiState.update { it.copy(busyPlanId = null, message = SubscriptionMessage.ACTION_FAILED) }
            }
        }
    }

    /**
     * "Pagar" del formulario. [expiry] va como MM/AA. Lo evidente se revisa aqui para no gastar una llamada;
     * el resto (digito de control, vencida) lo decide el backend.
     */
    fun onPay(number: String, expiry: String, cvc: String) {
        val plan = _uiState.value.paying ?: return
        if (_uiState.value.purchasing) return
        val digits = number.filter(Char::isDigit)
        val parts = expiry.split("/").map { it.trim() }
        val month = parts.getOrNull(0)?.toIntOrNull()
        val year = parts.getOrNull(1)?.takeIf { it.length == 2 }?.toIntOrNull()?.plus(2000)
        val error = when {
            digits.length < 13 -> CardError.INCOMPLETE_NUMBER
            month == null || month !in 1..12 || year == null -> CardError.BAD_EXPIRY
            !cvc.matches(Regex("\\d{3,4}")) -> CardError.BAD_CVC
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(cardError = error) }
            return
        }
        _uiState.update { it.copy(purchasing = true, cardError = null) }
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val result = subscriptions.purchase(userId, plan.id, CardInput(digits, month!!, year!!, cvc))
            when (result) {
                is ApiResult.Success -> when (val outcome = result.data) {
                    PurchaseOutcome.Purchased -> {
                        _uiState.update {
                            it.copy(purchasing = false, paying = null, message = SubscriptionMessage.PLAN_CHANGED)
                        }
                        load()
                    }
                    is PurchaseOutcome.Declined -> _uiState.update {
                        it.copy(
                            purchasing = false,
                            cardError = if (outcome.reason == DeclineReason.INSUFFICIENT_FUNDS) CardError.INSUFFICIENT_FUNDS else CardError.DECLINED,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    val invalid = (result.error as? ApiError.Http)?.code == 400
                    it.copy(purchasing = false, cardError = if (invalid) CardError.INVALID else CardError.FAILED)
                }
            }
        }
    }

    fun onDismissPayment() {
        if (_uiState.value.purchasing) return
        _uiState.update { it.copy(paying = null, cardError = null) }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
