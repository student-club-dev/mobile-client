package dev.feature.auth.oauth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS'da GoogleSignIn SDK Swift tomonida yashaydi (Kotlin/Native undan bevosita foydalana
 * olmaydi). Swift ilova ishga tushganda [IosGoogleSignInBridge.delegate] ni o'rnatadi —
 * qarang: `iosApp/iosApp/GoogleSignInBridge.swift`.
 *
 * Delegate o'rnatilmagan bo'lsa oqim [GoogleSignInResult.Unavailable] qaytaradi (tugma aniq
 * xabar beradi, jimgina ishlamay qolmaydi).
 */
object IosGoogleSignInBridge {
    var delegate: IosGoogleSignInDelegate? = null
}

/** Swift shu interfeysni `GIDSignIn` orqali amalga oshiradi. */
interface IosGoogleSignInDelegate {
    /**
     * Google hisob tanlash oynasini ochadi.
     *
     * `onResult(idToken, errorMessage)`:
     * - `idToken` — backend (`POST /v1/auth/student/oauth/google`) tekshiradigan token;
     * - `errorMessage` — xato matni;
     * - ikkalasi ham `null` — foydalanuvchi bekor qildi.
     */
    fun signIn(onResult: (String?, String?) -> Unit)
}

actual class GoogleSignIn {
    actual suspend fun signIn(): GoogleSignInResult {
        val delegate = IosGoogleSignInBridge.delegate ?: return GoogleSignInResult.Unavailable
        return suspendCancellableCoroutine { cont ->
            delegate.signIn { idToken, error ->
                if (!cont.isActive) return@signIn
                val result = when {
                    !idToken.isNullOrBlank() -> GoogleSignInResult.Success(idToken)
                    error != null -> GoogleSignInResult.Failed(error)
                    else -> GoogleSignInResult.Cancelled
                }
                cont.resume(result)
            }
        }
    }
}

@Composable
actual fun rememberGoogleSignIn(): GoogleSignIn = remember { GoogleSignIn() }
