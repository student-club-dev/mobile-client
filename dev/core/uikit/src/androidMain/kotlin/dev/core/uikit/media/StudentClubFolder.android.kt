package dev.core.uikit.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android: kesh ilovaning **shaxsiy** papkasida (`filesDir/StudentClub`).
 *
 * ⚠️ MediaStore ATAYLAB ishlatilmaydi. Ilgari media `Pictures/StudentClub` va
 * `Movies/StudentClub/Video` ga yozilardi va galereyada alohida albom bo'lib ko'rinardi.
 * Chatda bitta videoni besh marta yuborish galereyaga besh nusxa qo'shardi: server har
 * yuborishda yangi `mediaId` beradi, ya'ni fayl nomi ham har safar boshqacha bo'lardi va
 * "shu nomli fayl bormi?" tekshiruvi hech qachon ishlamasdi.
 *
 * Endi:
 * - papka galereya skaneriga ko'rinmaydi (`filesDir` — ilovaning ichki xotirasi);
 * - fayl MAZMUNI bo'yicha saqlanadi, ya'ni bir xil video bir marta yotadi;
 * - hech qanday xotira ruxsati kerak emas (ilgari eski Android'da `WRITE_EXTERNAL_STORAGE`
 *   kerak bo'lardi va shuning uchun alohida "legacy" yo'l bor edi — u ham tushib qoldi).
 */
actual suspend fun saveToStudentClubFolder(
    sourcePath: String,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext null
    runCatching {
        openSource(context, sourcePath).use { input -> context.store(input, fileName, isVideo) }
    }.getOrNull()
}

actual suspend fun saveBytesToStudentClubFolder(
    bytes: ByteArray,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext null
    runCatching {
        bytes.inputStream().use { input -> context.store(input, fileName, isVideo) }
    }.getOrNull()
}

actual suspend fun cacheRemoteToStudentClubFolder(
    url: String,
    headers: Map<String, String>,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext null

    // Allaqachon yuklab olingan bo'lsa tarmoqqa CHIQMAYMIZ — funksiyaning butun ma'nosi shu.
    context.existingUrl(fileName)?.let { return@withContext it }

    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            // 3xx/4xx da `inputStream` xato sahifasini bermaydi, lekin nol baytli faylni
            // keshga yozib qo'yish undan ham yomon: keyingi ochilishda u "yuklab olingan"
            // deb hisoblanardi va video umuman ochilmasdi.
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { input -> context.store(input, fileName, isVideo) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

actual suspend fun studentClubMediaUrls(): Map<String, String> = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext emptyMap()
    runCatching {
        context.refsDir().listFiles().orEmpty()
            .filter { it.isFile }
            .mapNotNull { ref ->
                val blob = context.blobOf(ref) ?: return@mapNotNull null
                ref.name to localFileUrl(blob.absolutePath)
            }
            .toMap()
    }.getOrDefault(emptyMap())
}

actual suspend fun purgeLegacyGalleryMedia(): Unit = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext
    val marker = File(context.mediaRoot().apply { mkdirs() }, LEGACY_PURGE_MARKER)
    if (marker.exists()) return@withContext
    runCatching {
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).forEach { collection ->
            // ⚠️ Shart ikki qismdan: papka BIZNIKI va nom ilovaning o'z sxemasiga mos.
            // Foydalanuvchi shu albomga o'zi biror rasm ko'chirgan bo'lsa u qoladi.
            //
            // `ESCAPE '\'` shart: `_` — LIKE ning joker belgisi va usiz `story_%` "story"
            // bilan boshlanadigan HAR QANDAY nomga mos kelardi.
            context.contentResolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                    "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\' OR " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\')",
                arrayOf("%$STUDENT_CLUB_FOLDER%", "story\\_%", "chat\\_%"),
            )
        }
    }
    // Belgi xatodan QAT'I NAZAR qo'yiladi: aks holda o'chirib bo'lmaydigan fayl har
    // ochilishda qayta urinishga sabab bo'lardi.
    runCatching { marker.writeText("1") }
    Unit
}

// ---------------------------------------------------------------------------
// Yozish
// ---------------------------------------------------------------------------

/**
 * Oqimni keshga yozadi va mantiqiy nomni unga bog'laydi.
 *
 * Ikki qadam:
 * 1. oqim vaqtinchalik faylga tushadi (mazmun kalitini hisoblash uchun uni diskda ko'rish
 *    kerak — oqimni ikki marta o'qib bo'lmaydi);
 * 2. kalit hisoblanadi; shu kalitli blob bo'lsa vaqtinchalik fayl O'CHIRILADI, bo'lmasa
 *    o'sha joyga ko'chiriladi.
 *
 * Natijada bir xil video necha marta kelishidan qat'i nazar diskda bitta nusxa qoladi.
 */
private fun Context.store(input: InputStream, fileName: String, isVideo: Boolean): String? {
    // Mantiqiy nom allaqachon bog'langan bo'lsa qayta yozmaymiz — bu eng arzon yo'l.
    existingUrl(fileName)?.let { return it }

    val temp = File(cacheDir, "sc_incoming_${fileName.hashCode()}_${input.hashCode()}.tmp")
    try {
        temp.outputStream().use { output -> input.copyTo(output) }
        // Bo'sh fayl — yaroqsiz javob. Uni saqlash "yuklab olingan" degan yolg'on holat
        // yaratardi va video keyin hech qachon ochilmasdi.
        if (temp.length() == 0L) return null

        val blob = File(blobsDir(), temp.contentKey() + extensionFor(isVideo))
        if (!blob.exists()) {
            // `renameTo` bitta fayl tizimida — nusxalashsiz, ya'ni katta videoda ham oniy.
            if (!temp.renameTo(blob)) {
                temp.copyTo(blob, overwrite = true)
            }
        }
        File(refsDir(), fileName).writeText(blob.name)
        return localFileUrl(blob.absolutePath)
    } finally {
        // `renameTo` muvaffaqiyatli bo'lsa fayl allaqachon ko'chgan — `delete()` shunchaki
        // `false` qaytaradi.
        temp.delete()
    }
}

// ---------------------------------------------------------------------------
// O'qish
// ---------------------------------------------------------------------------

/** Mantiqiy nomga bog'langan blob havolasi, yoki `null` — hali saqlanmagan. */
private fun Context.existingUrl(fileName: String): String? {
    val ref = File(refsDir(), fileName).takeIf { it.isFile } ?: return null
    val blob = blobOf(ref) ?: return null
    return localFileUrl(blob.absolutePath)
}

/**
 * Ref ko'rsatayotgan blob fayli.
 *
 * Blob yo'qolgan bo'lsa (foydalanuvchi ilova ma'lumotlarini tozalagan) ref ham
 * o'chiriladi — aks holda u abadiy "bor, lekin ochilmaydi" holatda qolardi.
 */
private fun Context.blobOf(ref: File): File? {
    val name = runCatching { ref.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: return null
    val blob = File(blobsDir(), name)
    if (!blob.isFile || blob.length() == 0L) {
        ref.delete()
        return null
    }
    return blob
}

// ---------------------------------------------------------------------------
// Yordamchilar
// ---------------------------------------------------------------------------

private fun Context.mediaRoot(): File = File(filesDir, STUDENT_CLUB_FOLDER)

private fun Context.blobsDir(): File = File(mediaRoot(), STUDENT_CLUB_BLOBS).apply { mkdirs() }

private fun Context.refsDir(): File = File(mediaRoot(), STUDENT_CLUB_REFS).apply { mkdirs() }

/**
 * Fayl mazmunidan kalit — hajm + boshidagi va oxiridagi bo'lak (qarang [mediaContentKey]).
 *
 * Butun fayl O'QILMAYDI: 100 MB'lik videoni hash qilish bir necha soniya olardi va bu
 * yuborishdan keyingi jimgina saqlashda sezilib qolardi.
 */
private fun File.contentKey(): String {
    val size = length()
    val sample = minOf(MEDIA_KEY_SAMPLE_BYTES.toLong(), size).toInt()
    RandomAccessFile(this, "r").use { raf ->
        val head = ByteArray(sample).also { raf.seek(0); raf.readFully(it) }
        val tail = ByteArray(sample).also { raf.seek(size - sample); raf.readFully(it) }
        return mediaContentKey(size, head, tail)
    }
}

private fun extensionFor(isVideo: Boolean): String = if (isVideo) ".mp4" else ".jpg"

private fun openSource(context: Context, sourcePath: String): InputStream =
    if (sourcePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(sourcePath))
            ?: error("Manbani ochib bo'lmadi: $sourcePath")
    } else {
        File(sourcePath.removePrefix("file://")).inputStream()
    }

// Sekin tarmoqda ulanish uzoq kutilmaydi, lekin O'QISH uzoq bo'lishi normal: 100 MB video
// mobil internetda bir necha daqiqa yuklanadi va bu vaqtda oqim sekin oqib turadi.
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000

/** Galereyadagi eski nusxalar tozalanganini bildiruvchi belgi fayli. */
private const val LEGACY_PURGE_MARKER = ".legacy_gallery_purged"
