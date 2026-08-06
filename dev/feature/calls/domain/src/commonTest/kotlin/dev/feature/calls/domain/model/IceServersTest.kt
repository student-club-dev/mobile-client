package dev.feature.calls.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `hasTurn` — `relayOnly` ni majburlash yoki majburlamaslikni hal qiladigan yagona
 * tekshiruv. Xato bo'lsa qo'ng'iroq "Ulanmoqda" da qotib qoladi, shuning uchun test bilan
 * qotirilgan.
 */
class IceServersTest {

    @Test
    fun `turn borligi aniqlanadi`() {
        val servers = IceServers(
            listOf(IceServer(urls = listOf("turn:turn.example.com:3478"), username = "u", credential = "p")),
        )
        assertTrue(servers.hasTurn)
    }

    @Test
    fun `turns ham turn hisoblanadi`() {
        val servers = IceServers(listOf(IceServer(urls = listOf("turns:turn.example.com:5349"))))
        assertTrue(servers.hasTurn)
    }

    @Test
    fun `bitta url royxatida turn bolsa yetadi`() {
        val servers = IceServers(
            listOf(IceServer(urls = listOf("stun:stun.example.com:3478", "turn:turn.example.com:3478"))),
        )
        assertTrue(servers.hasTurn)
    }

    @Test
    fun `faqat stun turn hisoblanmaydi`() {
        // Muhim farq: STUN srflx nomzod beradi, relay emas — `iceTransportPolicy = RELAY`
        // da undan foyda yo'q.
        val servers = IceServers(listOf(IceServer(urls = listOf("stun:stun.example.com:3478"))))
        assertFalse(servers.hasTurn)
    }

    @Test
    fun `bosh royxatda turn yoq`() {
        assertFalse(IceServers().hasTurn)
        assertFalse(IceServers(listOf(IceServer(urls = emptyList()))).hasTurn)
    }
}
