package pe.edu.upc.sems.energy.domain.model

/**
 * One site of the backend's organization comparison (`GET /organizations/{id}/site-consumption`) over the
 * last N days. Everything here is calculated by the backend.
 */
data class SiteConsumption(
    val siteId: String,
    val meterCount: Int,
    val kwh: Double,
    /** Energy only (peak and off-peak kWh at the site's tariff): no power charge, fixed charge or IGV. */
    val energyCostSoles: Double?,
    /** Highest power any reading of the site reached in the period. */
    val peakDemandKw: Double,
    val contractedPowerKw: Double?,
)
