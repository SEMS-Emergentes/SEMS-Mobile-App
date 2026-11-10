package pe.edu.upc.sems.subscriptions.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters
import pe.edu.upc.sems.subscriptions.data.mapper.SITES_LIMIT
import pe.edu.upc.sems.subscriptions.domain.model.PlanFeature
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.model.SiteLimit
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus

private const val NBSP = ' '
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "Suscripción": the current plan and the plans it can be changed to (Figma M24). */
@Composable
fun SubscriptionScreen(viewModel: SubscriptionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val snackbar = remember { SnackbarHostState() }
    val messages = mapOf(
        SubscriptionMessage.PLAN_CHANGED to stringResource(R.string.subscription_plan_changed),
        SubscriptionMessage.ACTION_FAILED to stringResource(R.string.subscription_action_failed),
    )
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        // Clearing the message first would change this effect's key and cancel the snackbar before it shows.
        snackbar.showSnackbar(messages.getValue(message))
        viewModel.onMessageShown()
    }

    Box(Modifier.fillMaxSize()) {
        val content = state.content
        when {
            state.isLoading && content == null -> LoadingScreen()
            content == null -> Column(Modifier.padding(SemsSpacing.md)) {
                FormErrorBanner(
                    error = state.error ?: pe.edu.upc.sems.organizations.presentation.common.FormError.GENERIC,
                    conflictTitle = R.string.form_error_generic_title,
                    conflictBody = R.string.form_error_generic_body,
                    onRetry = viewModel::refresh,
                    offerRetry = true,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = SemsSpacing.md, end = SemsSpacing.md, top = SemsSpacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
            ) {
                item { CurrentPlanCard(content) }
                item {
                    Text(
                        stringResource(R.string.subscription_change_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = SemsSpacing.xs),
                    )
                }
                items(content.otherPlans, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        isUpgrade = plan.priceSoles > (content.currentPlan?.priceSoles ?: 0.0),
                        busy = state.busyPlanId == plan.id,
                        enabled = state.busyPlanId == null && state.paying == null,
                        onChoose = { viewModel.onChoosePlan(plan) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(SemsSpacing.md))
    }

    state.paying?.let { plan ->
        PaymentDialog(
            plan = plan,
            purchasing = state.purchasing,
            error = state.cardError,
            onPay = viewModel::onPay,
            onDismiss = viewModel::onDismissPayment,
        )
    }
}

@Composable
private fun CurrentPlanCard(content: SubscriptionContent) {
    val subscription = content.subscription
    val plan = content.currentPlan
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            if (subscription == null) {
                Text(stringResource(R.string.subscription_current_plan), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.subscription_none), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.subscription_current_plan), style = MaterialTheme.typography.titleSmall)
                StatusChip(subscription.status)
            }
            Text(plan?.name ?: "—", style = MaterialTheme.typography.headlineMedium)
            if (plan != null) {
                Text(planPriceLine(plan), style = MaterialTheme.typography.bodyMedium)
                val used = content.sitesUsed
                val limit = plan.siteLimit
                if (used != null && limit != null) {
                    Spacer(Modifier.height(SemsSpacing.xxs))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.subscription_sites_used), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (limit) {
                                is SiteLimit.Limited -> stringResource(R.string.subscription_sites_used_of, used, limit.sites)
                                SiteLimit.Unlimited -> used.toString()
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    if (limit is SiteLimit.Limited && limit.sites > 0) {
                        LinearProgressIndicator(
                            progress = { (used.toFloat() / limit.sites).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
                subscription.renewalDate?.let { date -> NextChargeText(plan, date) }
            }
        }
    }
}

@Composable
private fun NextChargeText(plan: Plan, date: LocalDate) {
    // A free plan charges nothing, so there is no next charge to announce.
    if (plan.isFree) return
    Text(stringResource(R.string.subscription_next_charge, date.format(DATE_FORMAT)), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StatusChip(status: SubscriptionStatus) {
    val (container, content) = when (status) {
        SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SubscriptionStatus.CANCELED, SubscriptionStatus.PAST_DUE ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            stringResource(
                when (status) {
                    SubscriptionStatus.ACTIVE -> R.string.subscription_status_active
                    SubscriptionStatus.TRIAL -> R.string.subscription_status_trial
                    SubscriptionStatus.CANCELED -> R.string.subscription_status_canceled
                    SubscriptionStatus.PAST_DUE -> R.string.subscription_status_past_due
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
        )
    }
}

/**
 * Formulario de tarjeta del pago simulado. Avisa de que no se cobra dinero real: sin el aviso, alguien podria
 * escribir aqui su tarjeta de verdad creyendo que paga.
 */
@Composable
private fun PaymentDialog(
    plan: Plan,
    purchasing: Boolean,
    error: CardError?,
    onPay: (number: String, expiry: String, cvc: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payment_title, plan.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(SemsSpacing.sm)) {
                        Text(stringResource(R.string.payment_simulated_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.payment_simulated_body), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(planPriceLine(plan), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = number,
                    // Se agrupa de cuatro en cuatro mientras se escribe, como en la tarjeta.
                    onValueChange = { raw -> number = raw.filter(Char::isDigit).take(19).chunked(4).joinToString(" ") },
                    label = { Text(stringResource(R.string.payment_card_number)) },
                    placeholder = { Text("4242 4242 4242 4242") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { raw ->
                            val d = raw.filter(Char::isDigit).take(4)
                            expiry = if (d.length > 2) "${d.take(2)}/${d.drop(2)}" else d
                        },
                        label = { Text(stringResource(R.string.payment_expiry)) },
                        placeholder = { Text("MM/AA") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = cvc,
                        onValueChange = { raw -> cvc = raw.filter(Char::isDigit).take(4) },
                        label = { Text("CVC") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let {
                    Text(
                        stringResource(
                            when (it) {
                                CardError.INCOMPLETE_NUMBER -> R.string.payment_error_number
                                CardError.BAD_EXPIRY -> R.string.payment_error_expiry
                                CardError.BAD_CVC -> R.string.payment_error_cvc
                                CardError.INVALID -> R.string.payment_error_invalid
                                CardError.DECLINED -> R.string.payment_error_declined
                                CardError.INSUFFICIENT_FUNDS -> R.string.payment_error_funds
                                CardError.FAILED -> R.string.payment_error_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPay(number, expiry, cvc) }, enabled = !purchasing) {
                if (purchasing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(SemsSpacing.xs))
                }
                Text(stringResource(R.string.payment_pay, Formatters.soles(plan.priceSoles)))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !purchasing) { Text(stringResource(R.string.payment_cancel)) }
        },
    )
}

@Composable
private fun PlanCard(plan: Plan, isUpgrade: Boolean, busy: Boolean, enabled: Boolean, onChoose: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                // Same rule as the web: the middle plan is the one it recommends.
                if (plan.tier == PlanTier.BUSINESS) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.subscription_recommended),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
                        )
                    }
                }
            }
            Text(
                planPriceLine(plan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            plan.features.forEach { feature ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(SemsSpacing.sm))
                    Text(featureText(feature), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(SemsSpacing.xxs))
            val label = stringResource(
                if (isUpgrade) R.string.subscription_upgrade else R.string.subscription_change_to,
                plan.name,
            )
            val buttonContent: @Composable () -> Unit = {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(SemsSpacing.xs))
                }
                Text(label)
            }
            if (isUpgrade) {
                Button(onClick = onChoose, enabled = enabled, modifier = Modifier.fillMaxWidth()) { buttonContent() }
            } else {
                OutlinedButton(onClick = onChoose, enabled = enabled, modifier = Modifier.fillMaxWidth()) { buttonContent() }
            }
        }
    }
}

/** "S/ 149,00 al mes · hasta 5 locales", "Gratis · hasta 1 local". */
@Composable
private fun planPriceLine(plan: Plan): String {
    val price = if (plan.isFree) {
        stringResource(R.string.subscription_free)
    } else {
        stringResource(
            if (plan.yearly) R.string.subscription_price_year else R.string.subscription_price_month,
            Formatters.soles(plan.priceSoles),
        )
    }
    val sites = when (val limit = plan.siteLimit) {
        is SiteLimit.Limited -> pluralStringResource(R.plurals.subscription_sites_up_to, limit.sites, limit.sites)
        SiteLimit.Unlimited -> stringResource(R.string.subscription_sites_unlimited)
        null -> null
    }
    return listOfNotNull(price, sites).joinToString(" · ")
}

/** The backend words its features in English; the app words the ones it knows in Spanish and keeps the rest. */
@Composable
private fun featureText(feature: PlanFeature): String = when (feature.code) {
    "BASIC_DASHBOARD" -> stringResource(R.string.feature_basic_dashboard)
    "CONSUMPTION_ALERTS" -> stringResource(R.string.feature_consumption_alerts)
    SITES_LIMIT -> feature.value.toIntOrNull()
        ?.let { pluralStringResource(R.plurals.feature_sites_limit, it, it) }
        ?: stringResource(R.string.feature_sites_unlimited)
    "DEVICES_PER_SITE_LIMIT" -> feature.value.toIntOrNull()
        ?.let { stringResource(R.string.feature_meters_per_site, it) }
        ?: stringResource(R.string.feature_meters_unlimited)
    "ZONE_ANALYTICS" -> stringResource(R.string.feature_zone_analytics)
    "DEMAND_ALERTS" -> stringResource(R.string.feature_demand_alerts)
    "PEAK_HOUR_REPORTS" -> stringResource(R.string.feature_peak_hour_reports)
    "BASIC_INCLUDED" -> stringResource(R.string.feature_basic_included)
    "BUSINESS_INCLUDED" -> stringResource(R.string.feature_business_included)
    "SITE_BENCHMARKING" -> stringResource(R.string.feature_site_benchmarking)
    "TARIFF_OPTIMIZATION" -> stringResource(R.string.feature_tariff_optimization)
    "PRIORITY_SUPPORT" -> stringResource(R.string.feature_priority_support)
    else -> feature.name.ifBlank { feature.value }
}
