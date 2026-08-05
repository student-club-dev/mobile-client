package dev.core.data.seed

import dev.core.common.AppDispatchers
import dev.core.database.sql.StudentClubDatabase
import kotlinx.coroutines.withContext

/**
 * Eski o'rnatmalarda qolgan namuna (fake) ma'lumotni bir marta o'chiradi.
 *
 * Seed'ning o'zi [LocalDataSeeder] dan olib tashlandi, lekin u ILGARI yozilgan qatorlar
 * telefondagi bazada qolib ketadi va ekranda haqiqiy ma'lumot bilan aralashib ko'rinardi
 * (masalan "Dilnoza Rahimova" yoki "rent-chilonzor"). Bu qadam ularni tozalaydi.
 *
 * Faqat backendga ulangan bo'limlar tozalanadi — endpoint'i yo'qlari (ishlar, klublar,
 * eski `ad` jadvali) o'z joyida qoladi. Bildirishnomalar bu ro'yxatda YO'Q va kerak
 * emas: ularning jadvali `30.sqm` migratsiyasida butunlay almashtiriladi.
 *
 * Bir martalik: bajarilgani [PURGE_KEY] orqali `AppSetting` da belgilanadi. Keyinchalik
 * yana bir bo'lim backendga ko'chsa — [PURGE_VERSION] ni oshiring.
 */
class SeedPurge(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
) {
    suspend fun purgeOnce() = withContext(dispatchers.io) {
        val settings = db.appSettingQueries
        if (settings.selectByKey(PURGE_KEY).executeAsOneOrNull() == PURGE_VERSION) return@withContext

        // Universitetlar — prof-emis'dan (`ensureRemoteUniversities`) qayta to'ldiriladi.
        // Manba belgisi ham o'chiriladi, aks holda u "allaqachon yuklangan" deb turadi va
        // jadval bo'sh qolib ketardi (hech qayerda universitet tanlab bo'lmasdi).
        db.universityQueries.clear()
        settings.deleteByKey(UNIVERSITY_SOURCE_KEY)
        // Talabalar — endi `GET /v1/students` (feature:connections) beradi.
        db.studentQueries.clear()
        // "Siz uchun" feed'i — `POST /v1/catalog/*` + `POST /v1/discounts/search`.
        db.discountQueries.transaction {
            db.discountQueries.clearOffers()
            db.discountQueries.clearCategories()
            db.discountQueries.clearGroups()
        }
        // Namuna ijara/topshiriq e'lonlari — `/v1/student-listings*`. Faqat seed egasi
        // o'chiriladi, foydalanuvchining o'z e'lonlari (uid egasi) tegilmaydi.
        db.listingQueries.deleteByOwner(SEED_OWNER)
        // Eski "Siz uchun" seed versiyasi belgisi ham kerak emas.
        settings.deleteByKey(LEGACY_DISCOUNTS_SEED_KEY)

        settings.upsert(PURGE_KEY, PURGE_VERSION)
    }

    private companion object {
        const val PURGE_KEY = "seed_purge_version"

        /** v1 — universitet, talaba, "Siz uchun" feed'i va namuna e'lonlar olib tashlandi. */
        const val PURGE_VERSION = "1"

        /** Namuna e'lonlarning egasi (eski [LocalDataSeeder] dagi qiymat). */
        const val SEED_OWNER = "seed-user"

        /** Eski `seedDiscounts()` ishlatgan kalit. */
        const val LEGACY_DISCOUNTS_SEED_KEY = "discounts_seed_version"

        /** `UniversityRepositoryImpl.UNI_SOURCE_KEY` bilan bir xil bo'lishi shart. */
        const val UNIVERSITY_SOURCE_KEY = "universities_source"
    }
}
