package dev.core.uikit.map

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Compose kuzatmaydigan oddiy saqlagich — `update` blokida yozish rekompozitsiya keltirmasin. */
internal class Holder<T>(var value: T)

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

    // MapLibre ilova ichidan o'qiladi (CDN emas) — tayyor bo'lmaguncha WebView qurilmaydi,
    // aks holda sahifa kutubxonasiz yuklanib bo'sh qolardi.
    if (!rememberMapLibreReady()) return

    // Boshlang'ich markaz/markerlar FAQAT bir marta olinadi. Keyingi o'zgarishlar (qidiruv,
    // filtr, joylashuv) sahifani qayta yuklamasdan JS orqali qo'llaniladi — aks holda har
    // bosilgan harfda MapLibre skripti va barcha plitkalar qaytadan tortilib xarita qotardi.
    val initialCenter = remember { center }
    val initialMarkers = remember { markers }

    // HTML faqat MAVZU yoki pastki inset o'zgarganda qayta quriladi (kamdan-kam hodisa).
    val html = remember(dark, bottomInset) {
        offersMapHtml(initialCenter, initialMarkers, dark, userLocation, bottomInset)
    }

    // Bu holatlar Compose snapshot'i EMAS — `update` ichida o'qish/yozish sikl keltirmaydi.
    val pageReady = remember { Holder(false) }
    val lastMe = remember { Holder<MapPoint?>(null) }
    val lastMarkers = remember { Holder(initialMarkers) }
    val lastHtml = remember { Holder("") }

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
                        // Sahifa tayyor bo'lgach joriy holatni (markerlar + joylashuv) qo'llaymiz.
                        view.evaluateJavascript("setMarkers(${markersJs(lastMarkers.value)})", null)
                        lastMe.value?.let { view.evaluateJavascript("setMe(${it.lat},${it.lng})", null) }
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMarker(id: String) { post { currentOnTap(id) } }
                    },
                    OFFERS_MAP_BRIDGE,
                )
                lastHtml.value = html
                lastMarkers.value = initialMarkers
                lastMe.value = userLocation
                loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            when {
                // Mavzu/inset o'zgardi — sahifani qayta qurish shart.
                lastHtml.value != html -> {
                    lastHtml.value = html
                    pageReady.value = false
                    lastMarkers.value = markers
                    lastMe.value = userLocation
                    webView.loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "utf-8", null)
                }
                !pageReady.value -> Unit // hali yuklanmoqda — onPageFinished qo'llaydi
                else -> {
                    // Qidiruv/filtr natijasi — faqat markerlarni almashtiramiz (reload YO'Q).
                    if (markers != lastMarkers.value) {
                        lastMarkers.value = markers
                        webView.evaluateJavascript("setMarkers(${markersJs(markers)})", null)
                    }
                    // Jonli joylashuv — faqat ko'k nuqta.
                    if (userLocation != lastMe.value) {
                        lastMe.value = userLocation
                        userLocation?.let { webView.evaluateJavascript("setMe(${it.lat},${it.lng})", null) }
                    }
                }
            }
        },
        // Ekran yopilganda WebView'ni to'liq bo'shatamiz. Busiz MapLibre'ning animatsiya sikli va
        // WebGL konteksti tirik qolib, xaritani bir necha marta ochib-yopgach yangi kontekst
        // ochilmay xarita qora ekranga aylanardi; JS ko'prigi esa butun Composition'ni ushlab
        // turgani uchun xotira sizardi.
        onRelease = { webView ->
            webView.removeJavascriptInterface(OFFERS_MAP_BRIDGE)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        },
    )
}
