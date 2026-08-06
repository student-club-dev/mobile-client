package dev.feature.listings.data.remote

import dev.core.common.Resource
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.GeoApi
import dev.core.network.generated.model.GeocodeRequestDto
import dev.core.network.generated.model.GeocodeResultDto
import dev.core.network.generated.model.ReverseGeocodeRequestDto
import dev.core.network.generated.model.ReverseGeocodeResponseDto
import dev.core.network.response.safeCall
import dev.feature.listings.domain.repository.GeoRepository
import dev.feature.listings.domain.repository.PlaceSuggestion
import dev.feature.listings.domain.repository.ResolvedAddress

/**
 * Geokodlash — **o'z backendimiz** (`POST /v1/geo/geocode`, `POST /v1/geo/reverse-geocode`).
 *
 * Ilgari bu ish to'g'ridan-to'g'ri **OpenStreetMap Nominatim** ga borardi. Uch sabab bilan
 * o'zgartirildi:
 *
 * 1. **Aniqlik.** Backend Yandex Geocoder'ni proksi qiladi va u O'zbekiston manzillarida
 *    sezilarli aniqroq.
 * 2. **`regionId` / `districtId` tayyor keladi.** Nominatim faqat matn berardi va ilova uni
 *    `GeoCatalog` nomlariga **taxminan** moslashtirardi ("Toshkent shahri" ≈ "Tashkent") —
 *    bu jimgina noto'g'ri viloyat tanlanishiga olib kelardi. Endi id serverdan keladi.
 * 3. **Foydalanish shartlari.** Nominatim soniyasiga 1 so'rov so'raydi va `User-Agent` talab
 *    qiladi; ilova o'sib borsa bu chegara buzilishi muqarrar edi.
 *
 * ⚠️ **Zaxira ataylab saqlangan.** Ikkala endpoint ham `503` qaytarishi mumkin — bu
 * "provayder kaliti bu deploymentda sozlanmagan" degani (spec javoblarida bor). O'shanda
 * manzil tanlash butunlay ishlamay qolmasligi uchun [fallback] ga (Nominatim) tushamiz.
 * Backend to'g'ri sozlangan bo'lsa zaxiraga **hech qachon** so'rov ketmaydi.
 */
class ApiGeoRepository(
    private val geo: GeoApi,
    private val connectivity: NetworkConnectivity,
    /** Faqat backend javob bermaganda ishlatiladi — qarang klass izohi. */
    private val fallback: GeoRepository,
) : GeoRepository {

    override suspend fun reverseGeocode(lat: Double, lng: Double): Resource<ResolvedAddress> {
        val result = safeCall(connectivity) {
            geo.reverseGeocode(ReverseGeocodeRequestDto(lat = lat, lng = lng)).body()
        }
        return when (result) {
            is Resource.Success -> Resource.Success(result.data.toResolved())
            else -> fallback.reverseGeocode(lat, lng)
        }
    }

    override suspend fun search(query: String): Resource<List<PlaceSuggestion>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Resource.Success(emptyList())

        val result = safeCall(connectivity) {
            // `regionId` yuborilmaydi: qidiruv butun mamlakat bo'yicha ketsin — foydalanuvchi
            // e'lon joyini tanlayotganda viloyat hali belgilanmagan bo'lishi mumkin.
            geo.geocode(GeocodeRequestDto(query = trimmed)).body()
        }
        return when (result) {
            is Resource.Success -> Resource.Success(result.data.map { it.toSuggestion() })
            else -> fallback.search(trimmed)
        }
    }
}

/**
 * `nearestMetro` — server tomondan **haqiqiy** qiymat (`metro_stations` seed qilingandan
 * keyin, `DISCOUNTS_BUSINESS_API_RESPONSE.md` §2.3). Uzoqdagi bekatni server o'zi kesib
 * tashlaydi (3 km), shuning uchun bu yerda qo'shimcha filtr kerak emas — kelgan qiymat
 * mo'ljal sifatida ishlatishga tayyor.
 */
internal fun ReverseGeocodeResponseDto.toResolved(): ResolvedAddress = ResolvedAddress(
    address = address.orEmpty(),
    regionId = regionId,
    districtId = districtId,
    nearestMetro = nearestMetro?.takeIf { it.isNotBlank() },
)

/**
 * Server bitta satr beradi ("Amir Temur ko'chasi, 12, Yunusobod, Toshkent"), ro'yxatda esa
 * ikki qator kerak — birinchi bo'lak sarlavha, qolgani izoh. Nominatim yo'lida ham aynan
 * shunday bo'lingan, ya'ni ro'yxat ko'rinishi o'zgarmaydi.
 */
internal fun GeocodeResultDto.toSuggestion(): PlaceSuggestion = PlaceSuggestion(
    title = formattedAddress.substringBefore(",").trim().ifBlank { formattedAddress },
    subtitle = formattedAddress.substringAfter(",", "").trim(),
    lat = lat,
    lng = lng,
)
