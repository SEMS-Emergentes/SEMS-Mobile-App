package pe.edu.upc.sems.subscriptions.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import pe.edu.upc.sems.subscriptions.domain.model.PendingPlanChange
import pe.edu.upc.sems.subscriptions.domain.repository.PendingPlanStore

/**
 * The web keeps the plan the user went to pay for in localStorage and applies it when Stripe sends them back.
 * This is the same idea: nothing secret, just the plan and when the user left to pay.
 */
@Singleton
class SharedPrefsPendingPlanStore @Inject constructor(
    @ApplicationContext context: Context,
) : PendingPlanStore {

    private val prefs = context.getSharedPreferences("sems_pending_plan", Context.MODE_PRIVATE)

    override fun get(): PendingPlanChange? {
        val planId = prefs.getString(KEY_ID, null) ?: return null
        val startedAt = prefs.getLong(KEY_STARTED, 0L).takeIf { it > 0 } ?: return null
        return PendingPlanChange(planId, prefs.getString(KEY_NAME, null).orEmpty(), Instant.ofEpochMilli(startedAt))
    }

    override fun set(change: PendingPlanChange) {
        prefs.edit()
            .putString(KEY_ID, change.planId)
            .putString(KEY_NAME, change.planName)
            .putLong(KEY_STARTED, change.startedAt.toEpochMilli())
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ID = "plan_id"
        const val KEY_NAME = "plan_name"
        const val KEY_STARTED = "started_at"
    }
}
