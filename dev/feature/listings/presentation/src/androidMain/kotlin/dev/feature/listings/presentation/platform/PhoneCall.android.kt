package dev.feature.listings.presentation.platform

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPhoneCaller(): (phone: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { phone ->
            // Terish ilovasi yo'q qurilmada (emulyator, planshet) ActivityNotFoundException
            // otiladi — bu ilovani yiqitmasligi kerak, e'lonni ko'rish baribir ishlayveradi.
            runCatching {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.toDialable()}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            Unit
        }
    }
}

/** Probel va qavslar `tel:` sxemasida ishlamaydi — faqat raqam va boshidagi "+" qoladi. */
private fun String.toDialable(): String {
    val plus = if (trimStart().startsWith("+")) "+" else ""
    return plus + filter { it.isDigit() }
}
