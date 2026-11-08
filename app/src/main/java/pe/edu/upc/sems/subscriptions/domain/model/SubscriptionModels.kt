package pe.edu.upc.sems.subscriptions.domain.model

import java.time.Instant
import java.time.LocalDate

/** One characteristic of a plan as the backend lists it (`PlanFeatures`). The app words it in Spanish by [code]. */
data class PlanFeature(val code: String, val name: String, val value: String)

/** How many sites a plan includes (feature `SITES_LIMIT`: a number or "unlimited"). */
sealed interface SiteLimit {
    data class Limited(val sites: Int) : SiteLimit
    data object Unlimited : SiteLimit
}

data class Plan(
    val id: String,
    val name: String,
    val priceSoles: Double,
    /** True when it is billed every year; otherwise every month. */
    val yearly: Boolean,
    val siteLimit: SiteLimit?,
    val features: List<PlanFeature>,
) {
    val tier: PlanTier get() = PlanTier.fromPlanName(name)
    val isFree: Boolean get() = priceSoles <= 0.0
}

enum class SubscriptionStatus { ACTIVE, TRIAL, CANCELED, PAST_DUE;

    companion object {
        /** Same normalization as the web: any status it does not know counts as active. */
        fun from(raw: String?): SubscriptionStatus = when (raw.orEmpty().uppercase()) {
            "CANCELLED", "CANCELED" -> CANCELED
            "TRIAL", "TRIALING" -> TRIAL
            "PAST_DUE", "UNPAID" -> PAST_DUE
            else -> ACTIVE
        }
    }
}

data class Subscription(
    val id: String,
    val planId: String,
    val status: SubscriptionStatus,
    /** `EndDate` of the subscription: when the current period ends. */
    val renewalDate: LocalDate?,
)

data class PaymentCard(val brand: String, val last4: String, val isDefault: Boolean)

/** A payment of the user; the plan change is only applied once one of them is processed. */
data class Payment(val paidAt: Instant?, val createdAt: Instant?, val processed: Boolean)

/** A paid plan the user left the app to pay for and that has not been applied yet. */
data class PendingPlanChange(val planId: String, val planName: String, val startedAt: Instant)
