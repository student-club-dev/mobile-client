package dev.feature.profile.domain.model

/**
 * Foydalanuvchining profil ma'lumotlari — Firebase Auth bermaydigan maydonlar.
 *
 * Manba (offline-first):
 * - local kesh: SQLDelight `ProfileEntity` (yagona haqiqat UI uchun),
 * - masofaviy: REST `/v1/profile/me` (real API) yoki Firestore `users/{uid}` (backendsiz rejim).
 */
data class UserProfile(
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,   // E.164, masalan "+998901234567"
    val role: String? = null,          // "STUDENT" | "BUSINESS" | "EMPLOYER" | "UNIVERSITY"
    val universityId: String? = null,
    val universityEmail: String? = null,
    /**
     * Yashash manzili — e'lon geo katalogi bilan **bir xil id fazosida**
     * ("TOSHKENT_SHAHRI" / "CHILONZOR").
     *
     * Nima uchun kerak: yangi ish e'lonlari digesti talabaga MOS bo'lsa yuboriladi, moslik
     * esa "universiteti bir xil **YOKI** e'lon shu tumanda" degani
     * (`02-PUSH_CATALOG_RESPONSE.md` §4). Manzilsiz talaba shartning geo yarmini oladi
     * emas — faqat universiteti bo'yicha mos e'lonlarni ko'radi.
     *
     * `PATCH /v1/profile/me` yozadi, `GET /v1/profile/me` esa qaytaradi (2026-08-05 dan).
     */
    val regionId: String? = null,
    val districtId: String? = null,
    val birthYear: Int? = null,
    val courseYear: String? = null,    // "1".."4" | "MASTER"
    /**
     * "MALE" | "FEMALE". `GET /v1/students?gender=` filtri aynan shu maydonga tayanadi —
     * ko'rsatilmasa, talaba jins bo'yicha qidiruvga umuman tushmaydi.
     */
    val gender: String? = null,
    /**
     * Kim `online` / `lastSeenAt` ni ko'radi: "EVERYONE" | "CONNECTIONS" | "NOBODY".
     * `null` — server sukut qiymatini ("CONNECTIONS") qo'llaydi.
     */
    val lastSeenVisibility: String? = null,
    /**
     * Raqamni kim ko'radi: "EVERYONE" | "CONNECTIONS" | "NOBODY".
     *
     * ⚠️ Server sukuti — **`NOBODY`** (`lastSeenVisibility` niki esa `CONNECTIONS`):
     * talabalar raqamini ko'rsatishga rozilik bermagan va ochiq sukut spam qo'ng'iroqqa
     * olib kelardi (`handoff/08-PROFILE.md` §4). Ikkala sozlama **mustaqil**.
     */
    val phoneVisibility: String? = null,
    /**
     * Tarjimayi hol — 140 belgi. Havola, `@handle` va 7+ raqamli ketma-ketlik serverda
     * rad etiladi (`422 BIO_NOT_ALLOWED`), shuning uchun klientda ham oldindan tekshiriladi
     * ([bioRejectionReason]).
     */
    val bio: String? = null,
    /**
     * Profil rasmi — endi **hosila maydon**: har doim `photos[0].url` ga teng
     * (`handoff/08-PROFILE.md` §1). Rasm qo'shilganda, asosiy qilinganda yoki o'chirilganda
     * server ikkalasini bitta tranzaksiyada yangilaydi, ya'ni u hech qachon eskirmaydi.
     */
    val avatarUrl: String? = null,
    // Biznes egasi (rol == "BUSINESS") — universitet/kurs o'rniga shu maydonlar to'ldiriladi.
    val businessName: String? = null,
    val businessType: String? = null,
    /** Aloqa emaili (gmail) — profilda tahrirlanadi. */
    val email: String? = null,
) {
    /** Ism + familiya (bo'sh bo'lsa `null`) — sarlavhalarda ko'rsatish uchun. */
    val displayName: String?
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }

    /** Profil to'ldirilgan hisoblanadimi (kamida ism yoki universitet bor). */
    val isComplete: Boolean
        get() = !firstName.isNullOrBlank() || !universityId.isNullOrBlank()

    companion object {
        /** Tarjimayi hol chegarasi — serverdagi bilan bir xil. */
        const val MAX_BIO = 140
    }
}

/**
 * Bitta profil rasmi (`handoff/08-PROFILE.md` §2).
 *
 * [url] **token bilan** so'raladi; ilovaning rasm klienti `Authorization` sarlavhasini
 * o'z xostimizga o'zi qo'yadi (`createImageHttpClient`).
 */
data class ProfilePhoto(
    val id: String,
    val url: String,
    val thumbUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    /** To'rda ko'rsatiladigan havola — kichik nusxa bo'lsa o'sha. */
    val previewUrl: String get() = thumbUrl?.takeIf { it.isNotBlank() } ?: url

    companion object {
        /** Server chegarasi — oshsa `422 PHOTO_LIMIT_REACHED`. */
        const val MAX_PHOTOS = 6
    }
}

/**
 * Tarjimayi holni **yuborishdan oldin** tekshiradi. `null` — yaroqli.
 *
 * Nega klientda ham: server baribir `422 BIO_NOT_ALLOWED` beradi, lekin foydalanuvchi buni
 * faqat «Saqlash» bosgandan keyin ko'rardi. Hujjat ham shuni tavsiya qiladi — "yozayotganda
 * ogohlantiring, saqlashda kutmang" (`handoff/08-PROFILE.md` §5).
 *
 * Qoidalar serverdagi bilan bir xil: havola, `t.me/…`, `@kanal`, yalang'och domen va
 * **7+ raqam** (ajratgichlar hisobga olinmaydi, ya'ni `+998 90 123 45 67` ham rad etiladi).
 */
fun bioRejectionReason(raw: String): String? {
    val bio = raw.trim()
    if (bio.isEmpty()) return null
    if (bio.length > UserProfile.MAX_BIO) {
        return "Tarjimayi hol ${UserProfile.MAX_BIO} belgidan uzun bo'lmasin."
    }

    val lower = bio.lowercase()
    val hasLink = lower.contains("http://") || lower.contains("https://") ||
        lower.contains("t.me/") || lower.contains("@") ||
        // Yalang'och domen: `arzonkiyim.uz` — nuqta va tanish zona.
        BARE_DOMAIN.containsMatchIn(lower)
    if (hasLink) {
        return "Tarjimayi holda havola yoki foydalanuvchi nomi bo'lishi mumkin emas."
    }

    // Ajratgichlar tashlanadi — `+998 90 123 45 67` 12 xonali ketma-ketlikka aylanadi.
    if (bio.count { it.isDigit() } >= MIN_PHONE_DIGITS) {
        return "Tarjimayi holda telefon raqami bo'lishi mumkin emas."
    }
    return null
}

private val BARE_DOMAIN = Regex("[a-z0-9-]+\\.(uz|com|ru|net|org|io|me|co)\\b")

/** Serverdagi qoida: 7 va undan ortiq raqam — telefon deb hisoblanadi. */
private const val MIN_PHONE_DIGITS = 7
