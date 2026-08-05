@file:OptIn(ExperimentalForeignApi::class)

package dev.core.uikit.media

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
// ⚠️ Obj-C **kategoriya** metodlari — Kotlin/Native ularni kengaytma sifatida beradi va
// import qilinmasa umuman ko'rinmaydi (`setValue` o'rniga stdlib'ning delegat versiyasi
// topilib, xato tushunarsiz bo'lib chiqadi).
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.subdataWithRange
import platform.Foundation.writeToFile
import kotlin.coroutines.resume

/**
 * iOS: kesh `Application Support/StudentClub` da.
 *
 * ⚠️ `Documents` EMAS: u Fayllar ilovasida ko'rinadi va iCloud'ga zaxiralanadi — kesh esa
 * na foydalanuvchiga, na zaxiraga kerak. Photos kutubxonasi ham ishlatilmaydi: u ruxsat
 * so'rashni talab qiladi va Android'dagi kabi galereyada dublikat yaratardi.
 *
 * Saqlash Android bilan bir xil sxemada: fayl mazmuni bo'yicha `blobs/` ga tushadi,
 * mantiqiy nom esa `refs/` dagi kichkina fayl orqali unga ishora qiladi.
 */
actual suspend fun saveToStudentClubFolder(
    sourcePath: String,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.Default) {
    existingUrl(fileName)?.let { return@withContext it }
    val manager = NSFileManager.defaultManager
    val source = sourcePath.removePrefix("file://")
    // Vaqtinchalik nusxa — kalit hisoblanguncha manba tegilmasligi kerak (uni yuborish
    // oqimi o'zi o'chiradi va biz uni ko'chirib olsak oqim yiqilardi).
    val temp = tempPath(fileName)
    if (manager.fileExistsAtPath(temp)) manager.removeItemAtPath(temp, error = null)
    if (!manager.copyItemAtPath(source, toPath = temp, error = null)) return@withContext null
    linkTempToBlob(temp, fileName, isVideo)
}

actual suspend fun saveBytesToStudentClubFolder(
    bytes: ByteArray,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.Default) {
    existingUrl(fileName)?.let { return@withContext it }
    val data = bytes.toNSData() ?: return@withContext null
    val temp = tempPath(fileName)
    if (!data.writeToFile(temp, atomically = true)) return@withContext null
    linkTempToBlob(temp, fileName, isVideo)
}

actual suspend fun cacheRemoteToStudentClubFolder(
    url: String,
    headers: Map<String, String>,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.Default) {
    // Allaqachon yuklab olingan bo'lsa tarmoqqa CHIQMAYMIZ.
    existingUrl(fileName)?.let { return@withContext it }

    val request = NSMutableURLRequest(uRL = NSURL(string = url)).apply {
        headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
    }

    // `downloadTask` faylni diskka oqim bilan yozadi — video xotiraga o'qilmaydi.
    // Tur ATAYLAB ochiq yozilgan: u faqat `resume` chaqiruvidan kelib chiqadi va
    // kompilyator uni lambda ichidan chiqarib ololmaydi.
    val downloaded = suspendCancellableCoroutine<String?> { continuation ->
        val task = NSURLSession.sharedSession.downloadTaskWithRequest(request) { location, response, _ ->
            val code = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            // Xato javobining tanasi ham faylga tushadi: uni saqlasak keyingi ochilishda
            // "yuklab olingan" deb hisoblanardi va video umuman ochilmasdi.
            continuation.resume(if (code in 200..299) location?.path else null)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    } ?: return@withContext null

    // Sessiya vaqtinchalik faylni o'zi o'chiradi — avval o'z papkamizga ko'chirib olamiz.
    val temp = tempPath(fileName)
    val manager = NSFileManager.defaultManager
    if (manager.fileExistsAtPath(temp)) manager.removeItemAtPath(temp, error = null)
    if (!manager.moveItemAtPath(downloaded, toPath = temp, error = null)) return@withContext null
    linkTempToBlob(temp, fileName, isVideo)
}

actual suspend fun studentClubMediaUrls(): Map<String, String> = withContext(Dispatchers.Default) {
    val refs = refsDirectory() ?: return@withContext emptyMap()
    val manager = NSFileManager.defaultManager
    manager.contentsOfDirectoryAtPath(refs, error = null)
        ?.filterIsInstance<String>()
        .orEmpty()
        .mapNotNull { name -> blobPath(name)?.let { name to localFileUrl(it) } }
        .toMap()
}

/**
 * iOS'da eski nusxalar `Documents/StudentClub` da yotardi (Fayllar ilovasida ko'rinardi) —
 * butun papka bir marta o'chiriladi. Photos kutubxonasiga hech qachon yozilmagani uchun
 * u yerda tozalaydigan narsa yo'q.
 */
actual suspend fun purgeLegacyGalleryMedia(): Unit = withContext(Dispatchers.Default) {
    val documents = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: return@withContext
    val legacy = "$documents/$STUDENT_CLUB_FOLDER"
    val manager = NSFileManager.defaultManager
    if (manager.fileExistsAtPath(legacy)) {
        manager.removeItemAtPath(legacy, error = null)
    }
    Unit
}

// ---------------------------------------------------------------------------
// Blob / ref
// ---------------------------------------------------------------------------

/**
 * Vaqtinchalik faylni mazmuni bo'yicha blobga aylantiradi va mantiqiy nomni unga bog'laydi.
 *
 * Shu kalitli blob allaqachon bo'lsa vaqtinchalik fayl O'CHIRILADI — aynan shu bir xil
 * videoning ikkinchi nusxasini saqlab qo'yishning oldini oladi.
 */
private fun linkTempToBlob(temp: String, fileName: String, isVideo: Boolean): String? {
    val manager = NSFileManager.defaultManager
    val blobs = blobsDirectory() ?: return null
    val refs = refsDirectory() ?: return null
    val size = fileSize(temp)
    if (size <= 0L) {
        manager.removeItemAtPath(temp, error = null)
        return null
    }
    val blob = "$blobs/${contentKeyOf(temp, size)}${if (isVideo) ".mp4" else ".jpg"}"
    if (manager.fileExistsAtPath(blob)) {
        manager.removeItemAtPath(temp, error = null)
    } else if (!manager.moveItemAtPath(temp, toPath = blob, error = null)) {
        return null
    }
    val blobName = blob.substringAfterLast('/')
    (blobName as NSString).writeToFile(
        "$refs/$fileName",
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    return localFileUrl(blob)
}

/** Mantiqiy nomga bog'langan blob havolasi, yoki `null` — hali saqlanmagan. */
private fun existingUrl(fileName: String): String? = blobPath(fileName)?.let(::localFileUrl)

/**
 * Ref ko'rsatayotgan blob yo'li.
 *
 * Blob yo'qolgan bo'lsa ref ham o'chiriladi — aks holda u abadiy "bor, lekin ochilmaydi"
 * holatda qolardi.
 */
private fun blobPath(fileName: String): String? {
    val refs = refsDirectory() ?: return null
    val blobs = blobsDirectory() ?: return null
    val ref = "$refs/$fileName"
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(ref)) return null
    val blobName = NSString.stringWithContentsOfFile(ref, encoding = NSUTF8StringEncoding, error = null)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val blob = "$blobs/$blobName"
    if (fileSize(blob) <= 0L) {
        manager.removeItemAtPath(ref, error = null)
        return null
    }
    return blob
}

/**
 * Fayl mazmunidan kalit (qarang [mediaContentKey]).
 *
 * Butun fayl xotiraga o'qiladi-yu, faqat chekka bo'laklari hashlanadi: `NSData`
 * `dataWithContentsOfFile` ni mapped rejimda beradi, ya'ni katta video ham fizik xotirani
 * to'ldirmaydi.
 */
private fun contentKeyOf(path: String, size: Long): String {
    val data = NSData.dataWithContentsOfFile(path) ?: return "${size}_nodata"
    val sample = minOf(MEDIA_KEY_SAMPLE_BYTES.toLong(), size).toInt()
    val head = data.toByteArray(offset = 0, length = sample)
    val tail = data.toByteArray(offset = (size - sample).toInt(), length = sample)
    return mediaContentKey(size, head, tail)
}

/**
 * `NSData` ning bir bo'lagi → `ByteArray`.
 *
 * `getBytes(...)` Obj-C **kategoriya** metodi va Kotlin/Native uni ko'rsatmaydi, shuning
 * uchun bo'lak `subdataWithRange` bilan ajratilib, ko'rsatkichdan o'qiladi.
 */
private fun NSData.toByteArray(offset: Int, length: Int): ByteArray {
    if (length <= 0) return ByteArray(0)
    val slice = subdataWithRange(NSMakeRange(offset.toULong(), length.toULong()))
    val pointer = slice.bytes?.reinterpret<ByteVar>() ?: return ByteArray(0)
    return ByteArray(length) { index -> pointer[index] }
}

private fun fileSize(path: String): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        ?: return -1L
    return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: -1L
}

// ---------------------------------------------------------------------------
// Papkalar
// ---------------------------------------------------------------------------

private fun blobsDirectory(): String? = ensureDirectory(STUDENT_CLUB_BLOBS)

private fun refsDirectory(): String? = ensureDirectory(STUDENT_CLUB_REFS)

private fun tempPath(fileName: String): String {
    val root = ensureDirectory("tmp") ?: return fileName
    return "$root/$fileName.tmp"
}

private fun ensureDirectory(name: String): String? {
    val root = studentClubDirectory() ?: return null
    val folder = "$root/$name"
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(folder)) {
        manager.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    return folder
}

private fun studentClubDirectory(): String? {
    val support = NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: return null
    return "$support/$STUDENT_CLUB_FOLDER"
}

/** `ByteArray` → `NSData` (`AudioRecorder.ios.kt` dagi teskari o'girish bilan bir uslubda). */
private fun ByteArray.toNSData(): NSData? {
    // Bo'sh massivdan fayl yaratishning ma'nosi yo'q — chaqiruvchi uchun bu xato bilan bir xil.
    if (isEmpty()) return null
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
