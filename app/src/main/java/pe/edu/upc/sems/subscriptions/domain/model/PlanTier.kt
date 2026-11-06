package pe.edu.upc.sems.subscriptions.domain.model

/**
 * What a plan unlocks. Same three levels as the web app (lib/plan.js): Starter < Business < Enterprise.
 */
enum class PlanTier {
    STARTER,
    BUSINESS,
    ENTERPRISE,
    ;

    /** True when this plan reaches [required]. */
    fun includes(required: PlanTier): Boolean = ordinal >= required.ordinal

    companion object {
        /**
         * The level is derived from the plan's name, exactly as the web does. It also knows the old names
         * (Plus, Pro) so a subscription made before the catalog changed keeps its level.
         */
        fun fromPlanName(name: String?): PlanTier {
            val lower = name.orEmpty().lowercase()
            return when {
                "enterprise" in lower || "pro" in lower -> ENTERPRISE
                "business" in lower || "plus" in lower -> BUSINESS
                else -> STARTER
            }
        }
    }
}
