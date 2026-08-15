package dev.feature.notifications.domain.model

import kotlinx.datetime.Instant

/**
 * Bildirishnoma turi — ikonka va rangni belgilaydi.
 *
 * Server noma'lum tur yuborsa [SYSTEM] ga tushadi (qarang `NotificationMappers`), ya'ni
 * yangi tur qo'shilishi eski klientda bitta bildirishnomani "umumiy" qiladi, ro'yxatni
 * yiqitmaydi.
 */
enum class NotificationType { JOB, DISCOUNT, LISTING, CHAT, CONNECTION, SYSTEM }

/**
 * Bildirishnoma bosilganda ochiladigan ekran.
 *
 * Domen qatlamida ATAYLAB route emas, MA'NO turadi: `chat?conversationId=…` kabi yo'llar
 * navigatsiya grafiga tegishli va ular faqat [dev.feature.auth.presentation.main.StudentShell]
 * da yig'iladi. Shu sababdan server ham tayyor deeplink emas, `targetType` + `targetId`
 * juftligini yuboradi — mobil navigatsiyani o'zgartirish backendni qo'zg'atmaydi.
 */
sealed interface NotificationTarget {
    /** Suhbat — [conversationId] server bergan id. */
    data class Chat(val conversationId: String) : NotificationTarget

    /** E'lon/chegirma tafsiloti. */
    data class Listing(val listingId: String) : NotificationTarget

    /** "Do'stlar" ekrani, so'rovlar bo'limi ochilgan holda. */
    data object ConnectionRequests : NotificationTarget

    /** "Mening e'lonlarim" — e'lon holati o'zgarganda (moderatsiya, muddat). */
    data object MyListings : NotificationTarget

    /** Profil — profilga oid tizim bildirishnomalari. */
    data object Profile : NotificationTarget

    /**
     * Suhbatlar RO'YXATI — `CHAT` turidagi bildirishnomada `targetId` bo'lmaganda.
     *
     * Server xabar bildirishnomasini ba'zan `targetId` siz yuboradi va o'shanda [Chat] ni
     * qurib bo'lmaydi. Ilgari bunday qator [None] ga tushardi, ya'ni bosilganda **hech
     * nima bo'lmasdi** — foydalanuvchi nuqtai nazaridan bu shunchaki "ishlamaydigan"
     * bildirishnoma edi. Suhbatlar ro'yxatini ochish esa doim to'g'ri: yangi xabar aynan
     * o'sha yerda, ro'yxatning tepasida turadi.
     */
    data object Conversations : NotificationTarget

    /**
     * Hech qayerga olib bormaydi (masalan "Xush kelibsiz").
     *
     * Bunday bildirishnoma ham BOSILADI — faqat o'qilgan bo'ladi, ekran almashmaydi.
     */
    data object None : NotificationTarget

    companion object {
        /**
         * Server bergan `targetType` + `targetId` juftligi → ma'no.
         *
         * Ro'yxat javobi (`target`) va push konverti (`data.targetType`/`data.targetId`)
         * BIR XIL qiymatlarni yuboradi, shuning uchun o'girish ham bitta joyda: aks holda
         * bitta bildirishnoma push'dan bosilganda bir ekranga, ro'yxatdan bosilganda
         * boshqasiga olib borishi mumkin edi.
         *
         * Noma'lum tur ham, id kutgani holda id'siz kelgan tur ham [None] ga tushadi:
         * bosilganda hech qayerga o'tmaydi, faqat o'qilgan bo'ladi. Bu ataylab — "id'siz
         * suhbat" ni ochishga urinish bo'sh ekranga olib borardi.
         */
        fun of(type: String?, id: String?): NotificationTarget = when (type) {
            // Id bo'lmasa ham bosish ISHLAYDI — suhbatlar ro'yxati ochiladi (qarang
            // [Conversations]).
            "CHAT", "MESSAGE" -> id?.takeIf { it.isNotBlank() }?.let(::Chat) ?: Conversations
            "LISTING" -> id?.takeIf { it.isNotBlank() }?.let(::Listing) ?: None
            "CONNECTION_REQUESTS" -> ConnectionRequests
            "MY_LISTINGS" -> MyListings
            "PROFILE" -> Profile
            else -> None
        }
    }
}

/** Foydalanuvchi bildirishnomasi. */
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    /** Yaratilgan vaqt. "2 soat oldin" yorlig'i UI qatlamida shundan hisoblanadi. */
    val createdAt: Instant,
    val target: NotificationTarget,
    val read: Boolean,
)
