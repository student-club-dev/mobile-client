package dev.core.domain.repository

import dev.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Ilova sozlamalari — foydalanuvchi sessiyasidan mustaqil, local DB'da (`AppSettingEntity`) saqlanadi.
 * Reaktiv: qiymat o'zgarganda kuzatuvchilar avtomatik yangilanadi.
 */
interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** Boolean bayroq (masalan bildirishnoma sozlamalari). */
    fun observeFlag(key: String, default: Boolean): Flow<Boolean>
    suspend fun setFlag(key: String, value: Boolean)

    companion object {
        const val KEY_NOTIF_PUSH = "notif_push"
        const val KEY_NOTIF_EMAIL = "notif_email"
    }
}
