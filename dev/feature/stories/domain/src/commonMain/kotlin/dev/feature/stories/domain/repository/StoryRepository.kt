package dev.feature.stories.domain.repository

import dev.core.common.Resource
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryArchivePage
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.model.StoryViewerPage

/**
 * Story — 24 soatlik hikoyalar (`handoff/07-STORIES.md`).
 *
 * Eshik **chat bilan aynan bir xil**: faqat bog'langan talabalar ko'radi, bloklangan odam
 * hech qachon ko'rmaydi. Bog'lanish uzilsa hikoya lentadan **darhol** yo'qoladi.
 *
 * Kesh yo'q — ma'lumot tez eskiradi (har hikoya 24 soat yashaydi va istalgan payt muddati
 * o'tishi mumkin). Muddati o'tgan story [feed] va [mine] dan **hech qachon** qaytmaydi,
 * ya'ni `expiresAt` ni klientda tekshirish shart emas. Yagona istisno — [archive]: u
 * ataylab **faqat** muddati o'tganlarini beradi va uni faqat muallif ko'radi.
 */
interface StoryRepository {

    /**
     * `GET /v1/stories/feed` — muallif bo'yicha guruhlangan va **allaqachon saralangan**.
     * Qayta saralamang.
     */
    suspend fun feed(): Resource<List<StoryGroup>>

    /** `GET /v1/stories/mine` — faol hikoyalarim + **haqiqiy** `viewsCount`. */
    suspend fun mine(): Resource<List<Story>>

    /**
     * `GET /v1/stories/archive` — **muddati o'tgan** hikoyalarim (profildagi «Arxivlangan
     * postlar»).
     *
     * ⚠️ Faqat muallifga: arxiv lentaga ham, `mine` ga ham hech qachon tushmaydi va uni
     * boshqa hech kim o'qiy olmaydi. Ya'ni 24 soat tugagach hikoya **yo'qolmaydi**, egasi
     * uchun shu ro'yxatga o'tadi (`STORY_ARCHIVE_BACKEND.md`).
     */
    suspend fun archive(page: Int = 1, size: Int = DEFAULT_ARCHIVE_PAGE): Resource<StoryArchivePage>

    /**
     * Rasm/video yuklab, hikoya yaratadi: `POST /v1/media/chat-upload` → `POST /v1/stories`.
     *
     * ⚠️ Video darhol tayyor bo'lmasligi mumkin — yuklash `PROCESSING` qaytarsa server
     * `422 MEDIA_NOT_READY` beradi. Implementatsiya buni **o'zi kutadi** (bir necha marta
     * qayta uriniladi), ya'ni chaqiruvchi WS hodisasini kuzatishi shart emas.
     *
     * [onProgress] — yuborilgan baytlar ulushi (`0f..1f`). Faqat **fayl ketayotgan** qismni
     * qamraydi: undan keyingi `POST /v1/stories` va transkodni kutish foizsiz o'tadi
     * (qancha davom etishini oldindan bilib bo'lmaydi), shuning uchun u yerda `1f`
     * berilmaydi va UI "tayyorlanmoqda" holatiga o'tadi.
     */
    suspend fun create(
        bytes: ByteArray,
        fileName: String,
        caption: String?,
        onProgress: ((Float) -> Unit)? = null,
    ): Resource<Story>

    /**
     * O'sha hikoya yaratish, lekin media **diskdagi fayldan** yuklanadi.
     *
     * Video uchun: story videosi 48 MB gacha bo'lishi mumkin va uni `ByteArray` ga o'qish
     * (ustiga multipart nusxasi) arzon telefonni xotiradan qoqib tashlardi.
     */
    suspend fun createFromFile(
        path: String,
        sizeBytes: Long,
        fileName: String,
        caption: String?,
        onProgress: ((Float) -> Unit)? = null,
    ): Resource<Story>

    /**
     * `POST /v1/stories/{id}/view` — har hikoya ochilganda. **Idempotent**, o'z story'ingiz
     * hisoblanmaydi.
     *
     * Fon amali: xatosi ko'rsatilmaydi va hech narsani to'smaydi (`Unit` qaytadi).
     */
    suspend fun markViewed(storyId: String)

    /** `GET /v1/stories/{id}/views` — **faqat muallifga**, boshqasiga `403`. */
    suspend fun viewers(storyId: String, page: Int = 1, size: Int = DEFAULT_VIEWERS_PAGE): Resource<StoryViewerPage>

    /** `DELETE /v1/stories/{id}` — faqat muallif. Story **tahrirlanmaydi**, o'chirib qayta qo'yiladi. */
    suspend fun delete(storyId: String): Resource<Unit>

    companion object {
        const val DEFAULT_VIEWERS_PAGE = 30

        /** Arxiv bir sahifada — to'rda 3 ustun, ya'ni 10 qator. */
        const val DEFAULT_ARCHIVE_PAGE = 30
    }
}
