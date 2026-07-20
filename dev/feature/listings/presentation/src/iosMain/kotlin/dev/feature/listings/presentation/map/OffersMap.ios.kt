package dev.feature.listings.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    // Joylashuv KEY EMAS — jonli joylashuv xaritani qayta yuklamasdan `setMe(...)` bilan yangilanadi.
    val html = remember(markers, center, dark, bottomInset) { offersMapHtml(center, markers, dark, userLocation, bottomInset) }
    val lastHtml = remember { mutableStateOf<String?>(null) }
    val lastMe = remember { mutableStateOf<MapPoint?>(null) }

    // Handler WKWebView'dan uzoq yashashi kerak — aks holda xabar kelguncha yig'iladi.
    val handler = remember { MarkerMessageHandler() }
    handler.onMarker = onMarkerTap

    UIKitView(
        modifier = modifier,
        factory = {
            val controller = WKUserContentController()
            controller.addScriptMessageHandler(handler, name = OFFERS_MAP_BRIDGE)
            val config = WKWebViewConfiguration().apply { userContentController = controller }
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config)
            webView.opaque = false
            webView.loadHTMLString(html, baseURL = NSURL(string = TILE_HOST))
            lastHtml.value = html
            lastMe.value = userLocation
            webView
        },
        update = { webView ->
            if (lastHtml.value != html) {
                lastHtml.value = html
                lastMe.value = userLocation
                webView.loadHTMLString(html, baseURL = NSURL(string = TILE_HOST))
            } else if (userLocation != lastMe.value) {
                lastMe.value = userLocation
                userLocation?.let { webView.evaluateJavaScript("setMe(${it.lat},${it.lng})", completionHandler = null) }
            }
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
