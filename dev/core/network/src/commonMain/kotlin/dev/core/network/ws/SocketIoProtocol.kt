package dev.core.network.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Socket.IO paketining tarkibi (Engine.IO `4` — MESSAGE — dan keyingi qism).
 *
 * [body] — xom JSON matni: EVENT/ACK da massiv (`["name", {...}]`), CONNECT da obyekt.
 */
internal data class SocketIoPacket(
    val type: Char,
    val ackId: Int?,
    val body: String,
)

/**
 * **Socket.IO / Engine.IO ramkalarining sof kodeki** — tarmoqqa bog'liq emas, shuning uchun
 * to'liq test bilan qoplanadi (qarang `SocketIoProtocolTest`).
 *
 * ```
 * Engine.IO ramka:  0=open  1=close  2=ping  3=pong  4=message
 * Socket.IO paket:  0=CONNECT  1=DISCONNECT  2=EVENT  3=ACK  4=CONNECT_ERROR
 * Shakl:            <tur>[<namespace>,][<ackId>]<json>
 * Misol:            42/chat,7["message:send",{…}]   →   43/chat,7[{"status":"sent",…}]
 * ```
 */
internal object SocketIoProtocol {

    const val ENGINE_OPEN = '0'
    const val ENGINE_CLOSE = '1'
    const val ENGINE_PING = '2'
    const val ENGINE_PONG = '3'
    const val ENGINE_MESSAGE = '4'

    const val CONNECT = '0'
    const val DISCONNECT = '1'
    const val EVENT = '2'
    const val ACK = '3'
    const val CONNECT_ERROR = '4'

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** `4<tur>[/ns,][ackId]<json>` — yuborishga tayyor matnli ramka. */
    fun encode(namespace: String, type: Char, ackId: Int?, body: JsonElement): String = buildString {
        append(ENGINE_MESSAGE)
        append(type)
        // Standart namespace (`/`) yozilmaydi — server uni kutmaydi.
        if (namespace != DEFAULT_NAMESPACE) {
            append(namespace)
            append(',')
        }
        if (ackId != null) append(ackId)
        append(json.encodeToString(JsonElement.serializer(), body))
    }

    /**
     * Socket.IO paketini ajratadi. `null` — paket bo'sh yoki **boshqa namespace**ga tegishli
     * (bitta soketda bir nechta namespace bo'lishi mumkin).
     */
    fun decode(namespace: String, packet: String): SocketIoPacket? {
        if (packet.isEmpty()) return null
        val type = packet[0]
        var rest = packet.substring(1)

        var ns = DEFAULT_NAMESPACE
        if (rest.startsWith("/")) {
            val comma = rest.indexOf(',')
            if (comma >= 0) {
                ns = rest.substring(0, comma)
                rest = rest.substring(comma + 1)
            } else {
                // `41/chat` — tanasiz DISCONNECT.
                ns = rest
                rest = ""
            }
        }
        if (ns != namespace) return null

        // Ack id — raqamlar ketma-ketligi (bo'lmasligi ham mumkin).
        var i = 0
        while (i < rest.length && rest[i].isDigit()) i++
        return SocketIoPacket(
            type = type,
            ackId = if (i > 0) rest.substring(0, i).toIntOrNull() else null,
            body = rest.substring(i),
        )
    }

    const val DEFAULT_NAMESPACE = "/"
}
