package dev.feature.discounts.presentation.map

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun OffersMap(
    markers: List<OfferMarker>,
    center: MapPoint,
    modifier: Modifier,
    onMarkerTap: (String) -> Unit,
) {
    // Callback har rekompozitsiyada yangilanishi mumkin — ko'prik eng oxirgisini chaqirsin.
    val currentOnTap by rememberUpdatedState(onMarkerTap)

    // HTML faqat markerlar/markaz o'zgarganda qayta quriladi — boshqa rekompozitsiyada
    // xarita (surish/zoom) qayta yuklanib ketmasligi uchun.
    val html = remember(markers, center) { offersMapHtml(center, markers) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(0)
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMarker(id: String) {
                            // JS oqimidan keladi — Compose holatiga faqat asosiy oqimdan tegamiz.
                            post { currentOnTap(id) }
                        }
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
                webView.loadDataWithBaseURL(TILE_HOST, html, "text/html", "utf-8", null)
            }
        },
    )
}
