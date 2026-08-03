package dev.feature.stories.data.repository

import dev.core.common.Resource
import dev.core.common.map
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.StoriesApi
import dev.core.network.generated.model.AttachmentDto
import dev.core.network.generated.model.CreateStoryDto
import dev.core.network.media.ChatMediaKind
import dev.core.network.media.MediaUploader
import dev.core.network.response.safeCall
import dev.feature.stories.data.mapper.toDomain
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryArchivePage
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.model.StoryViewerPage
import dev.feature.stories.domain.repository.StoryRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Story repository'si — generatsiya qilingan [StoriesApi] ustida (`handoff/07-STORIES.md`).
 *
 * Kesh **yo'q**: lavha 24 soat yashaydi va istalgan payt muddati o'tishi mumkin, shu
 * jumladan foydalanuvchi uni ochib turganda. Eskirgan kesh bu yerda faqat "yo'q lavhani
 * ko'rsatish" xatosiga olib kelardi.
 */
class StoryRepositoryImpl(
    private val api: StoriesApi,
    private val media: MediaUploader,
    private val connectivity: NetworkConnectivity,
    /**
     * API origin'i — media havolalarini to'liq holga keltirish uchun (qarang [toDomain]).
     * Backend ularni nisbiy qaytaradi va video pleyer bunday havolani ocholmaydi.
     */
    private val apiOrigin: String,
) : StoryRepository {

    override suspend fun feed(): Resource<List<StoryGroup>> =
        safeCall(connectivity) { api.feed().body() }.map { dto -> dto.items.map { it.toDomain(apiOrigin) } }

    override suspend fun mine(): Resource<List<Story>> =
        safeCall(connectivity) { api.storiesMine().body() }.map { dto -> dto.items.map { it.toDomain(apiOrigin) } }

    /**
     * Arxiv — muddati o'tgan lavhalarim. Bu yerda ham kesh yo'q: ro'yxat faqat profilning
     * bitta bo'limida ko'rinadi va o'sha yerda bir marta o'qiladi.
     */
    override suspend fun archive(page: Int, size: Int): Resource<StoryArchivePage> =
        safeCall(connectivity) { api.archive(page = page, size = size).body() }
            .map { it.toDomain(apiOrigin) }

    /**
     * Ikki qadam: media yuklash → `POST /v1/stories`.
     *
     * `conversationId` **yuborilmaydi** — story'da suhbat yo'q (§1).
     *
     * ⚠️ **`MEDIA_NOT_READY` kutiladi.** Video yuklangach server uni transkod qiladi va shu
     * vaqt ichida `POST /v1/stories` `422` beradi. Hujjat `media:ready` WS hodisasini
     * kutishni taklif qiladi, lekin u chat modulida yashaydi — story qatlamini chatga
     * bog'lash bitta hodisa uchun juda qimmat. O'rniga qisqa qayta urinish: transkod odatda
     * bir necha soniya, [CREATE_ATTEMPTS] × [RETRY_DELAY_MS] esa undan uzunroq oyna beradi.
     */
    override suspend fun create(
        bytes: ByteArray,
        fileName: String,
        caption: String?,
        onProgress: ((Float) -> Unit)?,
    ): Resource<Story> = create(fileName, caption) { kind ->
        media.chatUpload(bytes = bytes, fileName = fileName, kind = kind, onProgress = onProgress)
    }

    override suspend fun createFromFile(
        path: String,
        sizeBytes: Long,
        fileName: String,
        caption: String?,
        onProgress: ((Float) -> Unit)?,
    ): Resource<Story> = create(fileName, caption) { kind ->
        media.chatUploadFile(
            path = path,
            sizeBytes = sizeBytes,
            fileName = fileName,
            kind = kind,
            onProgress = onProgress,
        )
    }

    /**
     * Ikkala yo'lning umumiy qismi — yuklashdan **keyingi** hammasi.
     *
     * Faqat yuklash bosqichi farq qiladi (baytlardan yoki fayldan), qayta urinish mantiqi esa
     * bir xil; ikki joyda takrorlansa ular albatta bir-biridan ajralib ketardi.
     */
    private suspend fun create(
        fileName: String,
        caption: String?,
        upload: suspend (ChatMediaKind) -> AttachmentDto,
    ): Resource<Story> = safeCall(connectivity) {
        val kind = if (isVideo(fileName)) ChatMediaKind.STORY_VIDEO else ChatMediaKind.STORY_IMAGE
        val uploaded = upload(kind)
        val body = CreateStoryDto(
            mediaId = uploaded.id,
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
        )

        var lastError: Throwable? = null
        repeat(CREATE_ATTEMPTS) { attempt ->
            try {
                return@safeCall api.storiesCreate(body).body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResponseException) {
                // Faqat "hali tayyor emas" kutiladi; qolgan xatolar darhol yuqoriga chiqadi
                // (masalan `STORY_LIMIT_REACHED` — kutish uni tuzatmaydi).
                if (!e.isMediaNotReady()) throw e
                lastError = e
                if (attempt < CREATE_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IllegalStateException("Story yaratilmadi")
    }.map { it.toDomain(apiOrigin) }

    /**
     * Fon amali: xatosi **yutiladi**. Bu ko'rish hisoblagichi uchun signal va u
     * foydalanuvchining lavhani ko'rishini hech qanday holatda to'smasligi kerak.
     *
     * Chegara — daqiqasiga 120 so'rov (§4): tez surib ko'rish normal, lekin undan
     * oshsa 429 keladi va u ham jimgina yutiladi.
     */
    override suspend fun markViewed(storyId: String) {
        try {
            api.view(storyId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Ataylab jim.
        }
    }

    override suspend fun viewers(storyId: String, page: Int, size: Int): Resource<StoryViewerPage> =
        safeCall(connectivity) { api.viewers(id = storyId, page = page, size = size).body() }
            .map { it.toDomain() }

    override suspend fun delete(storyId: String): Resource<Unit> =
        safeCall(connectivity) { api.storiesRemove(storyId).body() }

    /**
     * Tur **fayl nomidan** aniqlanadi — bu faqat `kind` (limit va ishlov yo'li) uchun
     * ko'rsatma, server turni baribir baytlardan aniqlaydi.
     */
    private fun isVideo(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private suspend fun ResponseException.isMediaNotReady(): Boolean {
        if (response.status.value != HTTP_UNPROCESSABLE) return false
        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        return body.contains(MEDIA_NOT_READY)
    }

    private companion object {
        const val HTTP_UNPROCESSABLE = 422
        const val MEDIA_NOT_READY = "MEDIA_NOT_READY"

        /** Transkod odatda bir necha soniya — 5 × 2 s ≈ 10 s oyna. */
        const val CREATE_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 2_000L

        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "qt")
    }
}
