package dev.core.uikit.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/** Compose kuzatmaydigan oddiy saqlagich — `update` blokida yozish rekompozitsiya keltirmasin. */
internal class Holder<T>(var value: T)

@OptIn(ExperimentalForeignApi::class)
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
    val currentOnTap by rememberUpdatedState(onMarkerTap)

    // MapLibre ilova ichidan o'qiladi (CDN emas) — tayyor bo'lmaguncha WebView qurilmaydi.
    if (!rememberMapLibreReady()) return

    // Sahifa (MapLibre matni bilan birga ~870 KB) fon oqimida yig'iladi — aks holda uni
    // qurish UI oqimini bir necha kadrga bloklardi. Boshlang'ich markaz/markerlar bir marta
    // olinadi; keyingi o'zgarishlar JS orqali (reload YO'Q).
    val html = rememberOffersMapHtml(markers, center, dark, userLocation, bottomInset) ?: return

    val htmlHolder = remember { Holder("") }
    val lastMe = remember { Holder<MapPoint?>(null) }
    val lastMarkers = remember { Holder(markers) }

    // Handler WKWebView'dan uzoq yashashi kerak — aks holda xabar kelguncha yig'iladi.
    val handler = remember { MarkerMessageHandler() }
    // Yon ta'sirni kompozitsiya tanasida emas, SideEffect'da o'rnatamiz (bekor qilingan
    // kompozitsiyada eski lambda o'rnatilib qolmasin).
    SideEffect { handler.onMarker = { id -> currentOnTap(id) } }

    UIKitView(
        modifier = modifier,
        factory = {
            val controller = WKUserContentController()
            controller.addScriptMessageHandler(handler, name = OFFERS_MAP_BRIDGE)
            val config = WKWebViewConfiguration().apply { userContentController = controller }
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config)
            webView.opaque = false
            webView.loadHTMLString(html, baseURL = NSURL(string = MAP_BASE_URL))
            htmlHolder.value = html
            lastMarkers.value = markers
            lastMe.value = userLocation
            webView
        },
        update = { webView ->
            if (htmlHolder.value != html) {
                htmlHolder.value = html
                lastMarkers.value = markers
                lastMe.value = userLocation
                webView.loadHTMLString(html, baseURL = NSURL(string = MAP_BASE_URL))
                return@UIKitView
            }
            // Qidiruv/filtr — markerlarni sahifani qayta yuklamasdan almashtiramiz.
            if (markers != lastMarkers.value) {
                lastMarkers.value = markers
                webView.evaluateJavaScript("setMarkers(${markersJs(markers)})", completionHandler = null)
            }
            if (userLocation != lastMe.value) {
                lastMe.value = userLocation
                userLocation?.let { webView.evaluateJavaScript("setMe(${it.lat},${it.lng})", completionHandler = null) }
            }
        },
        // Ekran yopilganda handler'ni yechamiz — aks holda WKUserContentController handler'ni
        // KUCHLI ushlaydi, handler esa Composition'ni; natijada WKWebView va uning alohida
        // WebContent jarayoni ekran yopilgandan keyin ham yashab, xotira o'sib boradi.
        onRelease = { webView ->
            webView.configuration.userContentController
                .removeScriptMessageHandlerForName(OFFERS_MAP_BRIDGE)
            webView.stopLoading()
            webView.loadHTMLString("", baseURL = null)
        },
    )
}

private class MarkerMessageHandler : NSObject(), WKScriptMessageHandlerProtocol {
    var onMarker: (String) -> Unit = {}

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val id = body["id"] as? String ?: return
        onMarker(id)
    }
}
