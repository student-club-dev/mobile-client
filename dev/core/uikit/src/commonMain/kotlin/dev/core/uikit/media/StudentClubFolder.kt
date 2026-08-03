package dev.core.uikit.media

/**
 * Foydalanuvchining **o'z** medialari uchun telefon xotirasidagi papka — Telegramdagi
 * `Telegram/Telegram Images` va `Telegram Video` ning ekvivalenti.
 *
 * Nega kerak: qo'ygan lavhangizni ko'rish uchun uni serverdan **qayta yuklab olish** —
 * bekorga sarflangan trafik va kutish. Fayl allaqachon telefonda edi; endi u yuborilgandan
 * keyin ham qoladi va ekranda o'sha local nusxa ochiladi.
 *
 * Papka ilova keshi emas, **ochiq xotira**: uni foydalanuvchi galereyasidan ham topadi va
 * ilova o'chirilsa ham yo'qolmaydi.
 *
 * - Android: `Pictures/StudentClub` va `Movies/StudentClub` (MediaStore, ya'ni galereyada
 *   alohida albom bo'lib ko'rinadi);
 * - iOS: ilova hujjatlaridagi `StudentClub` papkasi (Fayllar ilovasida ko'rinadi;
 *   Photos'ga yozish alohida ruxsat so'rardi va u lavha uchun ortiqcha).
 */
const val STUDENT_CLUB_FOLDER = "StudentClub"

/**
 * Faylni [STUDENT_CLUB_FOLDER] ga **nusxalaydi**. Manba tegilmaydi (yuborish oqimi uni
 * o'zi o'chiradi).
 *
 * [fileName] — papkadagi nom; u bilan fayl keyin qidiriladi ([studentClubMediaUrls]),
 * shuning uchun u **barqaror** bo'lishi kerak (`story_<id>.mp4`).
 *
 * Qaytadi: o'qish uchun havola (Android'da `content://`, iOS'da `file://`), yoki `null` —
 * saqlab bo'lmadi. `null` **xato emas**: media baribir serverda bor, shunchaki tarmoqdan
 * o'qiladi.
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
 * Papkadagi hamma media — `fayl nomi → havola`.
 *
 * Bitta so'rov bilan olinadi va butun ro'yxatga (profildagi postlar to'ri, arxiv) yetadi:
 * har bir post uchun alohida qidirish 30 ta so'rov degani bo'lardi.
 */
expect suspend fun studentClubMediaUrls(): Map<String, String>

/**
 * Story medialarining papkadagi nomi — **story id'si bo'yicha**, ya'ni alohida jadval yoki
 * kesh saqlash shart emas: fayl bor-yo'qligining o'zi javob.
 */
fun storyMediaFileName(storyId: String, isVideo: Boolean): String =
    "story_$storyId.${if (isVideo) "mp4" else "jpg"}"
