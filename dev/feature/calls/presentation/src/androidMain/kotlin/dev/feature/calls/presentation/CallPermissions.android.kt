package dev.feature.calls.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberCallPermissions(onResult: (Boolean) -> Unit): CallPermissionRequester {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        // ⚠️ Faqat MIKROFON majburiy: kamera rad etilsa video qo'ng'iroq ovozli bo'lib
        // davom etadi (`WebRtcCallEngine.startCamera` xatoni yutadi). Mikrofonsiz esa
        // qo'ng'iroqning ma'nosi yo'q.
        currentOnResult(granted[Manifest.permission.RECORD_AUDIO] != false)
    }

    return remember(context, launcher) {
        CallPermissionRequester { video ->
            val needed = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (video) add(Manifest.permission.CAMERA)
            }
            val missing = needed.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) currentOnResult(true) else launcher.launch(missing.toTypedArray())
        }
    }
}
