package dev.feature.notifications.presentation

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.core.uikit.locale.uiStringsNow

/**
 * Bildirishnoma vaqti yorlig'i — "hozir", "12 daqiqa oldin", "3 soat oldin", "kecha",
 * "5 kun oldin", "12.07.2026".
 *
 * Yorliq server bergan TAYYOR MATN emas, `createdAt` dan har chizishda hisoblanadi: tayyor
 * matn yozilgan paytda muzlab qolardi va ertasiga ham "2 soat oldin" deb turardi.
 * `kotlinx-datetime` da matn formatlagichi yo'q (KMP), shuning uchun qo'lda.
 */
internal object NotificationTime {

    fun label(createdAt: Instant, now: Instant = Clock.System.now()): String {
        // Vaqti noma'lum (parse qilinmagan sana) — yorliqsiz qoladi, "51 yil oldin" emas.
        if (createdAt.toEpochMilliseconds() <= 0L) return ""

        val seconds = (now - createdAt).inWholeSeconds
        // Qurilma soati serverdan orqada bo'lishi mumkin — kelajakdagi sana "hozir".
        val ui = uiStringsNow()
        if (seconds < MINUTE) return ui.justNow

        val minutes = seconds / MINUTE
        if (minutes < 60) return notificationsStringsNow().minutesAgo(minutes.toInt())

        val hours = minutes / 60
        if (hours < 24) return ui.hoursAgo(hours.toInt())

        val days = hours / 24
        return when {
            days == 1L -> ui.yesterday
            days < 7 -> ui.daysAgo(days.toInt())
            else -> createdAt.dateLabel()
        }
    }

    private fun Instant.dateLabel(): String {
        val date = toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "${date.dayOfMonth.pad()}.${date.monthNumber.pad()}.${date.year}"
    }

    private fun Int.pad(): String = if (this < 10) "0$this" else "$this"

    private const val MINUTE = 60L
}
