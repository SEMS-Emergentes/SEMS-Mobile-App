package pe.edu.upc.sems.energy.presentation.meters

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.MeterStatus

class MetersContentTest {
    @Test fun `unassigned filter does not include zoned meters`() {
        val unassigned = item("m-1", null)
        val zoned = item("m-2", "zone-1")
        val content = MetersContent(
            items = listOf(unassigned, zoned),
            zones = emptyList(),
            selectedZoneId = UNASSIGNED_ZONE_FILTER,
            canManage = true,
        )
        assertEquals(listOf("m-1"), content.visibleItems.map { it.meter.id })
    }

    private fun item(id: String, zoneId: String?) = MeterItem(
        meter = EnergyMeter(id, "SERIAL-$id", null, null, "site-1", zoneId, MeterStatus.ACTIVE, null),
        siteName = "Local",
        zoneName = null,
        latest = null,
        readingError = false,
    )
}
