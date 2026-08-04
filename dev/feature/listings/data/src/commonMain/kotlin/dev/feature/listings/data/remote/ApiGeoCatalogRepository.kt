package dev.feature.listings.data.remote

import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.database.sql.StudentClubDatabase
import dev.core.network.generated.api.GeoApi
import dev.core.network.response.safeCall
import dev.feature.listings.domain.model.District
import dev.feature.listings.domain.model.GeoCatalog
import dev.feature.listings.domain.model.MetroStation
import dev.feature.listings.domain.model.Region
import dev.feature.listings.domain.repository.GeoCatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Geo ma'lumotnomasining backend manbasi — kontrakt yo'llari ustida
 * (`GET /geo/regions`, `GET /geo/regions/{regionId}/districts`, `GET /geo/metro-stations`).
 *
 * **Uch qatlamli**: jarayon ichidagi xotira → local baza (`AppSettingEntity`, JSON) →
 * tarmoq. Sabab oddiy: ro'yxatlar deyarli o'zgarmaydi, lekin ular manzil tanlash oqimining
 * o'rtasida kerak bo'ladi — o'sha yerda tarmoqni kutish yoki bo'sh ro'yxat ko'rsatish
 * foydalanuvchi uchun to'g'ridan-to'g'ri yo'qotish. Kesh bo'lsa so'rov FONDA ketadi va
 * javob kelganda keyingi chaqiruvga tayyor turadi.
 *
 * Tarmoq ham, kesh ham bo'lmasa viloyat/tuman uchun [GeoCatalog] dagi statik ro'yxatga
 * tushamiz (id'lar bir xil formatda, ya'ni saqlangan e'lon buzilmaydi). Metro uchun
 * zaxira yo'q — u ilovada hardcode qilinmagan.
 */
class ApiGeoCatalogRepository(
    private val geo: GeoApi,
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
) : GeoCatalogRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    private var regionsMemo: List<Region>? = null
    private var metroMemo: List<MetroStation>? = null
    private val districtsMemo = mutableMapOf<String, List<District>>()

    override suspend fun regions(): List<Region> = lock.withLock {
        regionsMemo?.let { return it }
        val fetched = safeCall { geo.getRegions().body() }
            .dataOrNull()
            ?.map { Region(id = it.id, name = it.nameUz, districts = emptyList()) }
            ?.takeIf { it.isNotEmpty() }

        if (fetched != null) {
            cacheNamed(KEY_REGIONS, fetched.map { CachedNamed(it.id, it.name) })
        }
        val known = fetched
            ?: readCache(KEY_REGIONS, CachedNamed.serializer())?.map { Region(it.id, it.name) }
        // Statik zaxira ESLAB QOLINMAYDI: aks holda ilova birinchi so'rovni tarmoqsiz
        // qilgani uchun butun sessiya davomida eski ro'yxatda qolib ketardi.
        known?.also { regionsMemo = it } ?: GeoCatalog.regions()
    }

    override suspend fun districts(regionId: String): List<District> = lock.withLock {
        districtsMemo[regionId]?.let { return it }
        val fetched = safeCall { geo.getDistricts(regionId).body() }
            .dataOrNull()
            ?.map { District(id = it.id, name = it.nameUz) }
            ?.takeIf { it.isNotEmpty() }

        if (fetched != null) {
            cacheNamed(districtsKey(regionId), fetched.map { CachedNamed(it.id, it.name) })
        }
        val known = fetched
            ?: readCache(districtsKey(regionId), CachedNamed.serializer())
                ?.map { District(it.id, it.name) }
        known?.also { districtsMemo[regionId] = it } ?: GeoCatalog.districts(regionId)
    }

    override suspend fun metroStations(): List<MetroStation> = lock.withLock {
        metroMemo?.let { return it }
        val fetched = safeCall { geo.getMetroStations().body() }
            .dataOrNull()
            ?.map { MetroStation(it.id, it.nameUz, it.line, it.lat, it.lng) }
            ?.takeIf { it.isNotEmpty() }

        if (fetched != null) cacheStations(fetched.map(::CachedStation))
        val known = fetched
            ?: readCache(KEY_METRO, CachedStation.serializer())
                ?.map { MetroStation(it.id, it.name, it.line, it.lat, it.lng) }
        // Ro'yxat topilmasa bo'sh qaytadi, lekin ESLAB QOLINMAYDI — "mo'ljal yo'q" va
        // "hali yuklanmadi" bir xil ko'rinmasligi kerak. Bu yo'lga faqat backend
        // geokoderi yiqilganda kelinadi, ya'ni takroriy so'rov qimmat emas.
        known?.also { metroMemo = it } ?: emptyList()
    }

    private suspend fun cacheNamed(key: String, value: List<CachedNamed>) =
        write(key, json.encodeToString(ListSerializer(CachedNamed.serializer()), value))

    private suspend fun cacheStations(value: List<CachedStation>) =
        write(KEY_METRO, json.encodeToString(ListSerializer(CachedStation.serializer()), value))

    private suspend fun write(key: String, text: String) = withContext(dispatchers.io) {
        runCatching { db.appSettingQueries.upsert(key, text) }
        Unit
    }

    private suspend fun <T> readCache(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T>? = withContext(dispatchers.io) {
        val raw = db.appSettingQueries.selectByKey(key).executeAsOneOrNull() ?: return@withContext null
        runCatching { json.decodeFromString(ListSerializer(serializer), raw) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun <T> Resource<T>.dataOrNull(): T? = (this as? Resource.Success)?.data

    /** Kalitda `regionId` bor: har viloyat o'z tumanlari bilan alohida saqlanadi. */
    private fun districtsKey(regionId: String) = "geo_districts_$regionId"

    private companion object {
        const val KEY_REGIONS = "geo_regions"
        const val KEY_METRO = "geo_metro_stations"
    }
}

/** Viloyat ham, tuman ham — `{id, name}`; bitta kesh formati ikkalasiga yetadi. */
@Serializable
private data class CachedNamed(val id: String, val name: String)

@Serializable
private data class CachedStation(
    val id: String,
    val name: String,
    val line: String,
    val lat: Double,
    val lng: Double,
) {
    constructor(station: MetroStation) : this(
        station.id, station.name, station.line, station.lat, station.lng,
    )
}
