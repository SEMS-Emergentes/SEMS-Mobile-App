package pe.edu.upc.sems.subscriptions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
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
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.subscriptions.domain.model.PaymentCard
import pe.edu.upc.sems.subscriptions.domain.model.PendingPlanChange
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.Subscription
import pe.edu.upc.sems.subscriptions.domain.repository.PendingPlanStore
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

/** What the screen shows once loaded. */
data class SubscriptionContent(
    val subscription: Subscription?,
    /** The plan of the subscription, if it is in the catalog. */
    val currentPlan: Plan?,
    /** Every other plan of the catalog, cheapest first. */
    val otherPlans: List<Plan>,
    /** The default card, for "Próximo cobro". Null if the user has none or it could not be read. */
    val card: PaymentCard?,
    /** Active sites of the organization, for "Locales usados". Null if it could not be read. */
    val sitesUsed: Int?,
)

enum class SubscriptionMessage { PLAN_CHANGED, PAYMENT_NOT_FOUND, ACTION_FAILED, PAYMENT_START_FAILED }

data class SubscriptionUiState(
    val isLoading: Boolean = true,
    val error: FormError? = null,
    val content: SubscriptionContent? = null,
    /** The plan whose button was pressed and is being processed. */
    val busyPlanId: String? = null,
    /** The user went to pay and the payment has not shown up yet. */
    val awaitingPayment: PendingPlanChange? = null,
    /** Stripe's page to open; the screen opens it once and calls [SubscriptionViewModel.onCheckoutOpened]. */
    val checkoutUrl: String? = null,
    val message: SubscriptionMessage? = null,
)

/** A payment counts for the pending plan if it was made after the user left to pay (with a margin for clocks). */
private val CLOCK_MARGIN: Duration = Duration.ofMinutes(2)

/** "Suscripción" (Figma M24; web: SubscriptionView.vue). Prices, limits and features are the backend's. */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val organizations: OrganizationRepository,
    private val pendingStore: PendingPlanStore,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Called when the screen is shown, which is also when the user comes back from paying. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val userId = sessionManager.session.first()?.user?.userId ?: return
        if (_uiState.value.content == null) _uiState.update { it.copy(isLoading = true, error = null) }

        var data = fetch(userId)
        if (data == null) {
            _uiState.update { if (it.content == null) it.copy(isLoading = false, error = lastError) else it.copy(isLoading = false) }
            return
        }

        // The web applies the plan when Stripe sends the user back. Here the user comes back by hand, so the
        // payment the backend recorded (the webhook) is what says that it was paid.
        var message: SubscriptionMessage? = null
        val pending = pendingStore.get()
        var awaiting: PendingPlanChange? = null
        if (pending != null) {
            if (hasPaidSince(userId, pending)) {
                when (subscriptions.changePlan(userId, data.subscription?.id, pending.planId)) {
                    is ApiResult.Success -> {
                        pendingStore.clear()
                        message = SubscriptionMessage.PLAN_CHANGED
                        data = fetch(userId) ?: data
                    }
                    is ApiResult.Failure -> {
                        message = SubscriptionMessage.ACTION_FAILED
                        awaiting = pending
                    }
                }
            } else {
                awaiting = pending
            }
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                content = data,
                awaitingPayment = awaiting,
                message = message ?: it.message,
            )
        }
    }

    private var lastError: FormError = FormError.GENERIC

    private suspend fun fetch(userId: String): SubscriptionContent? = coroutineScope {
        val plans = async { subscriptions.plans() }
        val subscription = async { subscriptions.subscription(userId) }
        val cards = async { subscriptions.paymentCards(userId) }
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
            card = (cards.await() as? ApiResult.Success)?.data?.let { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() },
            sitesUsed = sites.await(),
        )
    }

    private suspend fun activeSites(userId: String): Int? {
        val access = (organizations.getMyOrganization(userId) as? ApiResult.Success)?.data ?: return null
        val sites = (organizations.listSites(access.organization.id) as? ApiResult.Success)?.data ?: return null
        return sites.count { it.status == OrgStatus.ACTIVE }
    }

    private suspend fun hasPaidSince(userId: String, pending: PendingPlanChange): Boolean {
        val payments = (subscriptions.payments(userId) as? ApiResult.Success)?.data ?: return false
        val since = pending.startedAt.minus(CLOCK_MARGIN)
        return payments.any { p ->
            val at = p.paidAt ?: p.createdAt
            p.processed && at != null && !at.isBefore(since)
        }
    }

    /**
     * "Cambiar a …" / "Mejorar plan". A paid plan goes through Stripe Checkout and is applied once the payment
     * is recorded; applying it first would leave the user with a plan they did not pay for if they give up.
     * A free plan is changed right away.
     */
    fun onChoosePlan(plan: Plan) {
        if (_uiState.value.busyPlanId != null) return
        _uiState.update { it.copy(busyPlanId = plan.id) }
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val subscriptionId = _uiState.value.content?.subscription?.id
            if (plan.isFree) {
                when (subscriptions.changePlan(userId, subscriptionId, plan.id)) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(busyPlanId = null, message = SubscriptionMessage.PLAN_CHANGED) }
                        load()
                    }
                    is ApiResult.Failure -> _uiState.update { it.copy(busyPlanId = null, message = SubscriptionMessage.ACTION_FAILED) }
                }
                return@launch
            }
            val pending = PendingPlanChange(plan.id, plan.name, java.time.Instant.now())
            pendingStore.set(pending)
            val base = BuildConfig.WEB_APP_URL
            when (val result = subscriptions.checkoutUrl(userId, subscriptionId, plan, "$base/subscription?paid=1", "$base/subscription?canceled=1")) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(busyPlanId = null, checkoutUrl = result.data, awaitingPayment = pending)
                }
                is ApiResult.Failure -> {
                    pendingStore.clear()
                    _uiState.update { it.copy(busyPlanId = null, message = SubscriptionMessage.PAYMENT_START_FAILED) }
                }
            }
        }
    }

    fun onCheckoutOpened() = _uiState.update { it.copy(checkoutUrl = null) }

    /** "Ya pagué, verificar": looks for the payment again. */
    fun onVerifyPayment() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            load()
            if (_uiState.value.awaitingPayment != null && _uiState.value.message == null) {
                _uiState.update { it.copy(message = SubscriptionMessage.PAYMENT_NOT_FOUND) }
            }
        }
    }

    /** "Descartar": the user did not pay after all. */
    fun onDiscardPending() {
        pendingStore.clear()
        _uiState.update { it.copy(awaitingPayment = null) }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
