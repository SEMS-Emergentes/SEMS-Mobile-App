package pe.edu.upc.sems.alerts.domain.model

/** Whether the user wants alerts on a channel (`email`, ...), and from which severity. */
data class NotificationPreference(
    val channel: String,
    val enabled: Boolean,
    val minSeverity: String?,
)
