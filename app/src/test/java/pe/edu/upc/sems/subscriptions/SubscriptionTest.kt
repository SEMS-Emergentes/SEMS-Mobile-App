package pe.edu.upc.sems.subscriptions

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
import pe.edu.upc.sems.subscriptions.data.mapper.toDomain
import pe.edu.upc.sems.subscriptions.data.remote.PlanDto
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionDto
import pe.edu.upc.sems.subscriptions.domain.model.CardInput
import pe.edu.upc.sems.subscriptions.domain.model.DeclineReason
import pe.edu.upc.sems.subscriptions.domain.model.PurchaseOutcome
import pe.edu.upc.sems.subscriptions.domain.model.SiteLimit
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus
import pe.edu.upc.sems.subscriptions.presentation.CardError
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
    fun `the decline reason the backend sends is understood`() {
        assertEquals(DeclineReason.CARD_DECLINED, DeclineReason.from("card_declined"))
        assertEquals(DeclineReason.INSUFFICIENT_FUNDS, DeclineReason.from("insufficient_funds"))
        assertEquals(DeclineReason.OTHER, DeclineReason.from(null))
    }

    @Test
    fun `a card never prints its number`() {
        assertFalse(CardInput("4242424242424242", 12, 2099, "123").toString().contains("4242"))
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
    private fun viewModel() = SubscriptionViewModel(subscriptions, organizations, sessionManager)

    private fun TestScope.load(viewModel: SubscriptionViewModel) {
        viewModel.refresh()
        advanceUntilIdle()
    }

    @Test
    fun `shows the current plan and offers the other ones, cheapest first`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        load(viewModel)

        val content = viewModel.uiState.value.content!!
        assertEquals("Business", content.currentPlan!!.name)
        assertEquals(listOf("Starter", "Enterprise"), content.otherPlans.map { it.name })
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
    fun `a free plan is applied right away, without a card`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onChoosePlan(STARTER)
        advanceUntilIdle()

        assertEquals(listOf(STARTER.id to null), subscriptions.purchaseCalls)
        assertNull(viewModel.uiState.value.paying)
        assertEquals(SubscriptionMessage.PLAN_CHANGED, viewModel.uiState.value.message)
    }

    @Test
    fun `a paid plan asks for the card and is not bought until it is paid`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onChoosePlan(ENTERPRISE)
        advanceUntilIdle()

        assertEquals(ENTERPRISE, viewModel.uiState.value.paying)
        assertTrue(subscriptions.purchaseCalls.isEmpty())
    }

    @Test
    fun `paying with an approved card buys the plan and closes the form`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)
        viewModel.onChoosePlan(ENTERPRISE)

        viewModel.onPay("4242 4242 4242 4242", "12/29", "123")
        advanceUntilIdle()

        val (planId, card) = subscriptions.purchaseCalls.single()
        assertEquals(ENTERPRISE.id, planId)
        assertEquals(CardInput("4242424242424242", 12, 2029, "123"), card)
        assertNull(viewModel.uiState.value.paying)
        assertEquals(SubscriptionMessage.PLAN_CHANGED, viewModel.uiState.value.message)
    }

    @Test
    fun `a declined card keeps the form open and says why`() = runTest(mainRule.dispatcher) {
        subscriptions.purchaseResult = ApiResult.Success(PurchaseOutcome.Declined(DeclineReason.INSUFFICIENT_FUNDS))
        val viewModel = viewModel()
        load(viewModel)
        viewModel.onChoosePlan(ENTERPRISE)

        viewModel.onPay("4000 0000 0000 9995", "12/29", "123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ENTERPRISE, state.paying)
        assertEquals(CardError.INSUFFICIENT_FUNDS, state.cardError)
        assertNull(state.message)
    }

    @Test
    fun `an obviously incomplete card is not sent`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)
        viewModel.onChoosePlan(ENTERPRISE)

        viewModel.onPay("4242", "12/29", "123")
        assertEquals(CardError.INCOMPLETE_NUMBER, viewModel.uiState.value.cardError)
        viewModel.onPay("4242 4242 4242 4242", "13/29", "123")
        assertEquals(CardError.BAD_EXPIRY, viewModel.uiState.value.cardError)
        viewModel.onPay("4242 4242 4242 4242", "12/29", "1")
        assertEquals(CardError.BAD_CVC, viewModel.uiState.value.cardError)
        advanceUntilIdle()

        assertTrue(subscriptions.purchaseCalls.isEmpty())
    }

    @Test
    fun `a card the backend rejects as invalid is reported as such`() = runTest(mainRule.dispatcher) {
        subscriptions.purchaseResult = ApiResult.Failure(ApiError.Http(400, "card_number is not a valid card number"))
        val viewModel = viewModel()
        load(viewModel)
        viewModel.onChoosePlan(ENTERPRISE)

        viewModel.onPay("4242 4242 4242 4241", "12/29", "123")
        advanceUntilIdle()

        assertEquals(CardError.INVALID, viewModel.uiState.value.cardError)
    }

    @Test
    fun `closing the form forgets the plan`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)
        viewModel.onChoosePlan(ENTERPRISE)

        viewModel.onDismissPayment()

        assertNull(viewModel.uiState.value.paying)
    }

    // endregion
}
