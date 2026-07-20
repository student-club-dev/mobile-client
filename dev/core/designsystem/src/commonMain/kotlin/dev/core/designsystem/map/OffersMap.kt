package dev.core.designsystem.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.core.designsystem.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Xaritadagi bitta e'lon markeri — joylashuv + ustidagi narx yorlig'i. */
data class OfferMarker(
    val id: String,        // e'lon id — marker bosilganda qaytariladi
    val lat: Double,
    val lng: Double,
    val label: String,     // "21k so'm", "300 so'm" ...
    val colorHex: String,  // "#7C5CFF"
    val highlight: Boolean = false,
)

/** JS marker bosilganini Kotlin'ga uzatuvchi ko'prik nomi. */
internal const val OFFERS_MAP_BRIDGE = "OffersMapBridge"

/**
 * Barcha e'lonlarni xaritada narx markerlari bilan ko'rsatadi (suriladi/zoom).
 *
 * **MapLibre GL JS + OpenFreeMap** (vektor, GPU, bepul, API kalitsiz) — WebView ichida.
 * Yorug' mavzuda OpenFreeMap "positron", qorong'uda CARTO "dark-matter" (ikkalasi ham tekin).
 *
 * @param center xarita boshlang'ich markazi
 * @param onMarkerTap marker bosilganda uning e'lon id'si bilan chaqiriladi
 */
@Composable
expect fun OffersMap(
    markers: List<OfferMarker>,
    center: MapPoint,
    dark: Boolean,
    userLocation: MapPoint?,
    bottomInset: Int,          // pastki tugmalar (zoom/locate) uchun pastdan bo'shliq (px) — tab panel ostida qolmasin
    modifier: Modifier,
    onMarkerTap: (String) -> Unit,
)

/**
 * MapLibre kutubxonasi — ilova ichiga joylangan (`composeResources/files/`), CDN'dan EMAS.
 *
 * Ilgari `unpkg.com` dan `<script src>` bilan tortilardi: xarita har ochilganda ~800 KB JS
 * qaytadan tarmoqdan yuklanar, sekin aloqada esa oq ekranda osilib qolardi (aynan shu
 * "Universitet" tab'iga o'tganda seziladigan qotishning sababi edi). Endi fayllar APK/IPA
 * ichida — birinchi o'qishdan keyin xotirada keshlanadi va tarmoq umuman kerak emas.
 *
 * Bir marta o'qiladi va butun jarayon davomida saqlanadi (fayl o'zgarmaydi).
 */
private object MapLibreAssets {
    var js: String? = null
    var css: String? = null
    val loaded: Boolean get() = js != null && css != null
}

/**
 * Kutubxona fayllarini (bir marta) o'qiydi. `false` qaytsa xarita hali qurilmaydi —
 * bu bir necha millisekund, birinchi ochilishda bir marta.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberMapLibreReady(): Boolean {
    var ready by remember { mutableStateOf(MapLibreAssets.loaded) }
    LaunchedEffect(Unit) {
        if (!MapLibreAssets.loaded) {
            runCatching {
                MapLibreAssets.js = Res.readBytes("files/maplibre-gl.js").decodeToString()
                MapLibreAssets.css = Res.readBytes("files/maplibre-gl.css").decodeToString()
            }
        }
        ready = MapLibreAssets.loaded
    }
    return ready
}

/** Sahifaga joylash uchun kutubxona matni — [OffersMap] va [MapPicker] ikkalasi ishlatadi. */
internal fun mapLibreJs(): String = MapLibreAssets.js.orEmpty()

internal fun mapLibreCss(): String = MapLibreAssets.css.orEmpty()

/** Markerlar ro'yxatini JS massiv literaliga aylantiradi. */
internal fun markersJs(markers: List<OfferMarker>): String =
    markers.joinToString(prefix = "[", postfix = "]") { m ->
        val label = m.label.replace("\\", "\\\\").replace("\"", "\\\"")
        val id = m.id.replace("\\", "\\\\").replace("\"", "\\\"")
        """{id:"$id",lat:${m.lat},lng:${m.lng},label:"$label",color:"${m.colorHex}",hl:${if (m.highlight) 1 else 0}}"""
    }

/** Xarita sahifasi — MapLibre GL JS + OpenFreeMap. */
internal fun offersMapHtml(center: MapPoint, markers: List<OfferMarker>, dark: Boolean, userLocation: MapPoint?, bottomInset: Int): String {
    val styleUrl = mapStyleUrl(dark)   // joy tanlash xaritasi bilan bir xil uslub
    val pillBg = if (dark) "#221E38" else "#ffffff"
    val meJs = if (userLocation != null) "{lat:${userLocation.lat},lng:${userLocation.lng}}" else "null"
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <style>${MapLibreAssets.css.orEmpty()}</style>
  <style>
    html, body, #map { margin: 0; padding: 0; height: 100%; width: 100%; }
    #map { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: ${mapBackgroundColor(dark)}; }
    .price { background: $pillBg; color: #14102D; font: 800 11.5px -apple-system, Roboto, sans-serif;
             padding: 4px 10px; border-radius: 13px; border: 1.5px solid #7C5CFF; white-space: nowrap;
             box-shadow: 0 3px 9px rgba(0,0,0,${if (dark) ".4" else ".18"}); cursor: pointer; }
    .cluster { width: 13px; height: 13px; border-radius: 50%; background: #22C55E; border: 2px solid #fff;
               box-shadow: 0 1px 3px rgba(0,0,0,.3); cursor: pointer; }
    .me { width: 15px; height: 15px; border-radius: 50%; background: #2563EB; border: 3px solid #fff;
          box-shadow: 0 1px 5px rgba(0,0,0,.35); }
    .me::after { content: ''; position: absolute; left: 50%; top: 50%; width: 15px; height: 15px;
                 margin: -7.5px 0 0 -7.5px; border-radius: 50%; background: rgba(37,99,235,.35);
                 animation: mepulse 2s ease-out infinite; }
    @keyframes mepulse { 0% { transform: scale(1); opacity: .6; } 100% { transform: scale(3.6); opacity: 0; } }
    .maplibregl-ctrl-group { border-radius: 11px !important; overflow: hidden; }
    .maplibregl-ctrl-bottom-right { margin-bottom: ${bottomInset}px; margin-right: 6px; }
    .maplibregl-ctrl-bottom-left { margin-bottom: ${bottomInset}px; }
    #locate { width: 29px; height: 29px; display: flex; align-items: center; justify-content: center;
              background: none; border: none; cursor: pointer; color: #14102D; }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>${MapLibreAssets.js.orEmpty()}</script>
  <script>
    var MARKERS = ${markersJs(markers)};
    var ME = $meJs;

    function postMarker(id) {
      if (window.$OFFERS_MAP_BRIDGE && window.$OFFERS_MAP_BRIDGE.onMarker) {
        window.$OFFERS_MAP_BRIDGE.onMarker(id);
      } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.$OFFERS_MAP_BRIDGE) {
        window.webkit.messageHandlers.$OFFERS_MAP_BRIDGE.postMessage({ id: id });
      }
    }

    var map = new maplibregl.Map({
      container: 'map',
      style: '$styleUrl',
      center: [${center.lng}, ${center.lat}],
      zoom: $MAP_DEFAULT_ZOOM,
      attributionControl: false
    });
    map.addControl(new maplibregl.AttributionControl({ compact: true }));

    // "Mening joylashuvim" — locate tugmasi (+/− ustida).
    function LocateControl() {}
    LocateControl.prototype.onAdd = function (m) {
      this._map = m;
      var c = document.createElement('div');
      c.className = 'maplibregl-ctrl maplibregl-ctrl-group';
      var b = document.createElement('button');
      b.id = 'locate';
      b.type = 'button';
      b.innerHTML = '<svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="7"/><line x1="12" y1="1.5" x2="12" y2="4.5"/><line x1="12" y1="19.5" x2="12" y2="22.5"/><line x1="1.5" y1="12" x2="4.5" y2="12"/><line x1="19.5" y1="12" x2="22.5" y2="12"/><circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none"/></svg>';
      b.onclick = function () { if (ME) map.flyTo({ center: [ME.lng, ME.lat], zoom: 15 }); };
      c.appendChild(b);
      this._c = c;
      return c;
    };
    LocateControl.prototype.onRemove = function () { this._c.parentNode.removeChild(this._c); };
    map.addControl(new LocateControl(), 'bottom-right');
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right');

    var meMarker = null;
    function placeMe() {
      if (!ME) return;
      if (!meMarker) { var el = document.createElement('div'); el.className = 'me'; meMarker = new maplibregl.Marker({ element: el }); }
      meMarker.setLngLat([ME.lng, ME.lat]).addTo(map);
    }
    // Kotlin tomondan jonli joylashuv yangilanganda chaqiriladi (xarita qayta yuklanmaydi).
    function setMe(lat, lng) {
      ME = { lat: lat, lng: lng };
      if (!map.loaded()) { map.once('load', placeMe); return; }
      placeMe();
    }

    // Har bir e'lonni to'g'ridan-to'g'ri HTML marker (narx pufagi) qilib qo'shamiz — ishonchli.
    // Qo'shilgan marker obyektlari saqlanadi — filtr o'zgarganda ularni o'chirib qayta chizamiz.
    var MARKER_OBJS = [];
    function addMarkers() {
      MARKERS.forEach(function (m) {
        var el = document.createElement('div');
        el.className = 'price';
        el.textContent = m.label;
        el.style.color = m.color;
        if (m.hl) el.style.fontSize = '13.5px';
        el.onclick = (function (id) { return function (e) { e.stopPropagation(); postMarker(id); }; })(m.id);
        MARKER_OBJS.push(new maplibregl.Marker({ element: el, anchor: 'bottom' }).setLngLat([m.lng, m.lat]).addTo(map));
      });
      placeMe();
    }

    /**
     * Kotlin tomondan filtr/qidiruv o'zgarganda chaqiriladi. MUHIM: xarita QAYTA YUKLANMAYDI —
     * shuning uchun MapLibre skripti/plitkalari qaytadan tortilmaydi va foydalanuvchining
     * pan/zoom holati saqlanadi. Ilgari har harf yozilganda butun sahifa qayta yuklanardi.
     */
    function setMarkers(list) {
      MARKERS = list;
      MARKER_OBJS.forEach(function (mk) { mk.remove(); });
      MARKER_OBJS = [];
      if (!map.loaded()) { map.once('load', addMarkers); return; }
      addMarkers();
    }
    if (map.loaded()) addMarkers(); else map.on('load', addMarkers);
  </script>
</body>
</html>
""".trimIndent()
}
