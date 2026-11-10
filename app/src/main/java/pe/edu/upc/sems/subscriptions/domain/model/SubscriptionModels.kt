package pe.edu.upc.sems.subscriptions.domain.model

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

/** Datos de tarjeta tal como se escriben. No se guardan: viajan una vez al backend, que los valida y los descarta. */
data class CardInput(val number: String, val expMonth: Int, val expYear: Int, val cvc: String) {
    // Sin esto, cualquier log del estado escribiria el numero completo y el CVC.
    override fun toString() = "CardInput(****)"
}

/** Como termina una compra que el backend llego a procesar. */
sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome

    /** El cobro se rechazo: el plan no cambio y el intento queda en el historial. */
    data class Declined(val reason: DeclineReason) : PurchaseOutcome
}

enum class DeclineReason {
    CARD_DECLINED, INSUFFICIENT_FUNDS, OTHER;

    companion object {
        fun from(raw: String?): DeclineReason = when (raw) {
            "card_declined" -> CARD_DECLINED
            "insufficient_funds" -> INSUFFICIENT_FUNDS
            else -> OTHER
        }
    }
}
