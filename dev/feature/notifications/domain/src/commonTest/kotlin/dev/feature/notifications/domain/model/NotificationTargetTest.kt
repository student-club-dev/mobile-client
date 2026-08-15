package dev.feature.notifications.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bug hisoboti #11: chat bildirishnomasini bosganda hech nima bo'lmasdi, chunki server
 * `targetId` siz yuborganda qator [NotificationTarget.None] ga tushardi.
 */
class NotificationTargetTest {

    @Test
    fun `id bor chat bildirishnomasi o'sha suhbatni ochadi`() {
        assertEquals(NotificationTarget.Chat("c-1"), NotificationTarget.of("CHAT", "c-1"))
    }

    @Test
    fun `id yo'q chat bildirishnomasi suhbatlar ro'yxatini ochadi`() {
        assertEquals(NotificationTarget.Conversations, NotificationTarget.of("CHAT", null))
        assertEquals(NotificationTarget.Conversations, NotificationTarget.of("CHAT", " "))
        // Server bildirishnoma turini `MESSAGE` deb ham yuboradi.
        assertEquals(NotificationTarget.Conversations, NotificationTarget.of("MESSAGE", null))
    }

    @Test
    fun `id talab qiladigan boshqa turlar idsiz hech qayerga olib bormaydi`() {
        assertEquals(NotificationTarget.None, NotificationTarget.of("LISTING", null))
        assertEquals(NotificationTarget.None, NotificationTarget.of("NOMA'LUM", "x"))
    }
}
