package dev.feature.listings.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberPhoneCaller(): (phone: String) -> Unit = remember {
    { phone ->
        // iOS'da `tel:` simulyatorda va iPad'da ochilmaydi — canOpenURL bilan tekshirmasak
        // hech narsa bo'lmaydi, lekin xato ham ko'rinmaydi.
        val url = NSURL.URLWithString("tel:${phone.toDialable()}")
        if (url != null && UIApplication.sharedApplication.canOpenURL(url)) {
            UIApplication.sharedApplication.openURL(url)
        }
    }
}

/** Probel va qavslar `tel:` sxemasida ishlamaydi — faqat raqam va boshidagi "+" qoladi. */
private fun String.toDialable(): String {
    val plus = if (trimStart().startsWith("+")) "+" else ""
    return plus + filter { it.isDigit() }
}
