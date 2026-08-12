package dev.core.common.locale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ilova interfeysi tili. **Sukut — [EN]**: ilova birinchi marta ochilganda ham, saqlangan
 * qiymat buzuq bo'lganda ham ingliz tili ko'rsatiladi.
 *
 * [code] — BCP-47 tili kodi (saqlash va `Accept-Language` sarlavhasi uchun).
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
    UZ("uz", "O'zbekcha"),
    ;

    companion object {
        /** Ilova ishga tushganda va noma'lum kod uchrasa ishlatiladigan til. */
        val Default: AppLanguage = EN

        /** Saqlangan/serverdan kelgan kodni turga aylantiradi; noma'lum bo'lsa — [Default]. */
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
                ?: Default
    }
}

/**
 * Compose'dan TASHQARIDAGI kod (validator, use-case, mapper, WebSocket qatlami) uchun joriy til.
 *
 * Compose ichida [dev.core.uikit.locale.LocalAppLanguage] ishlatiladi — u shu holatdan
 * mustaqil emas: ikkalasini ham ildiz Composable (`App`) bitta manbadan yangilaydi.
 */
object AppLocale {
    private val _language = MutableStateFlow(AppLanguage.Default)

    /** Joriy til — o'zgarishlarni kuzatish uchun. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Joriy til — bir martalik o'qish (Composable bo'lmagan joylar uchun). */
    val current: AppLanguage get() = _language.value

    fun set(language: AppLanguage) {
        _language.value = language
    }

    /** Uch tarjimadan joriy tilga mosini tanlaydi. */
    fun <T> pick(en: T, ru: T, uz: T): T = when (current) {
        AppLanguage.EN -> en
        AppLanguage.RU -> ru
        AppLanguage.UZ -> uz
    }
}
