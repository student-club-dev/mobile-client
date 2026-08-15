package dev.feature.notifications.presentation

import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.model.NotificationTarget
import dev.feature.notifications.domain.model.NotificationType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bildirishnomalar ro'yxatidagi guruhlash — bug hisobotining #10-bandi: bitta odamdan
 * kelgan bir nechta xabar ro'yxatni to'ldirib yuborardi.
 */
class GroupChatNotificationsTest {

    @Test
    fun `bitta odamning ketma-ket xabarlari bitta qatorga yig'iladi`() {
        val rows = groupChatNotifications(
            listOf(
                chat("3", "Oybek Gafurov", "alisherakam bomila"),
                chat("2", "Oybek Gafurov", "salomatt"),
                chat("1", "Quvonchbek Gafurov", "Do'stim"),
            ),
        )

        assertEquals(2, rows.size)
        // Ko'rinadigan qator — eng so'nggi xabar; id'lar esa ikkalasi ham saqlanadi
        // (qator bosilganda ikkalasi o'qilgan bo'lishi kerak).
        assertEquals("alisherakam bomila", rows[0].notification.body)
        assertEquals(listOf("3", "2"), rows[0].ids)
        assertEquals(2, rows[0].count)
        assertEquals(1, rows[1].count)
    }

    @Test
    fun `oradan boshqa bildirishnoma o'tsa guruh uzuladi`() {
        val rows = groupChatNotifications(
            listOf(
                chat("3", "Oybek Gafurov", "bugungi"),
                AppNotification(
                    id = "2",
                    title = "Yangi so'rov",
                    body = "Dilshoda siz bilan bog'lanmoqchi",
                    type = NotificationType.CONNECTION,
                    createdAt = Instant.fromEpochMilliseconds(2),
                    target = NotificationTarget.ConnectionRequests,
                    read = false,
                ),
                chat("1", "Oybek Gafurov", "ikki kun oldingi"),
            ),
        )

        // Vaqt tartibi buzilmasin: eski xabar bugungi qatorga qo'shilib ketmaydi.
        assertEquals(3, rows.size)
        assertEquals(listOf(1, 1, 1), rows.map { it.count })
    }

    @Test
    fun `boshqa turdagi bildirishnomalar hech qachon birlashtirilmaydi`() {
        val requests = List(3) { index ->
            AppNotification(
                id = "$index",
                title = "Yangi so'rov",
                body = "so'rov",
                type = NotificationType.CONNECTION,
                createdAt = Instant.fromEpochMilliseconds(index.toLong()),
                target = NotificationTarget.ConnectionRequests,
                read = false,
            )
        }
        assertEquals(3, groupChatNotifications(requests).size)
    }

    private fun chat(id: String, from: String, body: String) = AppNotification(
        id = id,
        title = from,
        body = body,
        type = NotificationType.CHAT,
        createdAt = Instant.fromEpochMilliseconds(id.toLong()),
        target = NotificationTarget.Conversations,
        read = false,
    )
}
