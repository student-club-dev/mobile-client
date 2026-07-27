package dev.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.common.map
import dev.core.database.sql.StudentClubDatabase
import dev.core.domain.model.Region
import dev.core.domain.repository.RegionRepository
import dev.core.network.generated.api.GeoApi
import dev.core.network.response.safeCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [RegionRepository] — viloyatlar backend'dan (`GET /v1/geo/regions`), tanlov esa local
 * `AppSettingEntity` da (id va nomi alohida kalitlarda).
 *
 * Nomi ham saqlanadi: sozlamalar ekrani tanlangan viloyatni tarmoqqa bormasdan, oflaynda
 * ham ko'rsata olishi kerak.
 */
class RegionRepositoryImpl(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
    private val geo: GeoApi,
) : RegionRepository {

    private val q get() = db.appSettingQueries

    override suspend fun regions(): Resource<List<Region>> =
        safeCall { geo.getRegions().body() }
            .map { list -> list.map { Region(id = it.id, name = it.nameUz) } }

    override fun observeSelected(): Flow<Region?> =
        q.selectByKey(KEY_REGION_ID)
            .asFlow()
            .mapToOneOrNull(dispatchers.io)
            .map { id -> id?.let { Region(id = it, name = read(KEY_REGION_NAME).orEmpty()) } }

    override fun selectedId(): String? = read(KEY_REGION_ID)

    override suspend fun select(region: Region?) = withContext(dispatchers.io) {
        q.transaction {
            if (region == null) {
                q.deleteByKey(KEY_REGION_ID)
                q.deleteByKey(KEY_REGION_NAME)
            } else {
                q.upsert(KEY_REGION_ID, region.id)
                q.upsert(KEY_REGION_NAME, region.name)
            }
        }
    }

    /**
     * [address] — universitetning TO'LIQ MANZILI (prof-emis `address`), masalan
     * "Toshkent shahri, Yunusobod tumani, Qorasaroy ko'chasi 1". Shuning uchun tenglik emas,
     * manzil ichidan viloyat nomini qidiramiz.
     *
     * Tartib muhim: avval TO'LIQ nom bo'yicha ("toshkent shahri"), keyingina qisqartirilgan
     * ko'rinish bo'yicha. Aks holda "Toshkent shahri" va "Toshkent viloyati" ikkalasi ham
     * "toshkent" ga aylanib, poytaxtdagi universitetga viloyat tayinlanib qolishi mumkin edi.
     */
    override suspend fun syncWithUniversity(universityId: String?, address: String?) {
        if (universityId == null) return
        // Universitet o'zgarmagan - qo'lda tanlangan viloyatni buzmaymiz.
        if (read(KEY_REGION_UNI) == universityId) return
        if (address.isNullOrBlank()) return

        val regions = (regions() as? Resource.Success)?.data ?: return   // tarmoq yo'q - keyin

        val simple = address.simplified()
        val match = regions.firstOrNull { simple.contains(it.name.simplified()) }
            ?: regions.firstOrNull { it.name.stripped() == simple.stripped() }
            ?: regions.firstOrNull { simple.stripped().contains(it.name.stripped()) }

        // Universitet baribir belgilanadi: manzilda viloyat topilmasa ham har ochilishda
        // qayta so'rov yubormaymiz. Foydalanuvchi o'zi Filter'dan tanlaydi.
        if (match != null) select(match)
        withContext(dispatchers.io) { q.upsert(KEY_REGION_UNI, universityId) }
    }

    private fun read(key: String): String? = q.selectByKey(key).executeAsOneOrNull()

    /** Kichik harf + apostrof/tire birxillashtirilgan; "viloyati/shahri" SAQLANADI. */
    private fun String.simplified(): String = lowercase()
        .replace('‘', '\'')
        .replace('’', '\'')
        .replace('`', '\'')
        .replace("-", " ")
        .trim()

    /** Qo'shimcha zaxira: "viloyati/shahri/respublikasi" ham olib tashlangan ko'rinish. */
    private fun String.stripped(): String = simplified()
        .replace("viloyati", "")
        .replace("shahri", "")
        .replace("respublikasi", "")
        .trim()

    private companion object {
        const val KEY_REGION_ID = "feed_region_id"
        const val KEY_REGION_NAME = "feed_region_name"
        /** Viloyat qaysi universitet bo'yicha hisoblangani - qayta hisoblash sharti. */
        const val KEY_REGION_UNI = "feed_region_university"
    }
}
