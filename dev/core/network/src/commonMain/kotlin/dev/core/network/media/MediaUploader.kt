package dev.core.network.media

import dev.core.network.NetworkConfig
import dev.core.network.generated.model.AttachmentDto
import dev.core.network.generated.model.MediaUploadResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

/**
 * Rasm nima uchun yuklanayotgani — backend `POST /v1/media/upload` faqat shu uchtasini qabul qiladi.
 *
 * Profil rasmi uchun alohida tur yo'q, shuning uchun eng yaqini — [LOGO].
 */
enum class MediaPurpose { LOGO, COVER, LISTING }

/**
 * Chat biriktirmasining turi — `POST /v1/media/chat-upload` dagi `kind`.
 *
 * Server turni **faylning baytlaridan** aniqlaydi va `kind` ni faqat limitlar hamda
 * ishlov berish yo'lini tanlash uchun ishlatadi (IMAGE 12 MB · GIF 20 MB · VIDEO 64 MB ·
 * VOICE 16 MB · FILE 48 MB — `handoff/02-API-CHANGES.md` §4c).
 */
enum class ChatMediaKind {
    IMAGE, GIF, VIDEO, VOICE, FILE,

    /**
     * Profil rasmi (12 MB · jpeg/png/webp/heic/heif) — `handoff/08-PROFILE.md` §2.
     * Chat biriktirmasi emas, shuning uchun `conversationId` **yuborilmaydi**.
     */
    PROFILE_PHOTO,

    /** Story rasmi (12 MB) — `handoff/07-STORIES.md` §1. `conversationId` yo'q. */
    STORY_IMAGE,

    /** Story videosi (48 MB, **≤ 30 s**) — uzunroq bo'lsa `422 MEDIA_TOO_LONG`. */
    STORY_VIDEO;

    /**
     * Suhbatga bog'liqmi. `false` bo'lganlarda `conversationId` **yuborilmasligi** kerak:
     * story va profil rasmi hech qanday suhbatga tegishli emas, ruxsatni server o'zi
     * (sessiya egasi bo'yicha) tekshiradi.
     */
    val needsConversation: Boolean
        get() = this !in setOf(PROFILE_PHOTO, STORY_IMAGE, STORY_VIDEO)
}

/**
 * Rasm yuklash (`POST /v1/media/upload`) — **qo'lda** yozilgan, generatsiya qilingan
 * `MediaApi.upload` ishlatilmaydi.
 *
 * Sababi: generator multipart qismini `formData { append("file", file) }` deb quradi va qismga
 * sarlavha qo'shmaydi. Natijada so'rov shunday ketardi:
 *
 * ```
 * Content-Disposition: form-data; name=file      // ← filename YO'Q
 * ```
 *
 * NestJS'ning `FileInterceptor`i (multer) esa qismni **faqat `filename` bo'lganda** fayl deb
 * qabul qiladi; aks holda uni oddiy matn maydoniga qo'yadi va `file` bo'sh bo'lib qoladi.
 * Shuning uchun bu yerda qism `Content-Disposition: filename` va `Content-Type` bilan quriladi.
 *
 * ⚠️ Spec qayta generatsiya qilinsa ham shu klass qoladi — `MediaUploadRequestTest` buni
 * ushlab turadi.
 */
class MediaUploader(
    private val client: HttpClient,
    private val config: NetworkConfig,
) {

    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        purpose: MediaPurpose,
        onProgress: UploadProgress? = null,
    ): MediaUploadResponseDto = client.post(config.baseUrl + PATH) {
        trackUpload(onProgress)
        setBody(
            MultiPartFormDataContent(
                formData {
                    filePart(bytes, fileName)
                    append("purpose", purpose.name)
                },
            ),
        )
    }.body()

    /**
     * `POST /v1/media/chat-upload` — chat biriktirmasi (`handoff/03-WEBSOCKET.md`, "Yuborish oqimi").
     *
     * [conversationId] chat biriktirmalari uchun **majburiy** va aynan u ruxsat tekshiruvi:
     * a'zo bo'lmasangiz, bog'lanish uzilgan yoki bloklangan bo'lsangiz `403 NOT_CONNECTED`.
     * Shu bilan server begona odam uchun fayl xostingiga aylanmaydi.
     *
     * Story va profil rasmida ([ChatMediaKind.needsConversation] `false`) suhbat yo'q —
     * o'sha yerda `null` qoldiriladi.
     *
     * Javob — to'liq [AttachmentDto]; uning `id` si keyin `message:send { mediaId }` da
     * ishlatiladi va **bir martalik**: ikkinchi marta yuborilsa `422 MEDIA_ALREADY_USED`.
     *
     * Generatsiya qilingan `ChatApi.chatUpload` ishlatilmaydi — [upload] dagi bilan bir xil
     * sabab: u multipart qismiga `filename` qo'ymaydi va NestJS `FileInterceptor` i qismni
     * fayl deb qabul qilmaydi.
     */
    suspend fun chatUpload(
        bytes: ByteArray,
        fileName: String,
        kind: ChatMediaKind,
        conversationId: String? = null,
        onProgress: UploadProgress? = null,
    ): AttachmentDto = client.post(config.baseUrl + CHAT_PATH) {
        trackUpload(onProgress)
        setBody(
            MultiPartFormDataContent(
                formData {
                    filePart(bytes, fileName)
                    append("kind", kind.name)
                    // Story va profil rasmida suhbat yo'q — maydon yuborilsa ham server uni
                    // e'tiborsiz qoldiradi, lekin yubormaslik niyatni aniqroq bildiradi.
                    if (kind.needsConversation && conversationId != null) {
                        append("conversationId", conversationId)
                    }
                },
            ),
        )
    }.body()

    /**
     * Fayl qismi — `filename` **va** `Content-Type` bilan.
     *
     * `filename` bo'lmasa multer qismni oddiy matn maydoniga qo'yadi va server "fayl yo'q"
     * deb javob beradi; shuning uchun ikkala sarlavha ham qo'lda qo'yiladi.
     */
    private fun FormBuilder.filePart(bytes: ByteArray, fileName: String) =
        append(
            key = "file",
            value = bytes,
            headers = Headers.build {
                append(HttpHeaders.ContentType, mimeTypeOf(fileName))
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
            },
        )

    /**
     * Yuborilgan baytlarni `0f..1f` ga o'giradi va [onProgress] ga uzatadi.
     *
     * ⚠️ **Hech qachon `1f` bermaydi** ([MAX_REPORTED]): oxirgi bayt soketga yozilgani —
     * serverning faylni qabul qilib, javob bergani EMAS. Katta videoda ular orasida bir
     * necha soniya bo'ladi (server transkodlash navbatiga qo'yadi) va `100%` ni erta
     * ko'rsatsak, foydalanuvchi tugadi deb o'ylab ekranni yopardi. `100%` — javob kelganda,
     * chaqiruvchining ishi.
     *
     * Hajm noma'lum bo'lsa (`contentLength == null`) hodisa **tashlab yuboriladi**: bunda
     * foizni hisoblab bo'lmaydi va UI aniqlanmagan (aylanma) halqaga tushadi.
     */
    private fun HttpRequestBuilder.trackUpload(onProgress: UploadProgress?) {
        if (onProgress == null) return
        onUpload { sent, total ->
            if (total != null && total > 0) {
                onProgress((sent.toFloat() / total).coerceIn(0f, MAX_REPORTED))
            }
        }
    }

    private companion object {
        const val PATH = "media/upload"
        const val CHAT_PATH = "media/chat-upload"

        const val MAX_REPORTED = 0.99f
    }
}

/**
 * Yuklash jarayoni — `0f..1f`.
 *
 * Ktor uni **yuborayotgan korutin ichida** chaqiradi, ya'ni tez va bloklamaydigan bo'lishi
 * kerak: ichida `StateFlow` yangilashdan boshqa ish qilmang.
 */
typealias UploadProgress = (Float) -> Unit

/**
 * Fayl kengaytmasidan MIME turi.
 *
 * ⚠️ Bu faqat **ko'rsatma**: chat yuklashda server turni faylning sehrli baytlaridan
 * aniqlaydi va `Content-Type` ga ham, fayl nomiga ham ishonmaydi. Shunga qaramay qism
 * to'g'ri sarlavha bilan ketadi — noto'g'ri turdagi qism ba'zi proksilarda kesib
 * tashlanishi mumkin.
 */
internal fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic" -> "image/heic"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg" -> "audio/ogg"
    "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"
    else -> "image/jpeg"
}
