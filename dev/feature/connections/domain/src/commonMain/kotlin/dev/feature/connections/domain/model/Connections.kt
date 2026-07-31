package dev.feature.connections.domain.model

import kotlinx.datetime.Instant

/**
 * **Qisqa profil** — odam ko'rsatiladigan hamma joyda bir xil shakl (`StudentSummary`).
 * Qidiruv, so'rovlar, bog'langanlar va suhbatlar ro'yxati — hammasi shuni qaytaradi.
 *
 * [online] / [lastSeenAt] **hamma joyda haqiqiy** (backend Redis'dan o'qiydi). Ular
 * ko'rsatilgan talabaning `lastSeenVisibility` sozlamasiga bo'ysunadi: `EVERYONE` — doim,
 * `CONNECTIONS` (sukut) — faqat bog'langanlarga, `NOBODY` — hech kimga. Yashirilganda
 * server `online = false`, `lastSeenAt = null` yuboradi, ya'ni klientda alohida tekshiruv
 * kerak emas.
 */
data class StudentSummary(
    val id: String,
    /** `Profiles` bo'limida o'rnatiladi — o'rnatmagan talabada `null`. */
    val username: String? = null,
    /** `firstName + " " + lastName`; ikkalasi ham bo'sh bo'lsa `null`. */
    val fullName: String? = null,
    val avatarUrl: String? = null,
    /**
     * Talabaning o'zi profilida ko'rsatgan universitet — **erkin satr** (`emis-142`).
     * Serverda universitetlar katalogi yo'q, shuning uchun nom qaytmaydi: uni local
     * `University` jadvalidan shu id bo'yicha topamiz.
     */
    val universityId: String? = null,
    val gender: Gender? = null,
    /** `"1".."4"` yoki `"MASTER"` — profildagi bilan bir xil shakl. */
    val courseYear: String? = null,
    val online: Boolean = false,
    val lastSeenAt: Instant? = null,
    /**
     * Profil rasmlari, tartib bo'yicha. **Birinchi element doim [avatarUrl] ga teng**
     * (`handoff/08-PROFILE.md` §3).
     *
     * Bo'sh ro'yxat — rasm qo'ymagan talaba; o'shanda [avatarUrl] ham `null` bo'lishi mumkin
     * va bosh harf ko'rsatiladi. Maydon **hech qachon `null` emas**.
     */
    val photos: List<StudentPhoto> = emptyList(),
    /**
     * Tarjimayi hol, 140 belgigacha. Havola, `@handle` va telefon raqami **serverda rad
     * etiladi**, ya'ni bu yerda doim oddiy matn — link detection kerak emas.
     */
    val bio: String? = null,
    /**
     * E.164 formatdagi raqam yoki `null`.
     *
     * ⚠️ Sukut sozlama `NOBODY`, ya'ni **ko'pchilikda `null` bo'ladi**. `null` bo'lsa qatorni
     * umuman chizmang — bo'sh "Telefon: —" foyda bermaydi (`handoff/08-PROFILE.md` §3).
     */
    val phoneNumber: String? = null,
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
 * Profil rasmi — qisqa profildagi va o'z profilimizdagi rasm bir xil shaklda.
 *
 * [url] **token bilan** so'raladi (`Authorization: Bearer`), ya'ni uni oddiy `Image.load`
 * bilan ochib bo'lmaydi — ilovaning rasm klienti sarlavhani o'zi qo'yadi
 * (`createImageHttpClient`).
 */
data class StudentPhoto(
    val id: String,
    val url: String,
    /** Kichik nusxa; kelmasa [url] ning o'zi ishlatiladi. */
    val thumbUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    /** Ro'yxatda ko'rsatiladigan havola — kichik nusxa bo'lsa o'sha. */
    val previewUrl: String get() = thumbUrl?.takeIf { it.isNotBlank() } ?: url
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

/** Talabaning jinsi — profilda ko'rsatiladi va `GET /v1/students` da filtr sifatida ketadi. */
enum class Gender { MALE, FEMALE }

/** Ro'yxat tartibi: `RECENT` — eng yangi hisoblar birinchi (sukut), `NAME` — ism bo'yicha. */
enum class StudentSort { RECENT, NAME }

/**
 * `GET /v1/students` filtri. **Hammasi ixtiyoriy**: bo'sh filtr = sahifalangan to'liq
 * ro'yxat. Filtrlar bir-birini toraytiradi (AND), ko'p qiymatli filtr ichida esa OR.
 *
 * [query] — username (prefiks) **yoki** ism/familiya (ichidan); registrga sezgir emas.
 * Ikki so'zli qidiruv ("Alisher Valiyev") ishlamaydi — server har bo'lakni alohida emas,
 * butun satrni solishtiradi.
 */
data class StudentFilter(
    val query: String? = null,
    /** `emis-142` ko'rinishidagi id'lar — aniq moslik, ro'yxat ichida OR. */
    val universityIds: List<String> = emptyList(),
    val genders: List<Gender> = emptyList(),
    /** `"1".."4"`, `"MASTER"`. */
    val courseYears: List<String> = emptyList(),
    val birthYearFrom: Int? = null,
    val birthYearTo: Int? = null,
    /** Munosabat bo'yicha toraytirish; `NONE` — "hali hech qanday munosabat yo'q". */
    val connectionStatus: ConnectionView? = null,
    val sort: StudentSort = StudentSort.RECENT,
) {
    val isEmpty: Boolean
        get() = query.isNullOrBlank() && universityIds.isEmpty() && genders.isEmpty() &&
            courseYears.isEmpty() && birthYearFrom == null && birthYearTo == null &&
            connectionStatus == null
}

/** Ro'yxatdagi bitta talaba — qisqa profil + men bilan munosabati. */
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
 * Bloklangan talaba (`GET /v1/blocks`).
 *
 * Ro'yxatda **faqat SIZ bloklaganlaringiz** bo'ladi. Sizni kim bloklagani serverda ataylab
 * berilmaydi — ya'ni bu ro'yxatni "meni kim bloklagan" deb o'qib bo'lmaydi va UI ham shunday
 * tushuntirishi kerak.
 *
 * [student] ning `online` va `lastSeenAt` maydonlari bu yerda **doim maskalangan**
 * (`false` / `null`): blok qilingandan keyin presence umuman ko'rinmaydi. Shu sabab ularni
 * qatorda ko'rsatmaymiz — aks holda foydalanuvchi "hammasi oflayn" degan yolg'on xulosaga keladi.
 */
data class BlockedStudent(
    val student: StudentSummary,
    val blockedAt: Instant,
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
