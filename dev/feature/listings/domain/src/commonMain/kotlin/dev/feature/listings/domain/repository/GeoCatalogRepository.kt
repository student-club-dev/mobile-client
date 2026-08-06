package dev.feature.listings.domain.repository

import dev.feature.listings.domain.model.District
import dev.feature.listings.domain.model.MetroStation
import dev.feature.listings.domain.model.Region

/**
 * Geo **ma'lumotnomasi** — viloyat / tuman / metro bekati.
 *
 * Manba: `GET /v1/geo/regions`, `GET /v1/geo/regions/{regionId}/districts`,
 * `GET /v1/geo/metro-stations` (kontrakt yo'llari — `DISCOUNTS_BUSINESS_API_RESPONSE.md`
 * §5.3). Eski `/v1/regions` va `/v1/districts` admin panelniki, ilova ularni chaqirmaydi.
 *
 * ⚠️ Metodlar `Resource` emas, **oddiy ro'yxat** qaytaradi va hech qachon yiqilmaydi:
 * tarmoq bo'lmasa kesh, kesh ham bo'lmasa
 * [dev.feature.listings.domain.model.GeoCatalog] dagi statik ro'yxat ishlatiladi.
 * Chaqiruvchilar (manzil tanlash, teskari geokodlash zaxirasi) uchun "viloyatlar
 * ro'yxati yo'q" degan holat ma'noga ega emas — u faqat qo'shimcha shovqin bo'lardi.
 *
 * Metro bekatlari uchun zaxira YO'Q (bo'sh ro'yxat): ular ilovada hardcode qilinmagan va
 * qilinmasligi ham kerak — §5.1 qoidasi, katalog serverniki.
 */
interface GeoCatalogRepository {

    suspend fun regions(): List<Region>

    suspend fun districts(regionId: String): List<District>

    suspend fun metroStations(): List<MetroStation>
}
