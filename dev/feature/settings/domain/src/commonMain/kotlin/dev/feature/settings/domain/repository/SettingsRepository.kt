package dev.feature.settings.domain.repository

import dev.core.common.locale.AppLanguage
import dev.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Ilova sozlamalari — foydalanuvchi sessiyasidan mustaqil, local DB'da (`AppSettingEntity`) saqlanadi.
 */
interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Interfeys tili. Hech narsa saqlanmagan bo'lsa — [AppLanguage.Default] (= EN), ya'ni
     * ilova birinchi ishga tushganda ingliz tilida ochiladi. Qurilma tiliga ERGASHMAYDI:
     * til faqat Sozlamalardagi aniq tanlov bilan o'zgaradi.
     */
    fun observeLanguage(): Flow<AppLanguage>
    suspend fun setLanguage(language: AppLanguage)
    fun observeFlag(key: String, default: Boolean): Flow<Boolean>
    suspend fun setFlag(key: String, value: Boolean)
    fun observeValue(key: String): Flow<String?>
    suspend fun setValue(key: String, value: String?)

    companion object {
        const val KEY_NOTIF_PUSH = "notif_push"
        const val KEY_NOTIF_EMAIL = "notif_email"

        /**
         * Tanishtiruv (onboarding) ekranlari bir marta ko'rilganmi. Chiqishda TOZALANMAYDI —
         * logout'dan keyin foydalanuvchi tanishtiruvga emas, to'g'ridan-to'g'ri kirish ekraniga
         * tushishi kerak.
         */
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }
}
