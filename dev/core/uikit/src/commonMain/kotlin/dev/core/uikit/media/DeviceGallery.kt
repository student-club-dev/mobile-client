package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Qurilma galereyasidagi bitta element — **ilova ichidagi** to'rda ko'rsatish uchun.
 *
 * Tizim tanlagichidan farqi shunda: bu yerda fayl hali o'qilmagan, faqat uning manzili va
 * ko'rsatish uchun kerak bo'ladigan minimal ma'lumot bor. Baytlar (yoki video fayli) faqat
 * foydalanuvchi «Yuborish» ni bosganda olinadi ([DeviceGallery.load]) — 40 ta rasmni
 * oldindan o'qish arzon telefonni quladi.
 */
@Immutable
class GalleryItem(
    /** Platforma identifikatori: Android — `content://` havolasi, iOS — `PHAsset.localIdentifier`. */
    val id: String,
    val isVideo: Boolean,
    /** Video davomiyligi; rasmda `0`. */
    val durationMs: Int = 0,
)

/** Galereyaga kirish huquqi. */
enum class GalleryAccess {
    /** Hali so'ralmagan yoki aniqlanmagan. */
    UNKNOWN,
    GRANTED,

    /**
     * Foydalanuvchi **ayrim** rasmlarga ruxsat bergan (Android 14+ «Select photos»,
     * iOS «Limited Photos»). To'r ishlaydi, lekin faqat tanlanganlar ko'rinadi —
     * shuning uchun "yana tanlash" yo'li ko'rsatilishi kerak.
     */
    LIMITED,
    DENIED,
}

/**
 * Qurilma galereyasi — Telegramdagi kabi **ilova ichidagi** biriktirish varag'i uchun.
 *
 * ⚠️ Bu tizim tanlagichining ([rememberMultiMediaPicker]) o'rnini bosmaydi, u bilan yonma-yon
 * yashaydi: to'rdan tez tanlash uchun shu, to'liq galereyani ochish uchun esa o'sha. Sabab —
 * ruxsat: tizim tanlagichi hech qanday ruxsat so'ramaydi va ruxsat berilmagan holatda ham
 * ishlaydi, ya'ni u har doim ishlaydigan zaxira yo'l.
 */
@Stable
interface DeviceGallery {

    /** Compose holati: o'zgarganda to'r o'zi qayta chiziladi. */
    val access: GalleryAccess

    /** Tizim ruxsat oynasini ochadi. Rad etilgan bo'lsa — ilova sozlamalarini. */
    fun requestAccess()

    /**
     * Eng yangi elementlardan boshlab bitta sahifa (rasm va video aralash, sanasi bo'yicha).
     * Ruxsat bo'lmasa — bo'sh ro'yxat.
     */
    suspend fun page(offset: Int, limit: Int): List<GalleryItem>

    /** To'r katagi uchun kichik nusxa; olinmasa `null` (katak bo'sh qoladi, xato emas). */
    suspend fun thumbnail(item: GalleryItem, sizePx: Int): ImageBitmap?

    /**
     * Tanlanganlarni yuborishga tayyor holatga keltiradi: rasm baytlari o'qiladi, video esa
     * keshga ko'chiriladi va tavsiflanadi.
     *
     * **Sekin** ishlaydi (bir necha MB o'qish) — chaqiruvchi yuklanish holatini ko'rsatishi
     * kerak. Yaroqsizlari [PickedMedia.skipped] da sanaladi.
     */
    suspend fun load(items: List<GalleryItem>): PickedMedia
}

/** Galereya to'ri uchun bir sahifadagi elementlar soni. */
const val GALLERY_PAGE_SIZE = 60

@Composable
expect fun rememberDeviceGallery(): DeviceGallery
