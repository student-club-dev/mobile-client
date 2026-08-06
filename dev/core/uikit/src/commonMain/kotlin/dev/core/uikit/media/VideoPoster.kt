package dev.core.uikit.media

/**
 * Videoning **birinchi kadri** — pufakda ko'rsatiladigan "yuzi".
 *
 * Nega klientda hisoblanadi: server `?variant=thumb` da video uchun rasm qaytarmaydi
 * (video baytlarining o'zi keladi), Coil esa videodan kadr chiqara olmaydi — natijada
 * pufakda bo'sh kulrang to'rtburchak turardi va foydalanuvchi qaysi video ekanini faqat
 * ochib bilardi.
 *
 * Kadr **bir marta** ajratiladi va ilova keshiga JPEG bo'lib yoziladi: keyingi
 * chaqiruvlar faylning o'zini qaytaradi. Kesh — ilovaniki (galereya emas): poster
 * foydalanuvchining rasmlari orasida ko'rinishi kerak emas.
 *
 * ⚠️ Manba sifatida iloji bo'lsa **telefondagi nusxa** berilsin
 * ([cacheRemoteToStudentClubFolder] saqlagan `content://`/`file://`): o'shanda kadr
 * darhol chiqadi. Tarmoq havolasi ham ishlaydi — platforma faqat kerakli baytlarni
 * o'qiydi, butun videoni emas — lekin sekinroq.
 *
 * @param source `content://`, `file://` yoki `https://` havola
 * @param headers himoyalangan havola uchun `Authorization` (local manbada e'tiborsiz)
 * @param cacheKey keshdagi nom uchun barqaror kalit — odatda `mediaId`
 * @return posterning `file://` havolasi, yoki `null` — kadr chiqmadi (buzuq fayl, kodek
 *   yo'q, tarmoq uzildi). `null` da pufak eskicha bo'sh fon bilan chiziladi.
 */
expect suspend fun videoPosterUrl(
    source: String,
    headers: Map<String, String>,
    cacheKey: String,
): String?
