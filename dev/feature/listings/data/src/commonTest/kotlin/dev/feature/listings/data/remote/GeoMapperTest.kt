package dev.feature.listings.data.remote

import dev.core.network.generated.model.GeocodeResultDto
import dev.core.network.generated.model.ReverseGeocodeResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backend geokoderining javobi domenga o'girilishi (`POST /v1/geo/geocode` va `/v1/geo/reverse-geocode`).
 *
 * Nega test kerak: ilgari viloyat/tuman **nom bo'yicha taxmin qilinardi** (Nominatim matn
 * berardi, ilova uni `GeoCatalog` bilan solishtirardi) va bu jimgina noto'g'ri viloyat
 * tanlanishiga olib kelardi. Endi id serverdan keladi — shu qoida qotirilgan.
 */
class GeoMapperTest {

    @Test
    fun `teskari geokod id larni o'zgartirmasdan uzatadi`() {
        val resolved = ReverseGeocodeResponseDto(
            regionId = "TOSHKENT_SHAHRI",
            districtId = "CHILONZOR",
            address = "Bunyodkor ko'chasi, 12",
            nearestMetro = "Chilonzor",
        ).toResolved()

        assertEquals("Bunyodkor ko'chasi, 12", resolved.address)
        // ⚠️ Taxmin YO'Q: id aynan serverdan kelgan holda qoladi.
        assertEquals("TOSHKENT_SHAHRI", resolved.regionId)
        assertEquals("CHILONZOR", resolved.districtId)
    }

    @Test
    fun `manzil kelmasa bo'sh satr — ekran yiqilmasin`() {
        val resolved = ReverseGeocodeResponseDto(address = null).toResolved()
        assertEquals("", resolved.address)
        assertNull(resolved.regionId)
    }

    @Test
    fun `qidiruv natijasi ikki qatorga bo'linadi`() {
        val suggestion = GeocodeResultDto(
            lat = 41.3111,
            lng = 69.2797,
            formattedAddress = "Amir Temur ko'chasi, 12, Yunusobod, Toshkent",
        ).toSuggestion()

        assertEquals("Amir Temur ko'chasi", suggestion.title)
        assertEquals("12, Yunusobod, Toshkent", suggestion.subtitle)
        assertEquals(41.3111, suggestion.lat)
    }

    @Test
    fun `vergulsiz manzil sarlavhada to'liq qoladi`() {
        // Bo'linadigan joy bo'lmasa sarlavha bo'sh qolmasligi kerak.
        val suggestion = GeocodeResultDto(
            lat = 0.0,
            lng = 0.0,
            formattedAddress = "Mega Planet",
        ).toSuggestion()

        assertEquals("Mega Planet", suggestion.title)
        assertEquals("", suggestion.subtitle)
    }
}
