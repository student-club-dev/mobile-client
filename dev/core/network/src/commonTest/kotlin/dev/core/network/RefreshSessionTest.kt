package dev.core.network

import dev.core.common.auth.AuthTokens
import dev.core.common.auth.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sessiyani yangilash siyosati.
 *
 * Bu testlar chinakam nosozlikdan keyin yozildi: yangilash HAR xatoda tokenlarni
 * o'chirardi va ikkita soket (chat + qo'ng'iroq) uni bir vaqtda chaqirardi. Refresh token
 * har yangilashda almashadi, ya'ni ikkinchi chaqiruv allaqachon ishlatilgan tokenni
 * yuborar va server sessiyani bekor qilardi — foydalanuvchi sababsiz "Qaytadan kiring"
 * xabarini olardi.
 */
class RefreshSessionTest {

    private val config = NetworkConfig(baseUrl = "https://example.test/v1/", enableLogging = false)

    @Test
    fun `muvaffaqiyatli yangilash yangi juftlikni saqlaydi`() = runTest {
        val store = FakeTokenStore(AuthTokens("old-access", "old-refresh"))
        val client = clientRespondingWith(tokens("new-access", "new-refresh"))

        val access = client.refreshSession(config, store)

        assertEquals("new-access", access)
        assertEquals("new-refresh", store.tokens()?.refreshToken)
    }

    @Test
    fun `tarmoq xatosida sessiya SAQLANADI`() = runTest {
        val store = FakeTokenStore(AuthTokens("access", "refresh"))
        val client = testClient(MockEngine { throw kotlinx.io.IOException("tarmoq yo'q") })

        assertNull(client.refreshSession(config, store))
        // Eng muhimi: foydalanuvchi ilovadan chiqarib yuborilmaydi.
        assertNotNull(store.tokens())
        assertEquals(0, store.clearCount)
    }

    @Test
    fun `server xatosida (5xx) sessiya SAQLANADI`() = runTest {
        val store = FakeTokenStore(AuthTokens("access", "refresh"))
        val client = testClient(MockEngine { respondError(HttpStatusCode.BadGateway) })

        assertNull(client.refreshSession(config, store))
        assertNotNull(store.tokens())
    }

    @Test
    fun `server rad etsa (401) sessiya tozalanadi`() = runTest {
        val store = FakeTokenStore(AuthTokens("access", "refresh"))
        val client = testClient(MockEngine { respondError(HttpStatusCode.Unauthorized) })

        assertNull(client.refreshSession(config, store))
        assertNull(store.tokens())
    }

    @Test
    fun `bir vaqtda kelgan ikki chaqiruv tokenni FAQAT BIR MARTA aylantiradi`() = runTest {
        val store = FakeTokenStore(AuthTokens("old-access", "old-refresh"))
        var requests = 0
        val client = testClient(
            MockEngine {
                requests += 1
                respond(
                    content = tokens("new-access", "new-refresh"),
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            },
        )

        // Chat va qo'ng'iroq soketlari aynan shunday — bir-biridan mustaqil.
        val results = listOf(
            async { client.refreshSession(config, store) },
            async { client.refreshSession(config, store) },
        ).awaitAll()

        assertEquals(1, requests, "refresh token faqat bir marta aylantirilishi kerak")
        assertTrue(results.all { it == "new-access" })
        assertNotNull(store.tokens())
    }

    // --- yordamchilar ---

    private fun tokens(access: String, refresh: String) =
        """{"accessToken":"$access","refreshToken":"$refresh"}"""

    private fun clientRespondingWith(body: String) = testClient(
        MockEngine {
            respond(
                content = body,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        },
    )

    /**
     * Haqiqiy klient bilan bir xil sozlamada: `expectSuccess` (4xx istisno bo'lib chiqadi)
     * va JSON. Bularsiz so'rov tanasi umuman seriyalanmasdi va test tarmoqqa yetib
     * bormasdan yiqilardi.
     */
    private fun testClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(appJson) }
    }

    private class FakeTokenStore(private var value: AuthTokens?) : TokenStore {
        var clearCount = 0
            private set

        override fun tokens(): AuthTokens? = value
        override fun save(tokens: AuthTokens, userId: String?) { value = tokens }
        override fun clear() { value = null; clearCount += 1 }
        override fun userId(): String? = null
    }
}
