@file:OptIn(ExperimentalForeignApi::class)

package dev.core.uikit.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
// ⚠️ Obj-C **kategoriya** metodlari — Kotlin/Native ularni kengaytma sifatida beradi va
// import qilinmasa umuman ko'rinmaydi (`setValue` o'rniga stdlib'ning delegat versiyasi
// topilib, xato tushunarsiz bo'lib chiqadi).
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.writeToFile
import kotlin.coroutines.resume

/**
 * iOS: papka ilova **hujjatlarida** (`Documents/StudentClub`) ochiladi va Fayllar
 * ilovasidan ko'rinadi.
 *
 * Photos kutubxonasiga yozish ataylab tanlanmadi: u `NSPhotoLibraryAddUsageDescription`
 * va foydalanuvchidan ruxsat so'rashni talab qiladi. O'z lavhangizni qayta yuklab
 * olmaslik uchun ruxsat so'rash — nomutanosib; kerak bo'lsa foydalanuvchi «Saqlash»
 * tugmasi bilan galereyaga o'zi ko'chiradi.
 */
actual suspend fun saveToStudentClubFolder(
    sourcePath: String,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.Default) {
    val target = prepareTarget(fileName, isVideo) ?: return@withContext null
    val source = sourcePath.removePrefix("file://")
    val manager = NSFileManager.defaultManager

    if (manager.fileExistsAtPath(target)) return@withContext localFileUrl(target)
    if (!manager.copyItemAtPath(source, toPath = target, error = null)) return@withContext null
    localFileUrl(target)
}

actual suspend fun saveBytesToStudentClubFolder(
    bytes: ByteArray,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.Default) {
    val target = prepareTarget(fileName, isVideo) ?: return@withContext null
    if (NSFileManager.defaultManager.fileExistsAtPath(target)) return@withContext localFileUrl(target)
    val data = bytes.toNSData() ?: return@withContext null
    if (!data.writeToFile(target, atomically = true)) return@withContext null
    localFileUrl(target)
}

actual suspend fun cacheRemoteToStudentClubFolder(
    url: String,
    headers: Map<String, String>,
    fileName: String,
    isVideo: Boolean,
): String? = withContext(Dispatchers.Default) {
    val target = prepareTarget(fileName, isVideo) ?: return@withContext null
    // Allaqachon yuklab olingan bo'lsa tarmoqqa CHIQMAYMIZ.
    if (NSFileManager.defaultManager.fileExistsAtPath(target)) return@withContext localFileUrl(target)

    val request = NSMutableURLRequest(uRL = NSURL(string = url)).apply {
        headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
    }

    // `downloadTask` faylni diskka oqim bilan yozadi — video xotiraga o'qilmaydi.
    // Tur ATAYLAB ochiq yozilgan: u faqat `resume` chaqiruvidan kelib chiqadi va
    // kompilyator uni lambda ichidan chiqarib ololmaydi.
    val temp = suspendCancellableCoroutine<String?> { continuation ->
        val task = NSURLSession.sharedSession.downloadTaskWithRequest(request) { location, response, _ ->
            val code = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            // Xato javobining tanasi ham faylga tushadi: uni saqlasak keyingi ochilishda
            // "yuklab olingan" deb hisoblanardi va video umuman ochilmasdi.
            continuation.resume(if (code in 200..299) location?.path else null)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    } ?: return@withContext null

    val manager = NSFileManager.defaultManager
    // Sessiya vaqtinchalik faylni o'zi o'chiradi, shuning uchun ko'chirish emas — KO'CHIRIB
    // OLISH (`moveItem`): nusxalash 100 MB videoni ikki marta diskka yozardi.
    if (!manager.moveItemAtPath(temp, toPath = target, error = null)) return@withContext null
    localFileUrl(target)
}

actual suspend fun studentClubMediaUrls(): Map<String, String> = withContext(Dispatchers.Default) {
    val root = studentClubDirectory() ?: return@withContext emptyMap()
    val manager = NSFileManager.defaultManager
    // Ildiz (rasm/lavha) + `Video` ichki papkasi. Ikkalasi ham ko'riladi: ichki papka
    // qo'shilishidan OLDIN saqlangan videolar to'g'ridan-to'g'ri ildizda yotibdi.
    listOf(root, "$root/$STUDENT_CLUB_VIDEO_FOLDER")
        .flatMap { folder ->
            manager.contentsOfDirectoryAtPath(folder, error = null)
                ?.filterIsInstance<String>()
                .orEmpty()
                .map { it to localFileUrl("$folder/$it") }
        }
        .toMap()
}

/** Papkani (kerak bo'lsa) yaratadi va fayl yo'lini qaytaradi. */
private fun prepareTarget(fileName: String, isVideo: Boolean): String? {
    val root = studentClubDirectory() ?: return null
    val folder = if (isVideo) "$root/$STUDENT_CLUB_VIDEO_FOLDER" else root
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(folder)) {
        manager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
    }
    return "$folder/$fileName"
}

private fun studentClubDirectory(): String? {
    val documents = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: return null
    return "$documents/$STUDENT_CLUB_FOLDER"
}

/** `ByteArray` → `NSData` (`AudioRecorder.ios.kt` dagi teskari o'girish bilan bir uslubda). */
private fun ByteArray.toNSData(): NSData? {
    // Bo'sh massivdan fayl yaratishning ma'nosi yo'q — chaqiruvchi uchun bu xato bilan bir xil.
    if (isEmpty()) return null
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
