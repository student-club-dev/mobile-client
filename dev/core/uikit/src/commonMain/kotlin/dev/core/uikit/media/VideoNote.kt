package dev.core.uikit.media

import androidx.compose.runtime.Composable

/**
 * Dumaloq video xabarni **yuborishga tayyorlaydi**: markazidan kvadrat qilib kesadi,
 * [MAX_VIDEO_NOTE_MS] gacha qirqadi va [MAX_VIDEO_NOTE_BYTES] ga sig'adigan bitreytda
 * qayta kodlaydi.
 *
 * Uchala amal ham **majburiy**, chunki uchalasi ham serverning talabi:
 * kvadrat bo'lmasa `422 MEDIA_NOT_SQUARE`, uzun yoki og'ir bo'lsa `422`.
 *
 * Nega alohida tayyorlovchi (oddiy [rememberVideoPreparer] emas): u kadrni **kesmaydi**
 * — faqat balandligini cheklaydi va kadr tezligini tushiradi. Dumaloq xabarda esa
 * kesish qaytarib bo'lmaydigan qaror: 16:9 kadrni doiraga solsak odamning yuzi
 * chetlarida qolib ketardi.
 *
 * Natija — o'sha [PickedVideo], ya'ni yuborish oqimi oddiy videodan farq qilmaydi.
 * `null` — kodek qo'llab-quvvatlamadi yoki joy tugadi.
 */
@Composable
expect fun rememberVideoNotePreparer(): VideoPreparer

/**
 * Dumaloq video xabar kadrining tomoni (piksel).
 *
 * 480 — Telegram bilan bir xil tartib: ekranda u ~208 dp doira bo'lib chiziladi, ya'ni
 * bundan yuqori aniqlik ko'rinmaydi, lekin 60 soniyalik faylni 12 MB chegarasidan
 * chiqarib yuborardi.
 */
const val VIDEO_NOTE_SIDE = 480
