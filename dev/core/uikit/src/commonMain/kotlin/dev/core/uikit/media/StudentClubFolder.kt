package dev.core.uikit.media

/**
 * Foydalanuvchining medialari uchun ilovaning **shaxsiy** kesh papkasi.
 *
 * Nega kerak: qo'ygan lavhangizni yoki chatdagi videoni ko'rish uchun uni serverdan
 * **qayta yuklab olish** — bekorga sarflangan trafik va kutish. Fayl bir marta shu yerga
 * tushadi va keyingi ochilishlarda internetsiz ham ochiladi.
 *
 * ⚠️ Papka GALEREYADA KO'RINMAYDI. Ilgari media MediaStore'ga (`Pictures/StudentClub`,
 * `Movies/StudentClub/Video`) yozilardi va u yerdan foydalanuvchining galereyasiga alohida
 * albom bo'lib chiqardi. Natijada bitta videoni chatda besh marta yuborish galereyaga besh
 * nusxa qo'shardi — server har yuborishda YANGI `mediaId` beradi, ya'ni fayl nomi ham
 * boshqacha bo'lardi. Endi kesh ilovaning o'z papkasida:
 *
 * - Android: `filesDir/StudentClub/…` — `MANAGE_EXTERNAL_STORAGE` ham, MediaStore ham
 *   kerak emas, galereya skaneri bu papkani umuman ko'rmaydi;
 * - iOS: `Application Support/StudentClub/…` — Fayllar ilovasida ham ko'rinmaydi
 *   (`Documents` dan farqi shu).
 *
 * **Dublikat bo'lishi mumkin emas**: fayl MAZMUNI bo'yicha saqlanadi
 * ([STUDENT_CLUB_BLOBS]), mantiqiy nom esa faqat o'sha mazmunga ishora qiladi
 * ([STUDENT_CLUB_REFS]). Bir xil video necha marta yuborilsa/olinsa ham diskda BITTA
 * nusxa qoladi.
 */
const val STUDENT_CLUB_FOLDER = "StudentClub"

/**
 * Mazmun bo'yicha saqlangan fayllar papkasi (`…/StudentClub/blobs`).
 *
 * Fayl nomi — uning mazmunidan olingan kalit ([mediaContentKey]), ya'ni bir xil bayt
 * ketma-ketligi doim bitta faylga tushadi.
 */
const val STUDENT_CLUB_BLOBS = "blobs"

/**
 * Mantiqiy nom → mazmun kaliti (`…/StudentClub/refs`).
 *
 * Har bir ref — nomi mantiqiy nom (`chat_<mediaId>.mp4`), ichida esa blob fayl nomi turgan
 * bir necha o'nlab baytlik fayl. Shu sababli beshta `mediaId` bitta videoga ishora qila
 * oladi va disk beshga ko'paymaydi.
 */
const val STUDENT_CLUB_REFS = "refs"

/**
 * Faylni keshga **nusxalaydi**. Manba tegilmaydi (yuborish oqimi uni o'zi o'chiradi).
 *
 * [fileName] — mantiqiy nom; u bilan fayl keyin qidiriladi ([studentClubMediaUrls]),
 * shuning uchun u **barqaror** bo'lishi kerak (`story_<id>.mp4`).
 *
 * Qaytadi: o'qish uchun havola (`file://`), yoki `null` — saqlab bo'lmadi. `null` **xato
 * emas**: media baribir serverda bor, shunchaki tarmoqdan o'qiladi.
 */
expect suspend fun saveToStudentClubFolder(
    sourcePath: String,
    fileName: String,
    isVideo: Boolean,
): String?

/** O'sha ish, lekin manba xotiradagi baytlar (rasm story shu yo'l bilan yuboriladi). */
expect suspend fun saveBytesToStudentClubFolder(
    bytes: ByteArray,
    fileName: String,
    isVideo: Boolean,
): String?

/**
 * Keshdagi hamma media — `mantiqiy nom → havola`.
 *
 * Bitta o'qish bilan olinadi va butun ro'yxatga (suhbat, profildagi postlar to'ri, arxiv)
 * yetadi: har bir element uchun alohida qidirish yuzlab fayl tizimi so'rovi degani bo'lardi.
 */
expect suspend fun studentClubMediaUrls(): Map<String, String>

/**
 * Story medialarining keshdagi mantiqiy nomi — **story id'si bo'yicha**, ya'ni alohida
 * jadval saqlash shart emas: nom bor-yo'qligining o'zi javob.
 */
fun storyMediaFileName(storyId: String, isVideo: Boolean): String =
    "story_$storyId.${if (isVideo) "mp4" else "jpg"}"

/**
 * Chat videosining keshdagi mantiqiy nomi — server `mediaId` si bo'yicha.
 *
 * `mediaId` yuborilgan ham, kelgan ham video uchun bir xil ishlaydi va qurilmalararo
 * barqaror: nom bor-yo'qligining o'zi «yuklab olinganmi?» degan savolga javob beradi.
 *
 * ⚠️ Bir xil video ikki marta yuborilsa ikkita `mediaId` va ikkita NOM bo'ladi — lekin
 * ular bitta blobga ishora qiladi, ya'ni disk ikki barobar bo'lmaydi.
 */
fun chatVideoFileName(mediaId: String): String = "chat_$mediaId.mp4"

/**
 * Videoni serverdan **bir marta** yuklab olib keshga yozadi va local havolasini qaytaradi.
 *
 * Shu nomli ref allaqachon bo'lsa tarmoqqa umuman chiqilmaydi — mavjud havola qaytadi.
 * Aynan shu sabab videoni ikkinchi marta ko'rish trafik sarflamaydi.
 *
 * [headers] — media havolasi himoyalangan (`/v1/media/{id}/raw`), shuning uchun
 * `Authorization` shu yerdan beriladi.
 *
 * Fayl **oqim bilan** yoziladi: video xotiraga o'qilmaydi ([saveBytesToStudentClubFolder]
 * dan farqi shu — u faqat rasm uchun).
 *
 * Qaytadi: `file://` havola, yoki `null` — saqlab bo'lmadi. `null` **xato emas**: video
 * baribir serverda bor, ekran eskicha tarmoqdan o'qiydi.
 */
expect suspend fun cacheRemoteToStudentClubFolder(
    url: String,
    headers: Map<String, String>,
    fileName: String,
    isVideo: Boolean,
): String?

/**
 * Ilovaning ESKI versiyalari galereyaga yozib qo'ygan nusxalarni **bir marta** o'chiradi.
 *
 * Ilgari media MediaStore'ga tushardi va foydalanuvchining galereyasida `StudentClub`
 * albomi bo'lib ko'rinardi — bitta video besh marta yuborilsa besh nusxa bilan. Yangi
 * versiyada kesh ilovaning shaxsiy papkasida, lekin eski nusxalar galereyada QOLIB
 * KETADI: ularni foydalanuvchi qo'lda tozalashi kerak bo'lardi.
 *
 * ⚠️ Faqat ILOVA O'ZI yozgan fayllar o'chiriladi: `StudentClub` papkasidagi `story_…` va
 * `chat_…` nomlilar. Foydalanuvchining o'z rasm va videolariga tegilmaydi.
 *
 * Bir marta ishlaydi (belgi fayli qo'yiladi) va xatolarni yutadi — tozalash muvaffaqiyatsiz
 * bo'lsa ilova baribir normal ishlaydi.
 */
expect suspend fun purgeLegacyGalleryMedia()

// ---------------------------------------------------------------------------
// Mazmun kaliti
// ---------------------------------------------------------------------------

/**
 * Fayl mazmunidan olinadigan **barqaror** kalit — dublikatlarni aniqlash uchun.
 *
 * Nega kriptografik hash emas: SHA-256 platformalarda turlicha chaqiriladi (Android'da
 * `MessageDigest`, iOS'da CoreCrypto) va butun faylni o'qishni talab qiladi — 100 MB'lik
 * videoda bu sezilarli kechikish. Bu yerda esa vazifa **xavfsizlik emas, tejash**: bir xil
 * fayl ikki marta saqlanmasin.
 *
 * Shuning uchun kalit — hajm + boshidagi va oxiridagi bo'lakning FNV-1a hash'i. Ikkita
 * TURLI video bir xil hajmga, bir xil birinchi 64 KB ga va bir xil oxirgi 64 KB ga ega
 * bo'lishi amalda uchramaydi; agar uchrasa ham eng yomon oqibat — ikkinchi video birinchisi
 * bilan almashib qolishi, ya'ni foydalanuvchi uni tarmoqdan qayta ko'radi.
 *
 * [size] — faylning to'liq hajmi, [head] va [tail] — mos ravishda boshidan va oxiridan
 * o'qilgan bo'laklar (fayl kichik bo'lsa ular ustma-ust tushishi mumkin, bu muammo emas).
 */
fun mediaContentKey(size: Long, head: ByteArray, tail: ByteArray): String {
    var hash = FNV_OFFSET
    fun mix(byte: Byte) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= FNV_PRIME
    }
    // Hajm ham hashga kiradi — aks holda bir xil boshlanadigan turli uzunlikdagi
    // fayllar bitta kalitga tushardi.
    repeat(Long.SIZE_BYTES) { i -> mix(((size ushr (i * 8)) and 0xFF).toByte()) }
    head.forEach(::mix)
    tail.forEach(::mix)
    // Hajm kalit NOMIDA ham qoladi: kalitni ko'rib faylni tanib olish oson va
    // to'qnashuv ehtimoli yana bir pog'ona pasayadi.
    return "${size}_${hash.toULong().toString(radix = 16)}"
}

/** Kalit uchun boshidan/oxiridan o'qiladigan bo'lak — 64 KB. */
const val MEDIA_KEY_SAMPLE_BYTES = 64 * 1024

private const val FNV_OFFSET = -3750763034362895579L // 0xCBF29CE484222325
private const val FNV_PRIME = 1099511628211L
