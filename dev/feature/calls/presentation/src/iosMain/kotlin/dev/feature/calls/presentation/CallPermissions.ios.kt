package dev.feature.calls.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS'da mikrofon/kamera ruxsatini `AVCaptureDevice` so'raydi va u media qatlami bilan
 * birga keladi. Media qatlami hali ulanmagani uchun ([CallVideo] izohiga qarang) bu yerda
 * so'rov yo'q: qo'ng'iroq baribir boshlanmaydi va foydalanuvchi aniq xato ko'radi.
 */
@Composable
actual fun rememberCallPermissions(onResult: (Boolean) -> Unit): CallPermissionRequester =
    remember(onResult) { CallPermissionRequester { onResult(true) } }
