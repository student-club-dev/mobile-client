package dev.core.domain.model

/** Chegirma taklifining olish turi. */
enum class DiscountTag { STUDENT_ID, PROMO_CODE }

/** "Siz uchun" bo'limi kategoriyasi = biznes turi (Ovqat, Kiyim, Game Club...). */
data class DiscountCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val offerCount: Int,
    val accent: Long,
)

/**
 * Bitta e'lon ("Siz uchun" feed'ida). Chegirmali (`isDiscount = true`) yoki
 * chegirmasiz oddiy tovar/xizmat bo'lishi mumkin — UI ikkisini alohida karta bilan chizadi.
 */
data class DiscountOffer(
    val id: String,
    val categoryId: String,          // biznes turi id: "ovqat", "game", "kiyim"...
    val subcategory: String = "",    // tur ichidagi bo'lim: "Pitsa", "PS5", "Tennis", "Polya"
    val gender: String = "",         // "MALE" | "FEMALE" | "" (asosan kiyim uchun)
    val merchant: String,            // "Evos", "Chopar"
    val title: String,               // "barcha pitsalarga" / "PS5 1 soat"
    val isDiscount: Boolean = true,  // false → chegirmasiz oddiy e'lon
    val discountPercent: Int = 0,    // 30 → −30% (isDiscount=false bo'lsa 0)
    val originalPrice: Long = 0,     // asl narx, so'm (0 → narx ko'rsatilmaydi)
    val finalPrice: Long = 0,        // chegirmadan keyingi narx (chegirmasizda = originalPrice)
    val priceUnit: String = "dona",  // "dona", "soat", "oy", "chipta"...
    val tag: DiscountTag = DiscountTag.STUDENT_ID,
    val promoCode: String? = null,
    val location: String? = null,    // "5 filial", "yetkazib berish"
    val expiry: String? = null,      // "bugun tugaydi"
    val emoji: String,
    val bannerAccent: Long,
    val featured: Boolean = false,   // Home feed'da ko'rsatiladimi
) {
    /** Chegirmadan tejaladigan summa (so'm). Chegirmasizda 0. */
    val savedAmount: Long get() = if (isDiscount) (originalPrice - finalPrice).coerceAtLeast(0) else 0
}
