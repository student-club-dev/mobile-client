package dev.feature.notifications.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /v1/notifications` javobi (`NOTIFICATIONS_BACKEND.md` §3.1).
 *
 * Ro'yxat endi kelayotgan spec'da (`student-club.json`) YO'Q, shuning uchun DTO'lar qo'lda
 * yozilgan — generatsiya qilingan klient faqat `POST /v1/devices` ni biladi. Endpoint
 * spec'ga qo'shilgan kuni bu fayl `NotificationsApi` bilan almashtiriladi; qolgan qatlamlar
 * (repository, mapper) o'zgarmaydi.
 */
@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto> = emptyList(),
    /** O'qilmaganlar soni — ro'yxatning ko'rinayotgan qismidan EMAS, butun hisobdan. */
    val unreadCount: Int = 0,
)

@Serializable
data class NotificationDto(
    val id: String,
    val title: String,
    val body: String = "",
    /**
     * `JOB | DISCOUNT | LISTING | CHAT | CONNECTION | SYSTEM`.
     *
     * `String`, enum EMAS — kotlinx noma'lum enum qiymatida BUTUN javobni yiqitadi va
     * server bu ro'yxatga yangi tur qo'shishi kutiladi (`lenientEnums` bilan bir xil sabab).
     */
    val type: String = "",
    /** ISO-8601. */
    val createdAt: String? = null,
    @SerialName("readAt")
    val readAt: String? = null,
    val read: Boolean? = null,
    val target: NotificationTargetDto? = null,
)

/**
 * Bosilganda ochiladigan ekran — tayyor deeplink emas, ma'no.
 *
 * `type`: `CHAT | LISTING | CONNECTION_REQUESTS | MY_LISTINGS | PROFILE`. Noma'lum qiymat
 * "hech qayerga" degani (bildirishnoma baribir o'qilgan bo'ladi).
 */
@Serializable
data class NotificationTargetDto(
    val type: String = "",
    val id: String? = null,
)

/** `POST /v1/notifications/read` tanasi — bitta yoki bir nechta id. */
@Serializable
data class MarkNotificationsReadDto(
    val ids: List<String>? = null,
    /** `true` — hammasi. `ids` bilan birga yuborilmaydi. */
    val all: Boolean? = null,
)
