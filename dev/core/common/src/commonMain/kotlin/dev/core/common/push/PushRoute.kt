package dev.core.common.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bosilgan push'ning `data` konverti — **platforma bilan UI o'rtasidagi ko'prik**.
 *
 * Server yuboradigan konvert (`02-PUSH_CATALOG_BACKEND.md` §2) — hamma qiymat `string`,
 * qiymati yo'q kalit umuman yuborilmaydi:
 * ```jsonc
 * { "kind": "CHAT", "notificationId": "clx…", "targetType": "CHAT",
 *   "targetId": "cnv_01H…", "conversationId": "cnv_01H…" }
 * ```
 *
 * Oqim: bildirishnoma bosildi → platforma qatlami (Android'da `MainActivity`, iOS'da
 * `UNUserNotificationCenter` delegate'i) konvertni [set] qiladi → talaba karkasi
 * (`StudentShell`) uni kuzatib turadi, o'qilgan deb belgilaydi va kerakli ekranni ochib
 * [consume] qiladi.
 *
 * Nega global obyekt: bildirishnoma **ilova ishga tushishidan oldin** bosilishi mumkin, ya'ni
 * hali birorta ekran ham, ViewModel ham mavjud emas. Qiymat shu yerda kutib turadi va UI
 * tayyor bo'lgach o'qiladi.
 *
 * Nega XOM satrlar, `NotificationTarget` emas: bu modul (`:dev:core:common`) feature
 * qatlamini ko'rmaydi. Ma'noga o'girish `StudentShell` da — ro'yxatdagi qator bosilganda
 * ishlaydigan O'SHA mantiq bilan bo'ladi. Ikkisi ajralib qolsa bitta bildirishnoma push'dan
 * va ro'yxatdan har xil ekranga olib borardi.
 */
object PushRoute {

    /**
     * Bosilgan bildirishnomaning yo'nalishi.
     *
     * [notificationId] — ro'yxatdagi qator id'si (§2.1). Push bosilganda o'sha qator
     * o'qilgan deb belgilanadi; aks holda foydalanuvchi bildirishnomani ko'rgan bo'lsa ham
     * `unreadCount` uni sanashda davom etardi.
     *
     * [conversationId] — chat va qo'ng'iroq push'larida ataylab saqlangan eski kalit
     * (§2). U [targetType]/[targetId] bilan bir xil ma'noni beradi va faqat zaxira
     * sifatida o'qiladi.
     */
    data class Payload(
        val notificationId: String? = null,
        val targetType: String? = null,
        val targetId: String? = null,
        val conversationId: String? = null,
    ) {
        /** Ochiladigan joy ham, belgilanadigan qator ham bo'lmasa — konvert bekor. */
        val isEmpty: Boolean
            get() = notificationId.isNullOrBlank() &&
                targetType.isNullOrBlank() &&
                conversationId.isNullOrBlank()
    }

    private val _pending = MutableStateFlow<Payload?>(null)

    /** Kutayotgan konvert yoki `null`. */
    val pending: StateFlow<Payload?> = _pending.asStateFlow()

    /** Platforma qatlami chaqiradi (intent extra'lari / `userInfo`). */
    fun set(payload: Payload) {
        if (!payload.isEmpty) _pending.value = payload
    }

    /** Faqat suhbat id'si ma'lum bo'lgan holat (eski chat push'i). */
    fun set(conversationId: String?) {
        set(Payload(conversationId = conversationId))
    }

    /** UI yo'nalishni bajargach chaqiradi — bir marta ishlashi uchun. */
    fun consume() {
        _pending.value = null
    }
}
