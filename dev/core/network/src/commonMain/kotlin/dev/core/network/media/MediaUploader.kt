package dev.core.network.media

import dev.core.network.NetworkConfig
import dev.core.network.generated.model.AttachmentDto
import dev.core.network.generated.model.MediaUploadResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

/**
 * Rasm nima uchun yuklanayotgani — backend `POST /v1/media/upload` faqat shu uchtasini qabul qiladi.
 *
 * Profil rasmi uchun alohida tur yo'q, shuning uchun eng yaqini — [LOGO].
 */
enum class MediaPurpose { LOGO, COVER, LISTING }

/**
 * Chat biriktirmasining turi — `POST /v1/media/chat-upload` dagi `kind`.
 *
 * Server turni **faylning baytlaridan** aniqlaydi va `kind` faqat ishlov berish yo'lini
 * tanlaydi.
 *
 * ⚠️ **Hajm chegarasi 2026-08-03 dan olib tashlandi.** Ilgari har tur uchun alohida
 * chegara bor edi (IMAGE 12 MB, VIDEO 64 MB…); endi yagona istisno — [VIDEO_NOTE]
 * (≤ 60 s, ≤ 12 MB) va [STORY_VIDEO] (≤ 60 s). Qolganini faqat kunlik kvota va
 * serverdagi bo'sh joy cheklaydi (`STORAGE_FULL`).
 */
enum class ChatMediaKind {
    IMAGE,

    /**
     * Rasm **siqilmasdan**: to'liq o'lchamda, formati saqlanadi, faqat EXIF (jumladan GPS)
     * olib tashlanadi.
     *
     * Xabar baribir `type: IMAGE` bo'lib qoladi — farq faqat sifatda. Telegram'dagi
     * «faylsifat yuborish» ning ekvivalenti.
     */
    IMAGE_ORIGINAL,

    GIF,
    VIDEO,

    /**
     * Dumaloq video xabar. **Kvadrat** bo'lishi shart (aks holda `422 MEDIA_NOT_SQUARE`),
     * ≤ 60 s va ≤ 12 MB; izoh qabul qilinmaydi.
     */
    VIDEO_NOTE,

    VOICE, FILE,

    /**
     * Profil rasmi (12 MB · jpeg/png/webp/heic/heif) — `handoff/08-PROFILE.md` §2.
     * Chat biriktirmasi emas, shuning uchun `conversationId` **yuborilmaydi**.
     */
    PROFILE_PHOTO,

    /** Story rasmi (12 MB) — `handoff/07-STORIES.md` §1. `conversationId` yo'q. */
    STORY_IMAGE,

    /** Story videosi — **≤ 60 s**; uzunroq bo'lsa `422 STORY_VIDEO_TOO_LONG`. */
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
 * Video sifati — `chat-upload` va `upload/init` dagi `quality`. **Faqat videoga** tegishli.
 *
 * [AUTO] — server o'zi hal qiladi (odatiy). [HIGH] — transkodlash kerak bo'lsa ham 1080p
 * saqlanadi. [ORIGINAL] — fayl **aynan yuborilganidek** saqlanadi va hech qachon
 * transkodlanmaydi.
 */
enum class MediaQuality { AUTO, HIGH, ORIGINAL }

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
        uploadTimeouts()
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
        quality: MediaQuality? = null,
        onProgress: UploadProgress? = null,
    ): AttachmentDto = client.post(config.baseUrl + CHAT_PATH) {
        uploadTimeouts()
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
                    // `quality` faqat videoga ta'sir qiladi; berilmasa server `AUTO` deb oladi.
                    quality?.let { append("quality", it.name) }
                },
            ),
        )
    }.body()

    /**
     * [chatUpload] ning **fayl** varianti — baytlar xotiraga o'qilmaydi.
     *
     * Nega alohida: video 64 MB gacha bo'lishi mumkin va uni `ByteArray` ga o'qish shuncha
     * xotirani band qiladi. Ustiga multipart uni ikkinchi marta nusxalaydi, ya'ni eng yomon
     * holatda 128 MB — arzon telefonda bu `OutOfMemoryError`. Bu yerda fayl **oqim bilan**
     * o'qiladi: xotirada faqat bufer qoladi.
     *
     * ⚠️ [path] so'rov **tugagunicha** turishi shart — Ktor qayta urinishda oqimni boshidan
     * so'raydi ([InputProvider] bloki har safar yangi manba beradi), ya'ni faylni oldindan
     * o'chirib bo'lmaydi.
     */
    suspend fun chatUploadFile(
        path: String,
        sizeBytes: Long,
        fileName: String,
        kind: ChatMediaKind,
        conversationId: String? = null,
        quality: MediaQuality? = null,
        onProgress: UploadProgress? = null,
        /** Sessiya id'si tug'ilganda chaqiriladi — bekor qilish va davom ettirish uchun. */
        onSession: ((String) -> Unit)? = null,
    ): AttachmentDto = UploadKeepAlive.holding {
        // Katta fayl BIR MARTALIK so'rov bilan ketmaydi: tarmoq uzilsa u noldan boshlanardi.
        if (sizeBytes >= RESUMABLE_ABOVE_BYTES) {
            return@holding resumableUpload(
                path = path,
                sizeBytes = sizeBytes,
                fileName = fileName,
                kind = kind,
                conversationId = conversationId,
                quality = quality,
                onProgress = onProgress,
                onSession = onSession,
            )
        }
        client.post(config.baseUrl + CHAT_PATH) {
            uploadTimeouts()
            trackUpload(onProgress)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        filePart(path, sizeBytes, fileName)
                        append("kind", kind.name)
                        if (kind.needsConversation && conversationId != null) {
                            append("conversationId", conversationId)
                        }
                        quality?.let { append("quality", it.name) }
                    },
                ),
            )
        }.body()
    }

    // ------------------------------------------------------------------------------------
    // Rezyumlanadigan yuklash — `POST /v1/media/upload/init` → `PUT …/part/{i}` → `complete`
    // ------------------------------------------------------------------------------------

    /**
     * Faylni **bo'laklab** yuboradi va uzilgan joydan davom eta oladi.
     *
     * Nega kerak: bir martalik `chat-upload` uzilganda noldan boshlanadi. 300 MB video
     * metroda 90% da uzilsa foydalanuvchi hammasini qaytadan yuboradi — va odatda ikkinchi
     * marta ham uzilib qoladi. Bu yerda esa server qaysi bo'laklarni olganini aytadi
     * (`received`) va faqat yetishmagani yuboriladi.
     *
     * Ketma-ketlik:
     * 1. **`init`** — kvota, ruxsat va disk shu yerda tekshiriladi, ya'ni rad javobi
     *    gigabayt emas, **bitta so'rov** turadi. Javobda `uploadId` va `chunkSize`.
     * 2. **`part/{index}`** — xom baytlar (multipart EMAS). Takroran yuborish zararsiz:
     *    bir xil indeks o'zini qayta yozadi.
     * 3. **`complete`** — bo'laklar birlashtiriladi va `chat-upload` bilan **aynan bir xil**
     *    ishlov beriladi; javob ham o'sha `AttachmentDto`.
     *
     * [resumeUploadId] berilsa yangi sessiya ochilmaydi — mavjudining holati o'qiladi.
     * Sessiya kamida 24 soat yashaydi.
     *
     * **Bo'laklar parallel ketadi** — server buni ochiq ruxsat etadi («any order and in
     * parallel»). Mobil tarmoqda bitta TCP oqimi kanalni to'ldirmaydi: kechikish katta,
     * oyna kichik, ya'ni bitta so'rov navbat bilan ketganda tezlik nominal tezlikning
     * yarmiga ham chiqmaydi. Bir vaqtda uchtasi ketsa kanal to'ladi.
     *
     * ⚠️ Xotira **cheklangan**: keyingi bo'lak faqat navbatda joy bo'shagach o'qiladi
     * ([partsInFlight]), ya'ni xotirada eng ko'pi bilan `IN_FLIGHT_BUDGET_BYTES` turadi.
     * Faylning o'zi baribir bitta oqim bilan, ketma-ket o'qiladi.
     */
    suspend fun resumableUpload(
        path: String,
        sizeBytes: Long,
        fileName: String,
        kind: ChatMediaKind,
        conversationId: String? = null,
        quality: MediaQuality? = null,
        onProgress: UploadProgress? = null,
        resumeUploadId: String? = null,
        onSession: ((String) -> Unit)? = null,
    ): AttachmentDto = UploadKeepAlive.holding {
        val session = resumeUploadId?.let { uploadStatus(it) }
            ?: initUpload(kind, conversationId, quality, fileName, sizeBytes)
        onSession?.invoke(session.uploadId)

        val chunkSize = session.chunkSize.coerceAtLeast(MIN_CHUNK_BYTES)
        val totalParts = ((sizeBytes + chunkSize - 1) / chunkSize).toInt()
        val alreadyReceived = session.received.toSet()

        // Jarayon **tugagan bo'laklar** bo'yicha sanaladi, ya'ni u parallel yuborishda ham
        // faqat oldinga yuradi. Hisobni bitta muteks himoya qiladi: `onProgress` ni ikki
        // korutin bir vaqtda chaqirsa ko'rsatkich sakrab ketardi.
        val progressLock = Mutex()
        var sent = alreadyReceived.size.toLong() * chunkSize

        coroutineScope {
            val slots = Semaphore(partsInFlight(chunkSize))
            SystemFileSystem.source(Path(path)).buffered().use { source ->
                for (index in 0 until totalParts) {
                    val remaining = sizeBytes - index.toLong() * chunkSize
                    val length = minOf(chunkSize.toLong(), remaining).toInt()
                    if (length <= 0) break
                    // Allaqachon serverda bo'lgan bo'lak baytlari ham o'qiladi va tashlab
                    // yuboriladi (`skip` ham xuddi shuni qiladi, lekin manba turiga bog'liq).
                    if (index in alreadyReceived) {
                        source.readByteArray(length)
                        continue
                    }
                    // ⚠️ Ruxsat baytlarni o'qishdan OLDIN olinadi: aks holda sikl butun
                    // faylni xotiraga o'qib, navbatda kutib turardi.
                    slots.acquire()
                    val chunk = source.readByteArray(length)
                    launch {
                        try {
                            uploadPart(session.uploadId, index, chunk)
                            progressLock.withLock {
                                sent += length
                                onProgress?.invoke((sent.toFloat() / sizeBytes).coerceIn(0f, MAX_REPORTED))
                            }
                        } finally {
                            slots.release()
                        }
                    }
                }
            }
        }
        // Hajm va bo'laklar soni e'lon qilinadi — server yig'ilganini shunga solishtiradi
        // (qarang [CompleteUploadBody]).
        completeUpload(session.uploadId, totalBytes = sizeBytes, parts = totalParts)
    }

    /**
     * Bir vaqtda ketadigan bo'laklar soni.
     *
     * Chegara **baytlarda**, sonda emas: server bo'lak hajmini o'zi tanlaydi va u kattaroq
     * bo'lsa parallelizm o'z-o'zidan kamayadi. Arzon telefonda uchta 8 MB lik bufer
     * `OutOfMemoryError` degani.
     */
    private fun partsInFlight(chunkSize: Int): Int =
        (IN_FLIGHT_BUDGET_BYTES / chunkSize).coerceIn(1, MAX_PARTS_IN_FLIGHT)

    /** Sessiya ochadi — `POST /v1/media/upload/init`. */
    private suspend fun initUpload(
        kind: ChatMediaKind,
        conversationId: String?,
        quality: MediaQuality?,
        fileName: String,
        totalBytes: Long,
    ): UploadSession = client.post(config.baseUrl + UPLOAD_INIT_PATH) {
        contentType(ContentType.Application.Json)
        setBody(
            InitUploadBody(
                kind = kind.name,
                conversationId = conversationId?.takeIf { kind.needsConversation },
                quality = quality?.name,
                fileName = fileName,
                totalBytes = totalBytes,
            ),
        )
    }.body()

    /** Uzilishdan keyin: qaysi bo'laklar yetib borgan — `GET /v1/media/upload/{id}`. */
    suspend fun uploadStatus(uploadId: String): UploadSession =
        client.get(config.baseUrl + uploadPath(uploadId)).body()

    /**
     * Bitta bo'lak — **xom baytlar**, multipart emas.
     *
     * `Content-Range` server tomonidan qabul qilinadi, lekin e'tiborsiz qoldiriladi:
     * baytlar qayerga tushishini yo'ldagi `index` hal qiladi.
     */
    private suspend fun uploadPart(uploadId: String, index: Int, bytes: ByteArray) {
        // Bo'lak — yagona **idempotent** so'rovimiz: bir xil indeks o'zini qayta yozadi,
        // ya'ni javob kelmay qolganda ham ko'r-ko'rona takrorlash xavfsiz. Shu sabab
        // qayta urinish aynan shu yerda: butun klientga `HttpRequestRetry` qo'yish xabar
        // yuborishni ham takrorlab, ikki nusxa xabar tug'dirishi mumkin edi.
        var attempt = 0
        while (true) {
            try {
                client.put(config.baseUrl + uploadPath(uploadId) + "/part/$index") {
                    uploadTimeouts()
                    contentType(ContentType.Application.OctetStream)
                    setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream))
                }
                return
            } catch (cancelled: CancellationException) {
                // Foydalanuvchi bekor qildi yoki ekran yopildi — qayta urinish mumkin emas.
                throw cancelled
            } catch (error: Throwable) {
                attempt++
                // Serverning "yo'q" javobi (kvota, ruxsat, sessiya eskirgan) takrorlashdan
                // o'zgarmaydi — faqat tarmoq uzilishi takrorlashga arziydi.
                if (attempt > PART_RETRIES || error is ResponseException) throw error
                delay(RETRY_BACKOFF_MS * attempt)
            }
        }
    }

    /** Bo'laklarni birlashtiradi — `POST /v1/media/upload/{id}/complete`. */
    private suspend fun completeUpload(
        uploadId: String,
        totalBytes: Long,
        parts: Int,
    ): AttachmentDto =
        client.post(config.baseUrl + uploadPath(uploadId) + "/complete") {
            // Birlashtirish va transkodlash serverda vaqt oladi — umumiy 15 soniyalik
            // chegara bu yerda ham yaramaydi.
            uploadTimeouts()
            contentType(ContentType.Application.Json)
            setBody(CompleteUploadBody(totalBytes = totalBytes, parts = parts))
        }.body()

    /**
     * Sessiyani tashlab yuboradi — `DELETE /v1/media/upload/{id}`.
     *
     * Ixtiyoriy (tegilmagan sessiya o'zi eskiradi), lekin foydalanuvchi yuborishni bekor
     * qilganda serverdagi joyni **darhol** bo'shatadi. Xatolar yutiladi: bekor qilishning
     * o'zi muvaffaqiyatsiz bo'lsa ham foydalanuvchiga ko'rsatadigan narsa yo'q.
     */
    suspend fun cancelUpload(uploadId: String) {
        runCatching { client.delete(config.baseUrl + uploadPath(uploadId)) }
    }

    private fun uploadPath(uploadId: String) = "$UPLOAD_PATH/$uploadId"

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
     * O'sha qism, lekin diskdagi fayldan.
     *
     * Hajm **oldindan** beriladi: usiz `Content-Length` noma'lum bo'lib qoladi va so'rov
     * `chunked` ketadi — o'shanda [trackUpload] foizni hisoblay olmaydi (UI aniqlanmagan
     * halqaga tushadi), ba'zi proksilar esa `chunked` multipart'ni umuman qabul qilmaydi.
     */
    private fun FormBuilder.filePart(path: String, sizeBytes: Long, fileName: String) =
        append(
            key = "file",
            value = InputProvider(sizeBytes) { SystemFileSystem.source(Path(path)).buffered() },
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
    /**
     * Yuklash uchun **alohida** vaqt chegaralari.
     *
     * Umumiy klientda `requestTimeoutMillis = 15 s` — u oddiy JSON so'rovlariga mo'ljallangan
     * va butun so'rovga (jumladan tananing oxirgi baytigacha) tegishli. 50 MB video mobil
     * internetda 15 soniyada hech qachon ketmaydi, ya'ni yuklash **doim** «so'rov vaqti
     * tugadi» bilan uzilardi.
     *
     * Shuning uchun bu yerda so'rov chegarasi **olib tashlanadi** — Telegram ham yuklashni
     * vaqt bo'yicha to'xtatmaydi. O'rniga [UPLOAD_SOCKET_TIMEOUT_MS] qoladi: u "hech narsa
     * uzatilmayapti" holatini ushlaydi, ya'ni tarmoq uzilsa yuklash osilib qolmaydi, lekin
     * sekin internetda ishlab turgan yuklash uzilmaydi.
     */
    private fun HttpRequestBuilder.uploadTimeouts() {
        timeout {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = UPLOAD_SOCKET_TIMEOUT_MS
        }
    }

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

        /** Rezyumlanadigan yuklashning bazasi. */
        const val UPLOAD_PATH = "media/upload"
        const val UPLOAD_INIT_PATH = "media/upload/init"

        /**
         * Shundan kattasi rezyumlanadigan yo'ldan ketadi.
         *
         * 10 MB — spec'ning o'z tavsiyasi (`chat-upload` tavsifi: «~10 MB dan kattasi
         * uchun `/v1/media/upload/…` ni afzal ko'ring»). Kichik fayl uchun uchta so'rov
         * o'rniga bitta multipart arzonroq va uzilish ehtimoli ham deyarli yo'q.
         */
        const val RESUMABLE_ABOVE_BYTES = 10L * 1024 * 1024

        /** Server juda kichik `chunkSize` bersa ham bo'lak shundan kichik bo'lmaydi. */
        const val MIN_CHUNK_BYTES = 256 * 1024

        /**
         * Bir vaqtda "havoda" turadigan baytlar — parallel bo'laklar shu byudjetdan
         * bo'linadi ([partsInFlight]).
         */
        const val IN_FLIGHT_BUDGET_BYTES = 12 * 1024 * 1024

        /**
         * Undan ortig'i foyda bermaydi: mobil kanal to'lgandan keyin qo'shimcha oqim
         * faqat kechikish va xotira qo'shadi.
         */
        const val MAX_PARTS_IN_FLIGHT = 3

        /** Bitta bo'lakni necha marta qayta yuborish (tarmoq uzilishida). */
        const val PART_RETRIES = 2

        /** Qayta urinishlar orasidagi kutish — urinish raqamiga ko'paytiriladi. */
        const val RETRY_BACKOFF_MS = 1_000L

        const val MAX_REPORTED = 0.99f

        /**
         * Soket jimligining chegarasi — 60 s.
         *
         * Bu "umuman uzatilmayapti" degani (tarmoq yo'qoldi, server javob bermay qoldi).
         * Sekin, lekin ishlab turgan yuklashga ta'sir qilmaydi: har bo'lak yuborilganda
         * hisob noldan boshlanadi.
         */
        const val UPLOAD_SOCKET_TIMEOUT_MS = 60_000L
    }
}

/**
 * Rezyumlanadigan yuklash sessiyasi — `UploadProgressDto` ning ko'zgusi.
 *
 * Generatsiya qilingan DTO ishlatilmaydi (`MediaUploader` ning qolgan qismi bilan bir xil
 * sabab: bu klass generatsiyadan mustaqil bo'lishi kerak), lekin shakl **aynan** bir xil.
 *
 * [received] — serverga yetib borgan bo'lak indekslari, o'sish tartibida. Uzilishdan keyin
 * shu ro'yxatda **yo'q** bo'laklar yuboriladi.
 */
@Serializable
data class UploadSession(
    val uploadId: String,
    val received: List<Int> = emptyList(),
    /** Bir bo'lakning hajmi. Faqat oxirgisi qisqaroq bo'lishi mumkin. */
    val chunkSize: Int,
    val totalBytes: Long = 0,
    /** ISO-8601. Kamida 24 soat — metroda uzilgan yuborish uni bosib o'tadi. */
    val expiresAt: String? = null,
)

/** `POST /v1/media/upload/init` ning tanasi. */
@Serializable
private data class InitUploadBody(
    val kind: String,
    val conversationId: String? = null,
    val quality: String? = null,
    val fileName: String? = null,
    /**
     * Butun faylning hajmi — kvota shu bo'yicha oldindan band qilinadi.
     *
     * `null` bo'lishi mumkin (`03-VIDEO_UPLOAD_STREAMING_RESPONSE.md` §1): u holda sessiya
     * **oqimli** bo'ladi — hajm hali noma'lum (video siqilayotgan paytda yuborila boshlaydi)
     * va u [CompleteUploadBody] da e'lon qilinadi. Server bunday sessiyaga rezerv (sukut
     * bo'yicha 2 GB) hisobidan joy ajratadi va kvotani `complete` da to'g'rilaydi.
     *
     * ⚠️ `explicitNulls = false` — `null` bo'lsa kalit UMUMAN yuborilmaydi, ya'ni server
     * uni "berilmagan" deb ko'radi (`0` deb emas: nol — klient xatosi va `422` beradi).
     */
    val totalBytes: Long? = null,
)

/**
 * `POST /v1/media/upload/{id}/complete` ning tanasi
 * (`03-VIDEO_UPLOAD_STREAMING_RESPONSE.md` §2).
 *
 * Ikkala maydon ham oqimli sessiyada **majburiy**, hajmi ma'lum sessiyada esa ixtiyoriy —
 * lekin biz ularni **doim** yuboramiz va bu ataylab: server yig'ilgan hajmni va bo'laklar
 * sonini e'lon qilinganiga solishtiradi, ya'ni teshiksiz, lekin ERTA to'xtagan qator
 * (`0,1,2` yetib bordi, `3,4,5` yo'q) endi "muvaffaqiyat" bo'lib qaytmaydi. Usiz server
 * kesilgan videoni yig'ib, foydalanuvchiga tugagan yuborish sifatida ko'rsatardi.
 */
@Serializable
private data class CompleteUploadBody(
    val totalBytes: Long,
    val parts: Int,
)

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
