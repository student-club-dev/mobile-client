package dev.feature.stories.domain.repository

import dev.core.common.Resource
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.model.StoryViewerPage

/**
 * Story — 24 soatlik lavhalar (`handoff/07-STORIES.md`).
 *
 * Eshik **chat bilan aynan bir xil**: faqat bog'langan talabalar ko'radi, bloklangan odam
 * hech qachon ko'rmaydi. Bog'lanish uzilsa lavha lentadan **darhol** yo'qoladi.
 *
 * Kesh yo'q — ma'lumot tez eskiradi (har lavha 24 soat yashaydi va istalgan payt muddati
 * o'tishi mumkin). Muddati o'tgan story serverdan **hech qachon** qaytmaydi, ya'ni
 * `expiresAt` ni klientda tekshirish shart emas.
 */
interface StoryRepository {

    /**
     * `GET /v1/stories/feed` — muallif bo'yicha guruhlangan va **allaqachon saralangan**.
     * Qayta saralamang.
     */
    suspend fun feed(): Resource<List<StoryGroup>>

    /** `GET /v1/stories/mine` — faol lavhalarim + **haqiqiy** `viewsCount`. */
    suspend fun mine(): Resource<List<Story>>

    /**
     * Rasm/video yuklab, lavha yaratadi: `POST /v1/media/chat-upload` → `POST /v1/stories`.
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
     * O'sha lavha yaratish, lekin media **diskdagi fayldan** yuklanadi.
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
     * `POST /v1/stories/{id}/view` — har lavha ochilganda. **Idempotent**, o'z story'ingiz
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
    }
}
