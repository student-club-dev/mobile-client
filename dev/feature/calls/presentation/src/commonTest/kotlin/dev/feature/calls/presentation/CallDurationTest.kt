package dev.feature.calls.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Qo'ng'iroq davomiyligining matni — chatdagi pufakcha, qo'ng'iroq ekranidagi taymer va
 * push matni **bir xil** shaklda yozadi (`handoff/09-CALLS-REST.md` §4).
 */
class CallDurationTest {

    @Test
    fun `daqiqa va soniya`() {
        assertEquals("3:04", formatDuration(184_000))
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7_400))
    }

    /** Soat qismi faqat kerak bo'lganda — `0:03:04` telefon qo'ng'irog'ida g'alati. */
    @Test
    fun `soat faqat kerak bo'lganda chiziladi`() {
        assertEquals("1:02:33", formatDuration(3_753_000))
        assertEquals("59:59", formatDuration(3_599_000))
        assertEquals("1:00:00", formatDuration(3_600_000))
    }

    /** Manfiy qiymat — soat qochgan qurilmada mumkin; `0:00` bo'lib qoladi. */
    @Test
    fun `manfiy qiymat nolga tushadi`() {
        assertEquals("0:00", formatDuration(-5_000))
    }
}
