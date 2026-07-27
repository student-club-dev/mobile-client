package dev.feature.connections.domain.model

import kotlinx.datetime.Instant

/**
 * **Qisqa profil** — odam ko'rsatiladigan hamma joyda bir xil shakl (`StudentSummary`).
 * Qidiruv, so'rovlar, bog'langanlar va suhbatlar ro'yxati — hammasi shuni qaytaradi.
 *
 * ⚠️ [online] / [lastSeenAt] **`Connections` bo'limida doim `false` / `null`** — backend ularni
 * u yerda to'ldirmaydi. Haqiqiy qiymat faqat `GET /v1/conversations` javobida va WS
 * `presence:update` hodisasida keladi. Onlayn indikatorini qidiruv natijasiga bog'lamang.
 */
data class StudentSummary(
    val id: String,
    /** `Profiles` bo'limida o'rnatiladi — o'rnatmagan talabada `null`. */
    val username: String? = null,
    /** `firstName + " " + lastName`; ikkalasi ham bo'sh bo'lsa `null`. */
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val online: Boolean = false,
    val lastSeenAt: Instant? = null,
) {
    /** Ekranda ko'rsatiladigan nom: to'liq ism → `@username` → umumiy zaxira. */
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: "Talaba"

    /** Avatar o'rnidagi harf. */
    val initial: String
        get() = displayName.trimStart('@').firstOrNull()?.uppercase() ?: "?"
}

/**
 * Qidiruvdagi **"men bilan munosabati"**. Bog'lanish yozuvining holati ([ConnectionStatus])
 * bilan ADASHTIRMANG — bu ikki xil enum.
 */
enum class ConnectionView {
    /** Bog'lanish yo'q (yoki avval rad etilgan) — «Bog'lanish» tugmasi. */
    NONE,

    /** **Men** so'rov yubordim, javob kutilyapti — «Yuborildi» (o'chirilgan). */
    PENDING_OUT,

    /** **U** menga so'rov yubordi — «Javob berish». */
    PENDING_IN,

    /** Bog'langanmiz — «Xabar yozish». */
    CONNECTED,
}

/** Bog'lanish yozuvining server tomondagi holati (`send`/`accept` javobida). */
enum class ConnectionStatus { PENDING, ACCEPTED, DECLINED }

/** Kutilayotgan so'rovlar yo'nalishi — so'rovda **kichik harflar** bilan ketadi. */
enum class RequestDirection { INCOMING, OUTGOING }

/** Shikoyat sababi. */
enum class ReportReason { SPAM, SCAM, HARASSMENT, INAPPROPRIATE, OTHER }

/** Qidiruv natijasidagi bitta talaba — qisqa profil + men bilan munosabati. */
data class SearchedStudent(
    val student: StudentSummary,
    val connectionStatus: ConnectionView,
)

/** Bog'lanish yozuvi — so'rov yuborish / qabul qilish javobi. */
data class Connection(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val status: ConnectionStatus,
    val createdAt: Instant,
    val respondedAt: Instant? = null,
)

/**
 * Kutilayotgan so'rov. [connectionId] — **so'rovning** id'si; `accept`/`decline` aynan shuni
 * oladi, talabaning id'sini emas.
 */
data class ConnectionRequest(
    val connectionId: String,
    val student: StudentSummary,
    val createdAt: Instant,
)

/**
 * Bog'langan talaba. Bu yerda `connectionId` **yo'q** — bog'lanishni uzish uchun
 * [student].`id` ishlatiladi (`DELETE /v1/connections/{studentId}`).
 */
data class ConnectedStudent(
    val student: StudentSummary,
    val connectedAt: Instant,
)

/**
 * Sahifalangan ro'yxat. ⚠️ Bu bo'limda sahifa **`1` dan** boshlanadi (feed'dagi `0` emas)
 * va parametrlar **query**'da ketadi.
 */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> empty(): Page<T> = Page(emptyList(), 1, 20, 0, false)
    }
}
