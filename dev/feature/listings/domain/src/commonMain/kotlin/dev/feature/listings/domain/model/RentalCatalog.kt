package dev.feature.listings.domain.model

/**
 * Ijara e'lonining tayyor tanlovlari.
 *
 * Bu yerda ro'yxatlar **qisqa** — ijara formasining asosiy maydonlari ([ListingDetails.Rental])
 * to'g'ridan-to'g'ri modelda, chunki ular hamma ijara e'lonida bir xil: nechi xonalik,
 * nechi kishi bor, nechi kishi kerak, kim uchun. Xizmatlardagi kabi dinamik spetsifikatsiya
 * bu yerda ortiqcha bo'lar edi.
 */
object RentalCatalog {

    /** Xonalar soni uchun tayyor tanlovlar — oxirgisi "5+" ma'nosida. */
    val ROOM_COUNTS = listOf(1, 2, 3, 4, 5)

    /** "Nechi kishi bor" / "nechi kishi kerak" uchun tayyor chiplar. */
    val TENANT_COUNTS = listOf(0, 1, 2, 3, 4, 5, 6)

    /** Depozit (oldindan to'lov) variantlari — oy hisobida. */
    val DEPOSIT_MONTHS = listOf(0, 1, 2, 3)

    /**
     * Uydagi qulayliklar. Kalit backend bilan bir xil (ASCII), yorliq — foydalanuvchiga.
     * Talaba uchun eng ko'p ahamiyatlisi birinchi turadi.
     */
    val AMENITIES: List<ListingCategory> = listOf(
        ListingCategory("WIFI", "Wi-Fi"),
        ListingCategory("FURNITURE", "Mebel bor"),
        ListingCategory("CONDITIONER", "Konditsioner"),
        ListingCategory("WASHER", "Kir yuvish mashinasi"),
        ListingCategory("FRIDGE", "Muzlatgich"),
        ListingCategory("KITCHEN", "Oshxona jihozlari"),
        ListingCategory("HOT_WATER", "Issiq suv"),
        ListingCategory("HEATING", "Isitish tizimi"),
        ListingCategory("SEPARATE_ROOM", "Alohida xona beriladi"),
        ListingCategory("BALCONY", "Balkon"),
        ListingCategory("PARKING", "Avtoturargoh"),
        ListingCategory("ELEVATOR", "Lift"),
        ListingCategory("NEAR_METRO", "Metroga yaqin"),
        ListingCategory("NEAR_UNIVERSITY", "Universitetga yaqin"),
    )

    fun amenity(key: String): ListingCategory? = AMENITIES.firstOrNull { it.key == key }

    /** Turarjoy turiga qarab "nechi xonalik" savolining yozuvi. */
    fun roomCountLabel(type: PropertyType?): String = when (type) {
        PropertyType.ROOM -> "Xonada nechta joy bor"
        PropertyType.BED_SPACE -> "Xonada nechta koyka"
        PropertyType.DORMITORY -> "Xonada nechta o'rin"
        else -> "Nechi xonalik"
    }

    /** Turarjoy turiga qarab odatiy narx birligi. */
    fun defaultPriceUnit(period: RentPeriod): PriceUnit = period.priceUnit
}
