package pe.edu.upc.sems.energy.presentation.meters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrMeterPayloadTest {
    @Test fun `accepts plain serial`() {
        assertEquals(QrMeterPayload("SEMS-MTR-047", null), parseMeterQr(" SEMS-MTR-047 "))
    }

    @Test fun `accepts serial JSON`() {
        assertEquals(QrMeterPayload("SEMS-MTR-047", "EM100"), parseMeterQr("""{"serial":"SEMS-MTR-047","model":"EM100"}"""))
    }

    @Test fun `accepts meter serial JSON`() {
        assertEquals(QrMeterPayload("SEMS-MTR-047", null), parseMeterQr("""{"meter_serial":"SEMS-MTR-047"}"""))
    }

    @Test fun `rejects empty and incomplete JSON`() {
        assertNull(parseMeterQr("   "))
        assertNull(parseMeterQr("""{"model":"EM100"}"""))
        assertNull(parseMeterQr("""{"meter_serial":"bad serial"}"""))
        assertNull(parseMeterQr("""{"serial":null}"""))
        assertNull(parseMeterQr("https://example.com/meter/123"))
        assertNull(parseMeterQr("""{"serial":"ok"""))
    }
}
