package dev.core.uikit.media

import androidx.compose.runtime.Composable

/**
 * Tanlangan video — `POST /v1/media/chat-upload?kind=VIDEO` yoki `STORY_VIDEO` ga ketadi.
 *
 * Baytlar emas, **fayl yo'li** saqlanadi: video 64 MB gacha bo'lishi mumkin va uni xotiraga
 * o'qish arzon telefonda ilovani quladi. Yuklovchi ([MediaUploader.chatUploadFile]) faylni
 * oqim bilan o'qiydi, ya'ni xotirada faqat bufer qoladi.
 */
class PickedVideo(
    /**
     * Videoning **manzili** — ikki xil bo'lishi mumkin:
     *
     * - ilova keshidagi fayl yo'li (`/data/.../outgoing_video_1.mp4`) — fayl **bizniki**,
     *   uni [deleteMediaFile] bilan o'chirish chaqiruvchining ishi;
     * - tizim tanlagichining havolasi (`content://…`) — fayl **hali ko'chirilmagan**.
     *
     * Ikkinchisi ataylab: galereyadan tanlangan 180 MB lik lavhani keshga ko'chirish bir
     * necha soniya va o'sha vaqtda foydalanuvchi ekranda kutib turardi. Siqish baribir
     * manbani o'zi o'qib, natijani keshga yozadi — ya'ni oldindan ko'chirish bekor ish edi.
     * Siqilmaydigan (kichik) videoda nusxa [VideoPreparer] ichida, yuborish bosilgandan
     * keyin olinadi.
     *
     * Qaysi turda ekanini [ownsFile] aytadi; o'ynatish uchun [playbackUrl] ishlating.
     */
    val path: String,
    /**
     * Kengaytma **haqiqiy formatga** mos bo'lishi shart (`video.mp4` / `video.mov`): server
     * MIME'ni baytlardan aniqlaydi, lekin ba'zi proksilar nomga qarab multipart qismini
     * kesib tashlaydi.
     */
    val fileName: String,
    /** Aniqlab bo'lmasa `null` — o'shanda chegarani server tekshiradi. */
    val durationMs: Int?,
    val sizeBytes: Long,
    /** Birinchi kadr (poster) — yuborilgunicha ekranda ko'rsatish uchun; olinmasa `null`. */
    val posterBytes: ByteArray? = null,
    /**
     * Manbaning kadr o'lchami, tezligi va kodeki — **siqish kerakmi** degan savolga shu
     * yerda javob beriladi (qarang `VideoCompressor.android.kt`).
     *
     * Aniqlab bo'lmasa nol/`false` qoladi va video ehtiyot yuzasidan qayta kodlanadi.
     *
     * ⚠️ Hozircha faqat Android to'ldiradi: iOS'da siqish `AVAssetExportSession`
     * presetlari bilan ketadi va bu qiymatlarga qaramaydi.
     */
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    /** Video yo'lagi H.264 (`video/avc`) mi — server aynan shuni kutadi. */
    val isH264: Boolean = false,
)

/**
 * Fayl **bizniki**mi (keshda yotibdimi) — ya'ni uni o'chirish kerakmi va uni to'g'ridan
 * to'g'ri yuklovchiga berish mumkinmi.
 *
 * `false` — [PickedVideo.path] tizim tanlagichining `content://` havolasi: uni o'chirish
 * mumkin emas (u bizniki emas) va yuklovchi ham undan o'qiy olmaydi.
 */
val PickedVideo.ownsFile: Boolean
    get() = !path.startsWith(CONTENT_SCHEME)

/** Videoni [ScVideoPlayer] ga berish uchun havola — fayl ham, `content://` ham o'ynaydi. */
val PickedVideo.playbackUrl: String
    get() = if (ownsFile) localFileUrl(path) else path

private const val CONTENT_SCHEME = "content://"

/** Video tanlashni ishga tushiruvchi. */
fun interface VideoPicker {
    fun pick()
}

/**
 * Galereyadan video tanlagich (Android: `PickVisualMedia(VideoOnly)`,
 * iOS: `PHPickerViewController` + `videosFilter`). [rememberImagePicker] kabi
 * **ruxsat so'ramaydi** — tizim tanlagichi faqat tanlangan faylni beradi.
 *
 * [onResult] `null` — bekor qilindi, chegaradan oshdi yoki o'qib bo'lmadi.
 *
 * ⚠️ Chegaradan oshgani ham `null` bilan keladi (alohida xato turi yo'q), chunki UI'da ikkala
 * holat uchun ham bir xil "video yuborilmadi" xabari ko'rsatiladi.
 *
 * ⚠️ Tanlagich videoni **siqmaydi**: u faqat faylni ilova keshiga ko'chiradi (nusxalash —
 * bir necha soniya), shuning uchun ko'rish ekrani deyarli darrov ochiladi. Siqish
 * yuborish bosilgandan **keyin**, xabarning o'z halqasi ichida ketadi ([VideoPreparer]) —
 * Telegramdagidek.
 */
@Composable
expect fun rememberVideoPicker(onResult: (PickedVideo?) -> Unit): VideoPicker

/**
 * Kamerani ochib **shu yerda** video yozib olish (Android: `CaptureVideo`,
 * iOS: `UIImagePickerController` + kamera manbasi).
 *
 * Natija galereyadan tanlanganidan farq qilmaydi — o'sha [PickedVideo], o'sha chegaralar va
 * o'sha siqish. Shuning uchun chaqiruvchi uchun ikkisi bir xil ko'rinadi.
 *
 * ⚠️ Yozilgan video foydalanuvchining galereyasiga **tushmaydi**: fayl ilova keshiga yoziladi
 * va yuborilgach o'chiriladi. Bu ataylab — tasodifan bosilgan tugma foydalanuvchining
 * galereyasini keraksiz lavhalar bilan to'ldirmasligi kerak.
 */
@Composable
expect fun rememberVideoCapture(onResult: (PickedVideo?) -> Unit): VideoPicker

/**
 * Yuborishdan oldingi **siqish** — Telegramdagi kabi, tanlash paytida emas, yuborilgandan
 * keyin.
 *
 * Nega bunday: siqish 4K lavhada bir necha o'n soniya davom etadi. Uni tanlashdan keyin
 * darrov qilsak, foydalanuvchi ekran oldida kutib turishi kerak bo'lardi. Telegram esa
 * xabarni darhol ro'yxatga qo'yadi (poster bilan) va siqishni yuklash bilan **bitta halqada**
 * ko'rsatadi — foydalanuvchi shu vaqtda boshqa xabar yozishi mumkin.
 */
fun interface VideoPreparer {
    /**
     * Videoni yuborishga tayyorlaydi.
     *
     * Qaytadi: yuboriladigan fayl. Siqish kerak bo'lmasa ([videoNeedsPreparing]) — [video]
     * ning o'zi; siqilgan bo'lsa — yangi fayl (asl nusxa o'chiriladi). `null` — siqib ham
     * chegaraga sig'madi yoki fayl o'qilmadi.
     *
     * [onProgress] — siqish ulushi `0f..1f`.
     */
    suspend fun prepare(video: PickedVideo, onProgress: (Float) -> Unit): PickedVideo?
}

@Composable
expect fun rememberVideoPreparer(): VideoPreparer

/**
 * Video siqilishi kerakmi.
 *
 * Kichik fayl qayta kodlanmaydi: bu sifatni bekorga tushirardi va bir necha soniya vaqt
 * olardi. Chegara serverning 64 MB'idan ancha past — mobil internetda 12 MB ham sezilarli,
 * ustiga siqilgan video deyarli har doim shundan yengil chiqadi.
 *
 * Chaqiruvchiga ham kerak: halqaning qancha qismi siqishga ajratilishi shunga bog'liq
 * (siqilmaydigan videoda halqa darrov yuklashdan boshlanadi).
 */
fun videoNeedsPreparing(sizeBytes: Long): Boolean = sizeBytes > COMPRESS_ABOVE_BYTES

/** Shundan kattasi siqiladi — ikkala platformada bir xil. */
private const val COMPRESS_ABOVE_BYTES = 12L * 1024 * 1024

/**
 * Yuborilishi mumkin bo'lgan eng katta hajm (64 MB).
 *
 * ⚠️ Serverda ham shu chegara bor, lekin bu yerda **oldin** to'sish kerak: bundan kattasini
 * yuborish uzoq ketadi va so'rov baribir `413` bilan qaytadi — ya'ni trafikni xato uchun
 * sarflagan bo'lamiz.
 */
const val MAX_VIDEO_BYTES: Long = 64L * 1024 * 1024

/** Yuborilishi mumkin bo'lgan eng uzun davomiylik (3 daqiqa) — server ham shuni tekshiradi. */
const val MAX_VIDEO_MS: Int = 3 * 60 * 1000
