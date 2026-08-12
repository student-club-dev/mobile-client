package dev.feature.listings.data.remote

import dev.core.common.Resource
import dev.feature.listings.domain.model.District
import dev.feature.listings.domain.model.GeoCatalog
import dev.feature.listings.domain.model.Region
import dev.feature.listings.domain.repository.GeoCatalogRepository
import dev.feature.listings.domain.repository.GeoRepository
import dev.feature.listings.domain.repository.PlaceSuggestion
import dev.feature.listings.domain.repository.ResolvedAddress
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dev.core.common.locale.AppLocale

/**
 * **Zaxira** geokoder — OpenStreetMap Nominatim (tekin, API kalit talab qilmaydi).
 *
 * ⚠️ Bu **asosiy yo'l emas**: manzil endi o'z backendimizdan olinadi
 * ([ApiGeoRepository]) — u Yandex Geocoder'ni proksi qiladi, O'zbekiston manzillarida
 * aniqroq va `regionId`/`districtId` ni tayyor beradi. Bu klass faqat backend `503`
 * qaytarganda (provayder kaliti sozlanmagan deployment) ishga tushadi.
 *
 * Nominatim'ning foydalanish qoidasi `User-Agent` ni talab qiladi va soniyasiga 1 so'rovdan
 * ko'p yubormaslikni so'raydi — zaxira sifatida bu chegara amalda hech qachon urilmaydi.
 *
 * MUHIM: bu klient ilovaning umumiy Ktor klienti EMAS — u har so'rovga Firebase Bearer
 * tokenini qo'shadi va bazaviy manzili `api.studentclub.uz`. Nominatim'ga o'z klienti kerak.
 *
 * [catalog] — viloyat/tuman/metro ma'lumotnomasi. Nominatim faqat MATN beradi, id bermaydi;
 * ilgari u ilovadagi statik ro'yxatga solishtirilardi, endi esa **serverdagi** ro'yxatga
 * (keshdan o'qiladi, ya'ni bu yo'l ham oflaynda ishlaydi). Metro bekati ham shu ro'yxatdan
 * hisoblanadi — shunda zaxira yo'l `nearestMetro` ni asosiy yo'l bilan bir xil beradi.
 */
class NominatimGeoRepository(
    private val httpClient: HttpClient,
    private val catalog: GeoCatalogRepository,
) : GeoRepository {

    override suspend fun search(query: String): Resource<List<PlaceSuggestion>> = try {
        val results: List<NominatimSearchResult> = httpClient.get("$BASE_URL/search") {
            parameter("q", query)
            parameter("format", "jsonv2")
            // Faqat O'zbekiston — talaba chegirmasi boshqa davlatda bo'lmaydi va
            // qidiruv ancha aniqroq ishlaydi.
            parameter("countrycodes", "uz")
            parameter("addressdetails", 1)
            parameter("limit", 8)
            parameter("accept-language", "uz")
            header("User-Agent", USER_AGENT)
        }.body()

        Resource.Success(results.mapNotNull { it.toSuggestion() })
    } catch (e: Exception) {
        Resource.Error(e.message ?: AppLocale.pick(en = "Search failed", ru = "Не удалось выполнить поиск", uz = "Qidirib bo'lmadi"), e)
    }

    override suspend fun reverseGeocode(lat: Double, lng: Double): Resource<ResolvedAddress> = try {
        val response: NominatimResponse = httpClient.get("$BASE_URL/reverse") {
            parameter("lat", lat)
            parameter("lon", lng)
            parameter("format", "jsonv2")
            parameter("zoom", 18) // ko'cha/uy darajasi
            parameter("accept-language", "uz")
            header("User-Agent", USER_AGENT)
        }.body()

        val regions = catalog.regions()
        val regionId = matchRegion(response.address, regions)
        val districts = regionId?.let { catalog.districts(it) }.orEmpty()
        val metro = GeoCatalog.nearestStation(catalog.metroStations(), lat, lng)

        Resource.Success(
            response.toResolved(
                regionId = regionId,
                districtId = matchDistrict(response.address, districts),
                nearestMetro = metro?.name,
            ),
        )
    } catch (e: Exception) {
        Resource.Error(e.message ?: AppLocale.pick(en = "Couldn't determine the address", ru = "Не удалось определить адрес", uz = "Manzilni aniqlab bo'lmadi"), e)
    }

    private companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org"
        const val USER_AGENT = "StudentClub/1.0 (https://studentclub.uz)"
    }
}

@Serializable
internal data class NominatimResponse(
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddress? = null,
)

@Serializable
private data class NominatimSearchResult(
    val lat: String? = null,
    val lon: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddress? = null,
)

private fun NominatimSearchResult.toSuggestion(): PlaceSuggestion? {
    val latitude = lat?.toDoubleOrNull() ?: return null
    val longitude = lon?.toDoubleOrNull() ?: return null
    val full = displayName.orEmpty()

    // `name` — joyning o'z nomi ("Mega Planet"). Bo'sh bo'lsa (oddiy manzil) to'liq
    // nomning birinchi qismini olamiz: "Amir Temur ko'chasi, 12, Yunusobod, ..."
    val title = name?.takeIf { it.isNotBlank() }
        ?: full.substringBefore(",").ifBlank { return null }

    val subtitle = full.removePrefix(title).trimStart(',', ' ')

    return PlaceSuggestion(title = title, subtitle = subtitle, lat = latitude, lng = longitude)
}

@Serializable
internal data class NominatimAddress(
    val road: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val neighbourhood: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val town: String? = null,
    val county: String? = null,
    val state: String? = null,
)

internal fun NominatimResponse.toResolved(
    regionId: String?,
    districtId: String?,
    nearestMetro: String?,
): ResolvedAddress {
    val a = address

    // Talabaga foydali qism: ko'cha + uy raqami. Nominatim uni bermasa — to'liq nom.
    val street = listOfNotNull(a?.road, a?.houseNumber).joinToString(" ").ifBlank { null }
    val area = a?.neighbourhood ?: a?.suburb
    val short = listOfNotNull(street, area).joinToString(", ")

    return ResolvedAddress(
        address = short.ifBlank { displayName.orEmpty() },
        regionId = regionId,
        districtId = districtId,
        nearestMetro = nearestMetro,
    )
}

/**
 * Nominatim viloyat nomini ("Toshkent shahri", "Tashkent") ma'lumotnomadagi id'ga bog'laydi.
 * Topilmasa `null` — bu xato emas: koordinata baribir bor, viloyat faqat filtr uchun.
 */
internal fun matchRegion(address: NominatimAddress?, regions: List<Region>): String? {
    val candidates = listOfNotNull(address?.state, address?.city)
    return regions.firstOrNull { region ->
        candidates.any { it.normalize().contains(region.name.take(6).normalize()) }
    }?.id
}

internal fun matchDistrict(address: NominatimAddress?, districts: List<District>): String? {
    val candidates = listOfNotNull(address?.county, address?.suburb, address?.town, address?.city)
    return districts.firstOrNull { district ->
        candidates.any { it.normalize().contains(district.name.take(5).normalize()) }
    }?.id
}

/** Taqqoslash uchun: kichik harf, apostroflarsiz ("Qo'qon" ≈ "Qoqon" ≈ "qo`qon"). */
private fun String.normalize(): String =
    lowercase().filter { it.isLetterOrDigit() }
