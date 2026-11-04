package pe.edu.upc.sems.energy.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MeterZoneBodyTest {
    @Test fun `clearing zone sends explicit null even when nulls are omitted globally`() {
        val json = Json { explicitNulls = false }
        assertEquals(JsonNull, meterZoneBody(null)["zone_id"])
        assertEquals("{\"zone_id\":null}", json.encodeToString(meterZoneBody(null)))
    }

    @Test fun `assigning zone sends its identifier`() {
        assertEquals(JsonPrimitive("zone-1"), meterZoneBody("zone-1")["zone_id"])
    }
}
