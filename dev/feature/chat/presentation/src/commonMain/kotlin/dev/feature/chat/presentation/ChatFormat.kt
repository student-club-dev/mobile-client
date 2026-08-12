package dev.feature.chat.presentation

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import dev.core.common.locale.AppLocale

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
            yesterday() -> chatStringsNow().yesterday
            else -> instant.date().let { "${it.dayOfMonth.pad()}.${it.monthNumber.pad()}" }
        }
    }

    /** Xabarlar orasidagi ajratgich: "Bugun" / "Kecha" / "12.07.2026". */
    fun dayLabel(instant: Instant): String = when (val date = instant.date()) {
        today() -> chatStringsNow().today
        yesterday() -> chatStringsNow().yesterday
        else -> "${date.dayOfMonth.pad()}.${date.monthNumber.pad()}.${date.year}"
    }

    /** Ikki xabar bir kunga tegishlimi — ajratgich chizish kerakligini shundan bilamiz. */
    fun sameDay(a: Instant, b: Instant): Boolean = a.date() == b.date()

    /** Sarlavhadagi "oxirgi marta ko'rilgan" matni. */
    fun lastSeen(instant: Instant?): String {
        val s = chatStringsNow()
        if (instant == null) return AppLocale.pick(en = "offline", ru = "не в сети", uz = "oflayn")
        return when (val date = instant.date()) {
            today() -> s.lastSeenAt(time(instant))
            yesterday() -> s.lastSeenYesterday(time(instant))
            else -> s.lastSeenAt("${date.dayOfMonth.pad()}.${date.monthNumber.pad()}")
        }
    }

    /** `1:07` / `12:03` — ovozli xabar va video davomiyligi. */
    fun duration(ms: Int): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "${total / 60}:${(total % 60).pad()}"
    }

    /**
     * `348 KB` / `12.4 MB` — fayl hajmi.
     *
     * Baytlar ko'rsatilmaydi: chat chegarasi 48 MB va u yerda bayt aniqligining ma'nosi yo'q,
     * uzun son esa qatorni keraksiz kengaytiradi.
     */
    fun fileSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < KB -> "$bytes B"
        bytes < MB -> "${bytes / KB} KB"
        else -> {
            val mb = bytes.toDouble() / MB
            // Bitta kasr xonasi — "12.4 MB". Butun songa yaxlitlansa 0.6 MB "0 MB" bo'lardi.
            val rounded = (mb * 10).toLong()
            "${rounded / 10}.${rounded % 10} MB"
        }
    }

    private const val KB = 1024L
    private const val MB = 1024L * 1024L

    private fun Instant.date(): LocalDate = toLocalDateTime(zone).date

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(zone).date

    private fun yesterday(): LocalDate = today().minus(DatePeriod(days = 1))

    private fun Int.pad(): String = if (this < 10) "0$this" else "$this"
}
