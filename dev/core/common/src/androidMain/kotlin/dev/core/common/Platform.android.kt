package dev.core.common

actual val platformName: String = "Android"

/**
 * Qurilma nomi — `POST auth/student/refresh` va `login` so'rovlarida sessiya yorlig'i.
 *
 * ⚠️ `filterNot { isNullOrBlank() }`, oddiy `filter { isNotBlank() }` EMAS:
 * `Build.MANUFACTURER` platforma tipi (`String!`) va JVM birlik testlarida u `null`
 * bo'ladi (mockable `android.jar` da qiymat yo'q). Oldingi variant o'sha yerda NPE
 * bilan yiqilar va uni ISHLATADIGAN har qanday testni — jumladan token yangilash
 * testlarini — sindirardi.
 */
actual val deviceName: String = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
    .filterNot { it.isNullOrBlank() }
    .joinToString(" ")
    .ifBlank { "Android" }
