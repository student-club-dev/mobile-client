package dev.feature.listings.presentation.map

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun OffersMap(
    markers: List<OfferMarker>,
    center: MapPoint,
    dark: Boolean,
    userLocation: MapPoint?,
    bottomInset: Int,
    modifier: Modifier,
    onMarkerTap: (String) -> Unit,
) {
    // Callback har rekompozitsiyada yangilanishi mumkin — ko'prik eng oxirgisini chaqirsin.
    val currentOnTap by rememberUpdatedState(onMarkerTap)

    // HTML faqat markerlar/markaz/mavzu/inset o'zgarganda qayta quriladi. Joylashuv KEY EMAS —
    // jonli joylashuv xaritani qayta yuklamasdan (siljitmasdan) `setMe(...)` orqali yangilanadi.
    val html = remember(markers, center, dark, bottomInset) { offersMapHtml(center, markers, dark, userLocation, bottomInset) }
    val pageReady = remember { mutableStateOf(false) }
    val lastMe = remember { mutableStateOf<MapPoint?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(0)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        pageReady.value = true
                        userLocation?.let { view.evaluateJavascript("setMe(${it.lat},${it.lng})", null) }
                        lastMe.value = userLocation
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMarker(id: String) { post { currentOnTap(id) } }
                    },
                    OFFERS_MAP_BRIDGE,
                )
                tag = html
                loadDataWithBaseURL(TILE_HOST, html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                pageReady.value = false
                webView.loadDataWithBaseURL(TILE_HOST, html, "text/html", "utf-8", null)
            } else if (pageReady.value && userLocation != lastMe.value) {
                // Jonli joylashuv — faqat ko'k nuqtani yangilaymiz.
                lastMe.value = userLocation
                userLocation?.let { webView.evaluateJavascript("setMe(${it.lat},${it.lng})", null) }
            }
        },
    )
}
