package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Ilova **ichidagi** to'liq ekranli kamera — Telegramdagi hikoya kamerasi kabi.
 *
 * Nega tizim kamerasi emas: u boshqa ilovaga o'tkazadi va u yerga o'z boshqaruvimizni
 * (chap-pastdagi galereya rasmchasi, «Rasm / Video» almashtirgichi) qo'shib bo'lmaydi.
 * Ilgari «+» bosilganda to'rt bandli TANLOV OYNASI chiqardi — «Suratga olish», «Video
 * yozish», «Galereyadan rasm», «Galereyadan video»; foydalanuvchi har safar shu oynani
 * o'tishi kerak edi.
 *
 * Boshqaruv:
 * - katta yumaloq tugma — surat (rejim `Video` bo'lsa yozishni boshlaydi/to'xtatadi);
 * - pastki qatordagi «Rasm / Video» — rejim;
 * - o'ngdagi tugma — old/orqa kamera;
 * - **chap-pastdagi rasmcha** — galereya ([onOpenGallery]).
 *
 * [galleryThumbnail] — galereyadagi eng oxirgi element kichik nusxasi; `null` bo'lsa
 * o'rniga oddiy ikonka chiziladi (ruxsat berilmagan yoki galereya bo'sh).
 */
@Composable
expect fun ScCameraScreen(
    onPhoto: (PickedImage) -> Unit,
    onVideo: (PickedVideo) -> Unit,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
    galleryThumbnail: ImageBitmap? = null,
    /** `false` — faqat surat (rejim qatori umuman chizilmaydi). */
    allowVideo: Boolean = true,
)

/**
 * Galereyadagi **eng oxirgi** element kichik nusxasi — kamera ekranidagi chap-pastdagi
 * rasmcha uchun.
 *
 * Ruxsat berilmagan yoki galereya bo'sh bo'lsa `null`. Ruxsat bu yerda SO'RALMAYDI:
 * rasmcha shunchaki bezak, uni ko'rsatish uchun foydalanuvchini bezovta qilish ortiqcha —
 * u galereyani ochganda tizim tanlagichi baribir ruxsatsiz ishlaydi.
 */
@Composable
expect fun rememberLatestGalleryThumbnail(sizePx: Int = LATEST_THUMB_PX): ImageBitmap?

/** Chap-pastdagi rasmcha uchun yetarli o'lcham. */
const val LATEST_THUMB_PX = 128
