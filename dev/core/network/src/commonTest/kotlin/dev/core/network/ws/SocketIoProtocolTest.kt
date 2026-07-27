package dev.core.network.ws

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Socket.IO / Engine.IO ramkalari — qo'lda yozilgan protokol, shuning uchun har bir shakl
 * shu yerda qotirilgan. Manba: handoff `chat.md` §7–§9 (WS Swagger'da YO'Q).
 */
class SocketIoProtocolTest {

    private val ns = "/chat"

    @Test
    fun encodesEventWithoutAck() {
        val frame = SocketIoProtocol.encode(
            ns, SocketIoProtocol.EVENT, ackId = null,
            body = buildJsonArray {
                add(JsonPrimitive("typing:start"))
                add(buildJsonObject { put("conversationId", JsonPrimitive("cnv_1")) })
            },
        )
        assertEquals("""42/chat,["typing:start",{"conversationId":"cnv_1"}]""", frame)
    }

    @Test
    fun encodesEventWithAckId() {
        val frame = SocketIoProtocol.encode(
            ns, SocketIoProtocol.EVENT, ackId = 7,
            body = buildJsonArray {
                add(JsonPrimitive("message:send"))
                add(buildJsonObject { put("body", JsonPrimitive("Salom!")) })
            },
        )
        // Ack id namespace'dan KEYIN, JSON'dan OLDIN turadi.
        assertEquals("""42/chat,7["message:send",{"body":"Salom!"}]""", frame)
    }

    @Test
    fun encodesConnectWithAuthPayload() {
        val frame = SocketIoProtocol.encode(
            ns, SocketIoProtocol.CONNECT, ackId = null,
            body = buildJsonObject { put("token", JsonPrimitive("jwt")) },
        )
        assertEquals("""40/chat,{"token":"jwt"}""", frame)
    }

    @Test
    fun omitsDefaultNamespace() {
        val frame = SocketIoProtocol.encode(
            SocketIoProtocol.DEFAULT_NAMESPACE, SocketIoProtocol.EVENT, ackId = null,
            body = buildJsonArray { add(JsonPrimitive("ping")) },
        )
        assertEquals("""42["ping"]""", frame)
    }

    @Test
    fun decodesConnectAck() {
        val packet = SocketIoProtocol.decode(ns, """0/chat,{"sid":"abc"}""")!!
        assertEquals(SocketIoProtocol.CONNECT, packet.type)
        assertNull(packet.ackId)
        assertEquals("""{"sid":"abc"}""", packet.body)
    }

    @Test
    fun decodesEvent() {
        val packet = SocketIoProtocol.decode(ns, """2/chat,["message:new",{"seq":42}]""")!!
        assertEquals(SocketIoProtocol.EVENT, packet.type)
        assertNull(packet.ackId)
        assertEquals("""["message:new",{"seq":42}]""", packet.body)
    }

    @Test
    fun decodesAckWithId() {
        val packet = SocketIoProtocol.decode(ns, """3/chat,7[{"status":"sent","seq":43}]""")!!
        assertEquals(SocketIoProtocol.ACK, packet.type)
        assertEquals(7, packet.ackId)
        assertEquals("""[{"status":"sent","seq":43}]""", packet.body)
    }

    @Test
    fun decodesDisconnectWithoutBody() {
        val packet = SocketIoProtocol.decode(ns, "1/chat")!!
        assertEquals(SocketIoProtocol.DISCONNECT, packet.type)
        assertEquals("", packet.body)
    }

    @Test
    fun ignoresOtherNamespaces() {
        // Bitta soketda bir nechta namespace bo'lishi mumkin — begonasi tashlanadi.
        assertNull(SocketIoProtocol.decode(ns, """2/admin,["message:new",{}]"""))
    }

    @Test
    fun decodesDefaultNamespacePacket() {
        val packet = SocketIoProtocol.decode(SocketIoProtocol.DEFAULT_NAMESPACE, """2["hello"]""")!!
        assertEquals(SocketIoProtocol.EVENT, packet.type)
        assertEquals("""["hello"]""", packet.body)
    }

    @Test
    fun decodesEmptyPacketAsNull() {
        assertNull(SocketIoProtocol.decode(ns, ""))
    }

    @Test
    fun multiDigitAckIdSurvivesRoundTrip() {
        val frame = SocketIoProtocol.encode(
            ns, SocketIoProtocol.EVENT, ackId = 123,
            body = buildJsonArray { add(JsonPrimitive("message:send")) },
        )
        // Engine.IO prefiksini (`4`) olib tashlab qaytarib o'qiymiz.
        val packet = SocketIoProtocol.decode(ns, frame.substring(1))!!
        assertEquals(123, packet.ackId)
    }
}
