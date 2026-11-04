package pe.edu.upc.sems.devices.presentation

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.edu.upc.sems.devices.presentation.devices.todayConsumption
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.shared.util.Formatters

class TodayConsumptionTest {

    private val today = LocalDate.of(2026, 11, 3)

    private fun consumption(deviceId: String, kwh: Double, soles: Double, periodEnd: String?) =
        DeviceConsumption(deviceId, kwh, soles, periodStart = null, periodEnd = periodEnd?.let(Instant::parse))

    @Test
    fun `sums what the device consumed on the Lima day`() {
        val list = listOf(
            consumption("dev-1", 100.0, 40.0, "2026-11-03T15:00:00Z"),
            consumption("dev-1", 18.0, 12.4, "2026-11-03T20:00:00Z"),
            consumption("dev-2", 99.0, 99.0, "2026-11-03T15:00:00Z"),
        )

        assertEquals(118.0 to 52.4, todayConsumption(list, "dev-1", today))
    }

    @Test
    fun `uses Lima time, not UTC, to decide the day`() {
        // 2026-11-04 02:00 UTC is still 2026-11-03 21:00 in Lima (UTC-5).
        val list = listOf(consumption("dev-1", 10.0, 5.0, "2026-11-04T02:00:00Z"))

        assertEquals(10.0 to 5.0, todayConsumption(list, "dev-1", today))
    }

    @Test
    fun `ignores other days and periods without an end`() {
        val list = listOf(
            consumption("dev-1", 10.0, 5.0, "2026-11-02T15:00:00Z"),
            consumption("dev-1", 10.0, 5.0, null),
        )

        assertNull(todayConsumption(list, "dev-1", today))
    }

    @Test
    fun `formats kWh and soles like the design`() {
        // The formatters use a non-breaking space so a value never wraps in the middle.
        assertEquals("118 kWh", Formatters.kilowattHours(118.0))
        assertEquals("12,4 kWh", Formatters.kilowattHours(12.4))
        assertEquals("S/ 52,40", Formatters.soles(52.4))
        assertEquals("S/ 3 412,50", Formatters.soles(3412.5))
    }
}
