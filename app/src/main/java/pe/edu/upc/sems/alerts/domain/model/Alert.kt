package pe.edu.upc.sems.alerts.domain.model

import java.time.Instant

/** Status of an alert. The backend sends it in lower case (`active`, `acknowledged`, `resolved`). */
enum class AlertStatus(val wire: String) {
    ACTIVE("active"),
    ACKNOWLEDGED("acknowledged"),
    RESOLVED("resolved"),
    ;

    companion object {
        /** Same reading as the web app (alerts.service.js): closed and dismissed count as resolved. */
        fun from(raw: String?): AlertStatus = when (raw?.trim()?.lowercase()) {
            "resolved", "closed", "dismissed" -> RESOLVED
            "acknowledged" -> ACKNOWLEDGED
            else -> ACTIVE // open, pending, active, or anything unknown
        }
    }
}

/**
 * Severity of an alert. The backend produces `critical`, `high`, `medium` and `low`; the design only has
 * three levels, so critical and high are both shown as "Crítica".
 */
enum class AlertSeverity(val rank: Int) {
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    ;

    companion object {
        fun from(raw: String?): AlertSeverity = when (raw?.trim()?.lowercase()) {
            "critical", "high" -> HIGH
            "low" -> LOW
            else -> MEDIUM // like the web app, anything unknown is medium
        }
    }
}

/** Kind of alert, grouped like the web app does. */
enum class AlertType {
    THRESHOLD, ANOMALY, INACTIVITY;

    companion object {
        fun from(raw: String?): AlertType {
            val value = raw.orEmpty().lowercase()
            return when {
                "anomaly" in value -> ANOMALY
                "inactiv" in value -> INACTIVITY
                else -> THRESHOLD
            }
        }
    }
}

data class Alert(
    val id: String,
    /** Null for alerts that are not about a single device. */
    val deviceId: String?,
    val type: AlertType,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val status: AlertStatus,
    val triggeredAt: Instant?,
    val resolvedAt: Instant?,
)
