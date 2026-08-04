package dev.core.uikit.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android: media galereyaga **albom** bo'lib tushadi (`Pictures/StudentClub`,
 * `Movies/StudentClub`) — Telegram ham aynan shunday qiladi.
 *
 * ⚠️ Android 10 (API 29) dan pastda MediaStore'ga yozish uchun `WRITE_EXTERNAL_STORAGE`
 * ruxsati kerak. Uni lavha saqlash uchun so'rash nomutanosib bo'lardi, shuning uchun eski
 * qurilmalarda papka ilovaning **o'z tashqi papkasida** ochiladi
 * (`Android/data/…/files/Movies/StudentClub`) — ruxsatsiz yoziladi va fayl menejeridan
 * ko'rinadi.
 */
actual suspend fun saveToStudentClubFolder(
    sourcePath: String,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext null
    runCatching {
        openSource(context, sourcePath).use { input -> context.writeToFolder(input, fileName, isVideo) }
    }.getOrNull()
}

actual suspend fun saveBytesToStudentClubFolder(
    bytes: ByteArray,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext null
    runCatching {
        bytes.inputStream().use { input -> context.writeToFolder(input, fileName, isVideo) }
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
    context.existingUrl(fileName, isVideo)?.let { return@withContext it }

    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            // 3xx/4xx da `inputStream` xato sahifasini bermaydi, lekin nol baytli faylni
            // papkaga yozib qo'yish undan ham yomon: keyingi ochilishda u "yuklab olingan"
            // deb hisoblanardi va video umuman ochilmasdi.
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { input -> context.writeToFolder(input, fileName, isVideo) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** Papkadagi tayyor nusxa — MediaStore'dan yoki eski qurilmalarda fayl tizimidan. */
private fun Context.existingUrl(fileName: String, isVideo: Boolean): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        findInMediaStore(fileName, isVideo)
    } else {
        legacyFolder(isVideo)
            ?.let { File(it, fileName) }
            ?.takeIf { it.isFile && it.length() > 0 }
            ?.let { localFileUrl(it.absolutePath) }
    }

actual suspend fun studentClubMediaUrls(): Map<String, String> = withContext(Dispatchers.IO) {
    val context = scMediaContext ?: return@withContext emptyMap()
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.queryMediaStore(isVideo = false) + context.queryMediaStore(isVideo = true)
        } else {
            context.listLegacyFolder(isVideo = false) + context.listLegacyFolder(isVideo = true)
        }
    }.getOrDefault(emptyMap())
}

// ---------------------------------------------------------------------------
// Yozish
// ---------------------------------------------------------------------------

private fun Context.writeToFolder(input: InputStream, fileName: String, isVideo: Boolean): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeToMediaStore(input, fileName, isVideo)
    } else {
        writeToLegacyFolder(input, fileName, isVideo)
    }

private fun Context.writeToMediaStore(input: InputStream, fileName: String, isVideo: Boolean): String? {
    // Allaqachon saqlanган bo'lsa qayta yozilmaydi: MediaStore bir xil nomga
    // "story_x (1).mp4" yaratadi va papka nusxalar bilan to'lib borardi.
    findInMediaStore(fileName, isVideo)?.let { return it }

    val collection = collectionUri(isVideo)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) VIDEO_MIME else IMAGE_MIME)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(isVideo))
        // Nusxalash tugamaguncha fayl galereyada ko'rinmaydi — yarim yozilgan video
        // albomda buzuq element bo'lib turmasin.
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = contentResolver.insert(collection, values) ?: return null
    runCatching {
        contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
            ?: error("openOutputStream = null")
    }.onFailure {
        // Yarim yozilgan qatorni qoldirmaymiz.
        contentResolver.delete(uri, null, null)
        return null
    }

    contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    return uri.toString()
}

private fun Context.writeToLegacyFolder(input: InputStream, fileName: String, isVideo: Boolean): String? {
    val folder = legacyFolder(isVideo) ?: return null
    if (!folder.exists() && !folder.mkdirs()) return null
    val file = File(folder, fileName)
    if (file.exists() && file.length() > 0) return localFileUrl(file.absolutePath)
    file.outputStream().use { output -> input.copyTo(output) }
    return localFileUrl(file.absolutePath)
}

// ---------------------------------------------------------------------------
// O'qish
// ---------------------------------------------------------------------------

private fun Context.findInMediaStore(fileName: String, isVideo: Boolean): String? =
    queryMediaStore(isVideo, displayName = fileName)[fileName]

private fun Context.queryMediaStore(isVideo: Boolean, displayName: String? = null): Map<String, String> {
    val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
    val selection = buildString {
        append("${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?")
        if (displayName != null) append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?")
    }
    val args = buildList {
        add("%$STUDENT_CLUB_FOLDER%")
        displayName?.let { add(it) }
    }.toTypedArray()

    val result = mutableMapOf<String, String>()
    contentResolver.query(collectionUri(isVideo), projection, selection, args, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            result[cursor.getString(nameColumn)] =
                Uri.withAppendedPath(collectionUri(isVideo), id.toString()).toString()
        }
    }
    return result
}

private fun Context.listLegacyFolder(isVideo: Boolean): Map<String, String> {
    val folder = legacyFolder(isVideo) ?: return emptyMap()
    // Videoda ichki `Video` papkasidan tashqari uning ONASI ham ko'riladi: `Video` qo'shilishidan
    // OLDIN saqlangan lavhalar to'g'ridan-to'g'ri `StudentClub` da yotibdi va ular yo'qolmasin.
    val folders = if (isVideo) listOf(folder, folder.parentFile).filterNotNull() else listOf(folder)
    return folders.flatMap { dir ->
        dir.listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
    }.associate { it.name to localFileUrl(it.absolutePath) }
}

// ---------------------------------------------------------------------------
// Yordamchilar
// ---------------------------------------------------------------------------

private fun openSource(context: Context, sourcePath: String): InputStream =
    if (sourcePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(sourcePath))
            ?: error("Manbani ochib bo'lmadi: $sourcePath")
    } else {
        File(sourcePath.removePrefix("file://")).inputStream()
    }

private fun collectionUri(isVideo: Boolean): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    } else {
        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

/**
 * Video → `Movies/StudentClub/Video`, rasm → `Pictures/StudentClub`.
 *
 * Videoga alohida ichki papka: chatdagi har bir video shu yerga tushadi va ular lavha
 * rasmlari bilan aralashib ketmasin. Rasm tomoni ataylab TEGILMAGAN — u yerda allaqachon
 * saqlangan lavhalar bor va ularni ko'chirishning ma'nosi yo'q.
 */
private fun relativePath(isVideo: Boolean): String =
    if (isVideo) {
        "${Environment.DIRECTORY_MOVIES}/$STUDENT_CLUB_FOLDER/$STUDENT_CLUB_VIDEO_FOLDER"
    } else {
        "${Environment.DIRECTORY_PICTURES}/$STUDENT_CLUB_FOLDER"
    }

private fun Context.legacyFolder(isVideo: Boolean): File? {
    val type = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
    // SD-karta chiqarib olingan bo'lsa `null` — o'shanda saqlash o'tkazib yuboriladi.
    val base = getExternalFilesDir(type) ?: return null
    val folder = File(base, STUDENT_CLUB_FOLDER)
    return if (isVideo) File(folder, STUDENT_CLUB_VIDEO_FOLDER) else folder
}

private const val VIDEO_MIME = "video/mp4"
private const val IMAGE_MIME = "image/jpeg"

// Sekin tarmoqda ulanish uzoq kutilmaydi, lekin O'QISH uzoq bo'lishi normal: 100 MB video
// mobil internetda bir necha daqiqa yuklanadi va bu vaqtda oqim sekin oqib turadi.
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000
