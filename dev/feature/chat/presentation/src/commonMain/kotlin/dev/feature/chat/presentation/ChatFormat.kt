package dev.feature.chat.presentation

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Chat vaqt yorliqlari. `kotlinx-datetime` da matn formatlagichi yo'q (KMP), shuning uchun
 * qo'lda — barcha matn o'zbekcha va qurilmaning vaqt mintaqasida.
 */
internal object ChatFormat {

    private val zone: TimeZone get() = TimeZone.currentSystemDefault()

    /** Xabar ostidagi soat: `14:22`. */
    fun time(instant: Instant): String {
        val t = instant.toLocalDateTime(zone)
        return "${t.hour.pad()}:${t.minute.pad()}"
    }

    /** Suhbatlar ro'yxatidagi qisqa yorliq: bugun — soat, kecha — "Kecha", aks holda sana. */
    fun listStamp(instant: Instant?): String {
        if (instant == null) return ""
        return when (instant.date()) {
            today() -> time(instant)
            yesterday() -> "Kecha"
            else -> instant.date().let { "${it.dayOfMonth.pad()}.${it.monthNumber.pad()}" }
        }
    }

    /** Xabarlar orasidagi ajratgich: "Bugun" / "Kecha" / "12.07.2026". */
    fun dayLabel(instant: Instant): String = when (val date = instant.date()) {
        today() -> "Bugun"
        yesterday() -> "Kecha"
        else -> "${date.dayOfMonth.pad()}.${date.monthNumber.pad()}.${date.year}"
    }

    /** Ikki xabar bir kunga tegishlimi — ajratgich chizish kerakligini shundan bilamiz. */
    fun sameDay(a: Instant, b: Instant): Boolean = a.date() == b.date()

    /** Sarlavhadagi "oxirgi marta ko'rilgan" matni. */
    fun lastSeen(instant: Instant?): String {
        if (instant == null) return "oflayn"
        return when (val date = instant.date()) {
            today() -> "oxirgi faollik ${time(instant)}"
            yesterday() -> "kecha ${time(instant)}"
            else -> "oxirgi faollik ${date.dayOfMonth.pad()}.${date.monthNumber.pad()}"
        }
    }

    private fun Instant.date(): LocalDate = toLocalDateTime(zone).date

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(zone).date

    private fun yesterday(): LocalDate = today().minus(DatePeriod(days = 1))

    private fun Int.pad(): String = if (this < 10) "0$this" else "$this"
}
