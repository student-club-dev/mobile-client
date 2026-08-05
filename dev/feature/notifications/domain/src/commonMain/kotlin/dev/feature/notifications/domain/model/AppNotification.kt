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
     * Hech qayerga olib bormaydi (masalan "Xush kelibsiz").
     *
     * Bunday bildirishnoma ham BOSILADI — faqat o'qilgan bo'ladi, ekran almashmaydi.
     */
    data object None : NotificationTarget
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
