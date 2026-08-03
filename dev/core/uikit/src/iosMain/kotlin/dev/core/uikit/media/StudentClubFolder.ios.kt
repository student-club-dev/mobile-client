@file:OptIn(ExperimentalForeignApi::class)

package dev.core.uikit.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

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
    val target = prepareTarget(fileName) ?: return@withContext null
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
    val target = prepareTarget(fileName) ?: return@withContext null
    if (NSFileManager.defaultManager.fileExistsAtPath(target)) return@withContext localFileUrl(target)
    val data = bytes.toNSData() ?: return@withContext null
    if (!data.writeToFile(target, atomically = true)) return@withContext null
    localFileUrl(target)
}

actual suspend fun studentClubMediaUrls(): Map<String, String> = withContext(Dispatchers.Default) {
    val folder = studentClubDirectory() ?: return@withContext emptyMap()
    val names = NSFileManager.defaultManager
        .contentsOfDirectoryAtPath(folder, error = null)
        ?.filterIsInstance<String>()
        .orEmpty()
    names.associateWith { localFileUrl("$folder/$it") }
}

/** Papkani (kerak bo'lsa) yaratadi va fayl yo'lini qaytaradi. */
private fun prepareTarget(fileName: String): String? {
    val folder = studentClubDirectory() ?: return null
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
