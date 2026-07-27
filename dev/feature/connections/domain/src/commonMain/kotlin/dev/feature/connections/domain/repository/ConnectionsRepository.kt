package dev.feature.connections.domain.repository

import dev.core.common.Resource
import dev.feature.connections.domain.model.ConnectedStudent
import dev.feature.connections.domain.model.Connection
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.Page
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.RequestDirection
import dev.feature.connections.domain.model.SearchedStudent

/**
 * **Bog'lanishlar** (LinkedIn uslubi): talaba topish → so'rov → qabul/rad → bog'langanlar,
 * ustiga blok va shikoyat.
 *
 * Bu — **chat'ning eshigi**: bog'lanmagan talabalar bilan suhbat ochib ham, yozib ham
 * bo'lmaydi (`403 NOT_CONNECTED`).
 *
 * Kesh yo'q — hamma metod to'g'ridan-to'g'ri backendga boradi. Sabab: bog'lanish holati
 * ikki tomonlama va tez o'zgaradi, eskirgan kesh esa "Bog'lanish" tugmasini xato
 * ko'rsatardi. Ro'yxatlar sahifama-sahifa yuklanadi.
 */
interface ConnectionsRepository {

    /**
     * Talaba qidirish. Moslik: `username` **boshidan**, `firstName`/`lastName` **ichidan** —
     * har biri ALOHIDA, ya'ni "Alisher Valiyev" deb qidirilsa hech nima topilmaydi.
     * O'zingiz va blok bilan bog'liq talabalar natijaga tushmaydi.
     */
    suspend fun search(query: String, page: Int = 1, size: Int = DEFAULT_PAGE_SIZE): Resource<Page<SearchedStudent>>

    /**
     * So'rov yuborish. Agar u odam sizga allaqachon so'rov yuborgan bo'lsa — javobda
     * `status = ACCEPTED` keladi, ya'ni **darhol bog'lanish** sodir bo'ldi (C1).
     *
     * Rad etilgan so'rovni **24 soat** davomida qayta yuborib bo'lmaydi (`429`, C10) —
     * server qolgan vaqtni aytmaydi.
     */
    suspend fun sendRequest(studentId: String): Resource<Connection>

    /** Kutilayotgan (`PENDING`) so'rovlar — kiruvchi yoki chiquvchi. */
    suspend fun requests(
        direction: RequestDirection,
        page: Int = 1,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Resource<Page<ConnectionRequest>>

    /** Qabul qilish. [connectionId] — **so'rovning** id'si (`ConnectionRequest.connectionId`). */
    suspend fun accept(connectionId: String): Resource<Connection>

    /** Rad etish. Yuboruvchi uchun 24 soatlik sovish muddati boshlanadi. */
    suspend fun decline(connectionId: String): Resource<Unit>

    /** Bog'langanlar ro'yxati. */
    suspend fun connections(page: Int = 1, size: Int = DEFAULT_PAGE_SIZE): Resource<Page<ConnectedStudent>>

    /**
     * Bog'lanishni uzish — [studentId] **ikkinchi talabaning** id'si.
     * Uzilgandan keyin sovish muddati YO'Q, darhol qayta so'rov yuborish mumkin.
     */
    suspend fun disconnect(studentId: String): Resource<Unit>

    /**
     * Bloklash (idempotent). Bog'lanish/so'rov o'chadi, ikki tomon bir-birini qidiruvda
     * ko'rmaydi va so'rov yubora olmaydi.
     */
    suspend fun block(studentId: String): Resource<Unit>

    /** Blokni yechish (idempotent). ⚠️ Avvalgi bog'lanish **tiklanmaydi**. */
    suspend fun unblock(studentId: String): Resource<Unit>

    /**
     * Shikoyat. [targetStudentId] **yoki** [messageId] — aynan bittasi berilishi shart.
     * Takroriy shikoyat eskisiga birlashtiriladi; foydalanuvchi uchun farqi yo'q.
     */
    suspend fun report(
        reason: ReportReason,
        targetStudentId: String? = null,
        messageId: String? = null,
        note: String? = null,
    ): Resource<Unit>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
