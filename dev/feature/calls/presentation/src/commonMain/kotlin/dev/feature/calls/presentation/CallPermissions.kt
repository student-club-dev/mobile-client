package dev.feature.calls.presentation

import androidx.compose.runtime.Composable

/**
 * Mikrofon (va video qo'ng'iroqda kamera) ruxsatini so'raydi.
 *
 * Ruxsat qo'ng'iroq **boshlanishidan oldin** so'raladi, ekran ochilishida emas: aks holda
 * so'rov sababsiz ko'rinadi va ko'pincha rad etiladi. Rad etilsa `onResult(false)` keladi
 * va qo'ng'iroq umuman boshlanmaydi — server hech narsa bilmaydi, chegara sarflanmaydi.
 */
@Composable
expect fun rememberCallPermissions(onResult: (Boolean) -> Unit): CallPermissionRequester

/** Ruxsat so'rovchi — tugma bosilganda [request] chaqiriladi. */
fun interface CallPermissionRequester {
    /** [video] `true` bo'lsa kamera ham so'raladi. */
    fun request(video: Boolean)
}
