package dev.core.domain.model

import dev.core.common.locale.AppLocale

/**
 * "Siz uchun" (Takliflar) katalogining ILOVADAGI tuzatishlari — server katalogi ustidan.
 *
 * Server katalogi biznes uchun tuzilgan, talabaga esa bo'linish boshqacharoq kerak:
 *
 * - **Ijara** (`RENTAL_HOUSE`) Takliflarda umuman ko'rinmaydi: uy-joy ilovada alohida —
 *   "E'lonlar" bo'limida (`ListingKind.RENTAL`) yashaydi. Ikkala joyda turgani chalkashtirardi.
 * - **"Savdo va xizmat"** serverdan bitta guruh bo'lib keladi, lekin kiyim sotib olish bilan
 *   sartaroshxonaga yozilish bir narsa emas — katalogda ikkiga bo'linadi.
 *
 * Bu FAQAT ko'rinish qatlami: so'rovlar baribir serverning guruh kaliti bilan ketadi
 * (bo'lingan bo'lim o'z turlari bo'yicha qo'shimcha filtrlanadi). Shuning uchun serverda
 * yangi tur paydo bo'lsa ilova uni baribir ko'rsatadi — [SERVICE_TYPES] da bo'lmasa
 * savdo tomonida chiqadi.
 */
object CatalogRules {

    /** Takliflar katalogiga tushmaydigan biznes turlari (kalitlar backendniki). */
    val HIDDEN_TYPES = setOf("RENTAL_HOUSE")

    /** "Xizmatlar" bo'limiga ajratiladigan turlar — qolgani savdo tomonida qoladi. */
    val SERVICE_TYPES = setOf("BARBERSHOP", "BEAUTY_SALON", "PRINTING")

    /** Ajratilgan bo'lim kaliti: "SHOPPING" → "SHOPPING:SERVICES" (server guruhi o'zgarmaydi). */
    const val SERVICES_SUFFIX = ":SERVICES"
    const val SERVICES_EMOJI = "🛠"

    /**
     * Ajratilgan bo'limning nomi — serverdan kelmaydi, ilova o'zi qo'yadi, demak
     * tarjimasi ham ilovada. Guruh ro'yxati ViewModel'da (Compose'dan tashqarida)
     * yig'iladi, shuning uchun til [AppLocale] dan olinadi.
     */
    val servicesName: String
        get() = AppLocale.pick(en = "Services", ru = "Услуги", uz = "Xizmatlar")

    fun isHidden(typeKey: String): Boolean = typeKey.uppercase() in HIDDEN_TYPES

    fun isService(typeKey: String): Boolean = typeKey.uppercase() in SERVICE_TYPES

    /**
     * Xizmatlar ajratilgandan keyin qolgan qismning nomi: "Savdo va xizmat" → "Savdo".
     * Nomida "xizmat" bo'lmasa — serverdagi nom o'zgarishsiz qoladi.
     */
    fun goodsName(groupName: String): String =
        groupName.substringBefore(" va xizmat", groupName).trim().ifBlank { groupName }
}
