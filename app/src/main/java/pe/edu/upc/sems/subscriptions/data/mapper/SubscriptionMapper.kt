package pe.edu.upc.sems.subscriptions.data.mapper

import java.time.Instant
import java.time.ZoneId
import pe.edu.upc.sems.subscriptions.data.remote.PlanDto
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionDto
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanFeature
import pe.edu.upc.sems.subscriptions.domain.model.SiteLimit
import pe.edu.upc.sems.subscriptions.domain.model.Subscription
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus

private val LIMA: ZoneId = ZoneId.of("America/Lima")

/** Feature that carries the Stripe price id: internal, never shown (the web hides it too). */
private const val STRIPE_PRICE_ID = "STRIPE_PRICE_ID"
const val SITES_LIMIT = "SITES_LIMIT"

fun PlanDto.toDomain(): Plan {
    val shown = features.filter { !it.code.equals(STRIPE_PRICE_ID, ignoreCase = true) }
        .map { PlanFeature(it.code.orEmpty(), it.name.orEmpty(), it.value.orEmpty()) }
    val limit = shown.firstOrNull { it.code == SITES_LIMIT }?.value?.let { value ->
        if (value.equals("unlimited", ignoreCase = true)) SiteLimit.Unlimited else value.toIntOrNull()?.let(SiteLimit::Limited)
    }
    return Plan(
        id = planId,
        name = name ?: "Plan",
        priceSoles = price ?: 0.0,
        yearly = billingPeriod.orEmpty().lowercase().let { it.startsWith("year") || it.startsWith("annual") },
        siteLimit = limit,
        features = shown,
    )
}

fun SubscriptionDto.toDomain() = Subscription(
    id = subscriptionId.orEmpty(),
    planId = planId.orEmpty(),
    status = SubscriptionStatus.from(status),
    renewalDate = endDate.toInstantOrNull()?.atZone(LIMA)?.toLocalDate(),
)

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
