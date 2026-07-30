package dev.core.network.media

import dev.core.network.NetworkConfig
import dev.core.network.generated.model.AttachmentDto
import dev.core.network.generated.model.MediaUploadResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
 * VOICE 16 MB · FILE 48 MB — `handoff/api-changes.md` §4c).
 */
enum class ChatMediaKind { IMAGE, GIF, VIDEO, VOICE, FILE }

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
    ): MediaUploadResponseDto = client.post(config.baseUrl + PATH) {
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
     * `POST /v1/media/chat-upload` — chat biriktirmasi (`handoff/chat.md`, "Yuborish oqimi").
     *
     * [conversationId] **majburiy** va aynan u ruxsat tekshiruvi: a'zo bo'lmasangiz,
     * bog'lanish uzilgan yoki bloklangan bo'lsangiz `403 NOT_CONNECTED`. Shu bilan server
     * begona odam uchun fayl xostingiga aylanmaydi.
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
        conversationId: String,
    ): AttachmentDto = client.post(config.baseUrl + CHAT_PATH) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    filePart(bytes, fileName)
                    append("kind", kind.name)
                    append("conversationId", conversationId)
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

    private companion object {
        const val PATH = "media/upload"
        const val CHAT_PATH = "media/chat-upload"
    }
}

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
