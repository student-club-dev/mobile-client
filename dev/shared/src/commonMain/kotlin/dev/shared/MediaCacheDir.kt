package dev.shared

import coil3.PlatformContext
import okio.Path

/**
 * Yuklab olingan rasmlar saqlanadigan papka — telefondagi **`StudentClub`** ichida.
 *
 * Nega o'zimiz belgilaymiz (Coil'ning sukutdagi papkasi ham bor):
 * - sukutdagi joy — tizimning **vaqtinchalik** papkasi (`java.io.tmpdir`), uni OS xotira
 *   tugaganda birinchi bo'lib tozalaydi. «Har doim ko'rish» talabi esa aynan buning
 *   teskarisi;
 * - papka nomi ilovaniki bo'lsa, keshni topish ham, tozalash ham tushunarli bo'ladi.
 *
 * ⚠️ Bu ilovaning **o'z** papkasi, galereya emas: chatdagi har bir rasm telefon albomiga
 * tushib ketmasligi kerak. Galereyaga saqlash — foydalanuvchi o'zi tanlaydigan alohida amal.
 */
internal expect fun imageCacheDirectory(context: PlatformContext): Path

/**
 * Rasm keshining chegarasi — 256 MB.
 *
 * Chegara **shart**: usiz papka cheksiz o'sardi. LRU eng eski fayllarni o'zi chiqarib
 * tashlaydi, ya'ni yaqinda ko'rilgan narsa doim joyida qoladi.
 */
internal const val IMAGE_CACHE_MAX_BYTES: Long = 256L * 1024 * 1024
