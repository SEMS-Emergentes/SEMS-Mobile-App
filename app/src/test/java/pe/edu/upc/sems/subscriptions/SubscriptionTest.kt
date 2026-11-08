package pe.edu.upc.sems.subscriptions

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.subscriptions.FakeSubscriptionRepository.Companion.BUSINESS
import pe.edu.upc.sems.subscriptions.FakeSubscriptionRepository.Companion.ENTERPRISE
import pe.edu.upc.sems.subscriptions.FakeSubscriptionRepository.Companion.STARTER
import pe.edu.upc.sems.subscriptions.FakeSubscriptionRepository.Companion.payment
import pe.edu.upc.sems.subscriptions.data.mapper.toDomain
import pe.edu.upc.sems.subscriptions.data.remote.PaymentDto
import pe.edu.upc.sems.subscriptions.data.remote.PlanDto
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionDto
import pe.edu.upc.sems.subscriptions.domain.model.PaymentCard
import pe.edu.upc.sems.subscriptions.domain.model.PendingPlanChange
import pe.edu.upc.sems.subscriptions.domain.model.SiteLimit
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus
import pe.edu.upc.sems.subscriptions.presentation.SubscriptionMessage
import pe.edu.upc.sems.subscriptions.presentation.SubscriptionViewModel
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val json = Json { ignoreUnknownKeys = true }

    // region the backend's contract

    @Test
    fun `a plan is read from the backend's PascalCase JSON and its Stripe price id stays hidden`() {
        val raw = """{"PlanID":"p1","Name":"Business","Price":149.0,"BillingPeriod":"monthly","PlanFeatures":[
            {"FeatureCode":"SITES_LIMIT","FeatureName":"Sites included","FeatureValue":"5"},
            {"FeatureCode":"STRIPE_PRICE_ID","FeatureName":"Stripe price id","FeatureValue":"price_123"}]}"""

        val plan = json.decodeFromString(PlanDto.serializer(), raw).toDomain()

        assertEquals(149.0, plan.priceSoles, 1e-9)
        assertEquals(SiteLimit.Limited(5), plan.siteLimit)
        assertEquals(listOf("SITES_LIMIT"), plan.features.map { it.code })
        assertFalse(plan.yearly)
        assertFalse(plan.isFree)
    }

    @Test
    fun `unlimited sites and a free price are understood`() {
        val plan = PlanDto(
            planId = "p", name = "Enterprise", price = 0.0,
            features = listOf(pe.edu.upc.sems.subscriptions.data.remote.PlanFeatureDto("SITES_LIMIT", "Sites included", "unlimited")),
        ).toDomain()

        assertEquals(SiteLimit.Unlimited, plan.siteLimit)
        assertTrue(plan.isFree)
    }

    @Test
    fun `only a processed payment counts as paid`() {
        assertTrue(PaymentDto(status = "processed").toDomain().processed)
        assertFalse(PaymentDto(status = "pending").toDomain().processed)
        assertFalse(PaymentDto(status = "failed").toDomain().processed)
    }

    @Test
    fun `an unknown subscription status counts as active, as in the web`() {
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionDto(status = "SOMETHING").toDomain().status)
        assertEquals(SubscriptionStatus.CANCELED, SubscriptionDto(status = "CANCELLED").toDomain().status)
        assertEquals(SubscriptionStatus.PAST_DUE, SubscriptionDto(status = "PAST_DUE").toDomain().status)
    }

    // endregion

    // region the ViewModel

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val subscriptions = FakeSubscriptionRepository()
    private val organizations = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
    }
    private val store = FakePendingPlanStore()

    private fun viewModel() = SubscriptionViewModel(subscriptions, organizations, store, sessionManager)

    private fun TestScope.load(viewModel: SubscriptionViewModel) {
        viewModel.refresh()
        advanceUntilIdle()
    }

    @Test
    fun `shows the current plan and offers the other ones, cheapest first`() = runTest(mainRule.dispatcher) {
        subscriptions.cardsResult = ApiResult.Success(listOf(PaymentCard("Visa", "4821", isDefault = true)))
        val viewModel = viewModel()

        load(viewModel)

        val content = viewModel.uiState.value.content!!
        assertEquals("Business", content.currentPlan!!.name)
        assertEquals(listOf("Starter", "Enterprise"), content.otherPlans.map { it.name })
        assertEquals("4821", content.card!!.last4)
        assertEquals(1, content.sitesUsed) // the organization's only active site
    }

    @Test
    fun `without a subscription every plan is offered`() = runTest(mainRule.dispatcher) {
        subscriptions.subscriptionResult = ApiResult.Success(null)
        val viewModel = viewModel()

        load(viewModel)

        val content = viewModel.uiState.value.content!!
        assertNull(content.subscription)
        assertEquals(3, content.otherPlans.size)
    }

    @Test
    fun `if the plans cannot be loaded the screen reports it`() = runTest(mainRule.dispatcher) {
        subscriptions.plansResult = ApiResult.Failure(ApiError.Timeout)
        val viewModel = viewModel()

        load(viewModel)

        assertNull(viewModel.uiState.value.content)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a free plan is applied right away`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onChoosePlan(STARTER)
        advanceUntilIdle()

        assertEquals(listOf("sub-1" to STARTER.id), subscriptions.changeCalls)
        assertTrue(subscriptions.checkoutCalls.isEmpty())
        assertEquals(SubscriptionMessage.PLAN_CHANGED, viewModel.uiState.value.message)
        assertNull(store.pending)
    }

    @Test
    fun `a paid plan goes to Stripe first and is not applied until it is paid`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onChoosePlan(ENTERPRISE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("https://checkout.stripe.test/session", state.checkoutUrl)
        assertTrue(subscriptions.changeCalls.isEmpty())
        assertEquals(ENTERPRISE.id, store.pending!!.planId)
        assertEquals(ENTERPRISE.id, state.awaitingPayment!!.planId)
    }

    @Test
    fun `if Stripe cannot be opened nothing stays pending`() = runTest(mainRule.dispatcher) {
        subscriptions.checkoutResult = ApiResult.Failure(ApiError.Http(500, "boom"))
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onChoosePlan(ENTERPRISE)
        advanceUntilIdle()

        assertNull(store.pending)
        assertNull(viewModel.uiState.value.checkoutUrl)
        assertEquals(SubscriptionMessage.PAYMENT_START_FAILED, viewModel.uiState.value.message)
    }

    @Test
    fun `coming back after paying applies the plan once the payment is recorded`() = runTest(mainRule.dispatcher) {
        store.pending = PendingPlanChange(ENTERPRISE.id, "Enterprise", Instant.parse("2026-11-06T10:00:00Z"))
        subscriptions.paymentsResult = ApiResult.Success(listOf(payment(processed = true, at = "2026-11-06T10:03:00Z")))
        val viewModel = viewModel()

        load(viewModel)

        assertEquals(listOf("sub-1" to ENTERPRISE.id), subscriptions.changeCalls)
        assertNull(store.pending)
        assertNull(viewModel.uiState.value.awaitingPayment)
        assertEquals(SubscriptionMessage.PLAN_CHANGED, viewModel.uiState.value.message)
    }

    @Test
    fun `a payment made before leaving, or not processed, does not apply the plan`() = runTest(mainRule.dispatcher) {
        store.pending = PendingPlanChange(ENTERPRISE.id, "Enterprise", Instant.parse("2026-11-06T10:00:00Z"))
        subscriptions.paymentsResult = ApiResult.Success(
            listOf(
                payment(processed = true, at = "2026-10-01T10:00:00Z"), // an old payment
                payment(processed = false, at = "2026-11-06T10:03:00Z"), // not processed
            ),
        )
        val viewModel = viewModel()

        load(viewModel)

        assertTrue(subscriptions.changeCalls.isEmpty())
        assertEquals(ENTERPRISE.id, viewModel.uiState.value.awaitingPayment!!.planId)
        assertNotNull(store.pending)
    }

    @Test
    fun `verifying with no payment yet says so, and discarding forgets the pending plan`() = runTest(mainRule.dispatcher) {
        store.pending = PendingPlanChange(ENTERPRISE.id, "Enterprise", Instant.parse("2026-11-06T10:00:00Z"))
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onVerifyPayment()
        advanceUntilIdle()
        assertEquals(SubscriptionMessage.PAYMENT_NOT_FOUND, viewModel.uiState.value.message)

        viewModel.onDiscardPending()
        assertNull(store.pending)
        assertNull(viewModel.uiState.value.awaitingPayment)
    }

    // endregion
}
