package dev.feature.discounts.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Xaritadagi bitta e'lon markeri — joylashuv + ustidagi narx yorlig'i. */
data class OfferMarker(
    val id: String,        // e'lon id — marker bosilganda qaytariladi
    val lat: Double,
    val lng: Double,
    val label: String,     // "21k", "890k so'm" ...
    val colorHex: String,  // "#7C5CFF"
    val highlight: Boolean = false,
)

/** JS marker bosilganini Kotlin'ga uzatuvchi ko'prik nomi. */
internal const val OFFERS_MAP_BRIDGE = "OffersMapBridge"

/**
 * Barcha e'lonlarni xaritada narx markerlari bilan ko'rsatadi (suriladi/zoom).
 * [MapPicker] bilan bir xil kutubxonasiz tayl dvigatelidan foydalanadi.
 *
 * @param center xarita boshlang'ich markazi (odatda markerlar o'rtasi yoki foydalanuvchi joyi)
 * @param onMarkerTap marker bosilganda uning e'lon id'si bilan chaqiriladi
 */
@Composable
expect fun OffersMap(
    markers: List<OfferMarker>,
    center: MapPoint,
    modifier: Modifier,
    onMarkerTap: (String) -> Unit,
)

/** Markerlar ro'yxatini JS massiv literaliga aylantiradi. */
internal fun markersJs(markers: List<OfferMarker>): String =
    markers.joinToString(prefix = "[", postfix = "]") { m ->
        val label = m.label.replace("\\", "\\\\").replace("\"", "\\\"")
        val id = m.id.replace("\\", "\\\\").replace("\"", "\\\"")
        """{id:"$id",lat:${m.lat},lng:${m.lng},label:"$label",color:"${m.colorHex}",hl:${m.highlight}}"""
    }

/**
 * Xarita sahifasi — markerli variant. Kutubxonasiz, lekin **masshtablanadigan**:
 *
 * 1. Tayllar `#world` panasida — surish faqat `transform: translate3d` ni yangilaydi (GPU).
 * 2. Render `requestAnimationFrame` bilan kadr boshiga bir marta.
 * 3. **Viewport culling** — faqat ekranda ko'rinadigan markerlar chiziladi.
 * 4. **Clustering** — yaqin markerlar bitta guruh pufagiga yig'iladi (ustiga bosilsa zoom).
 * 5. **DOM pool** — element qayta ishlatiladi, ko'rinadigan pufaklar soni bilan chegaralanadi.
 *
 * Shu sabab marker soni katta bo'lsa ham (backend viewport bo'yicha qaytargani) xarita qotmaydi.
 */
internal fun offersMapHtml(center: MapPoint, markers: List<OfferMarker>): String = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <style>
    html, body { margin: 0; padding: 0; overflow: hidden;
                 background: #E8E6F2; font: 600 12px -apple-system, Roboto, sans-serif; }
    #map { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
           touch-action: none; overflow: hidden; }
    #world { position: absolute; top: 0; left: 0; will-change: transform;
             transform: translate3d(0,0,0); }
    .tile { position: absolute; width: 256px; height: 256px; user-select: none;
            -webkit-user-drag: none; pointer-events: none; }
    #markers { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 5; }
    .mk { position: absolute; transform: translate(-50%, -100%); white-space: nowrap;
          background: #fff; color: #14102D; font: 800 11px -apple-system, Roboto, sans-serif;
          padding: 3px 8px; border-radius: 11px; border: 1.6px solid #7C5CFF;
          box-shadow: 0 2px 5px rgba(0,0,0,.28); pointer-events: auto; cursor: pointer; }
    .mk:active { transform: translate(-50%, -100%) scale(1.08); }
    .mk.hl { background: #7C5CFF; color: #fff; border-color: #fff; }
    /* Guruh (cluster) — dumaloq, markazda soni. */
    .mk.cluster { transform: translate(-50%, -50%); border-radius: 50%;
                  width: 34px; height: 34px; padding: 0; display: flex; align-items: center;
                  justify-content: center; background: #14102D; color: #fff; border-color: #fff;
                  font-size: 12px; }
    .mk.cluster:active { transform: translate(-50%, -50%) scale(1.08); }
    .zoom { position: fixed; right: 10px; bottom: 34px; z-index: 10; display: flex;
            flex-direction: column; gap: 6px; }
    .zoom div { width: 38px; height: 38px; border-radius: 10px; background: #fff; color: #14102D;
                display: flex; align-items: center; justify-content: center; font-size: 20px;
                box-shadow: 0 2px 6px rgba(0,0,0,.2); }
    .attr { position: fixed; right: 4px; bottom: 4px; z-index: 10; font-size: 9px;
            color: #555; background: rgba(255,255,255,.7); padding: 1px 4px; border-radius: 4px; }
  </style>
</head>
<body>
  <div id="map"><div id="world"></div></div>
  <div id="markers"></div>
  <div class="zoom"><div id="zin">+</div><div id="zout">−</div></div>
  <div class="attr">© OpenStreetMap · © CARTO</div>

  <script>
    var TILE = '$TILE_HOST';
    var FALLBACK = '$FALLBACK_TILE_HOST';
    var TS = 256;
    var useFallback = false;
    var MARKERS = ${markersJs(markers)};
    var CLUSTER_CELL = 58; // ekran piksel — shu katakdagi markerlar bitta guruhga yig'iladi

    function tileUrl(z, x, y) {
      if (useFallback) return FALLBACK + '/' + z + '/' + x + '/' + y + '.png';
      return TILE + '/rastertiles/voyager/' + z + '/' + x + '/' + y + '@2x.png';
    }
    var MIN_Z = 3, MAX_Z = 19;

    var z = 12;
    var center = { lat: ${center.lat}, lng: ${center.lng} };

    var map = document.getElementById('map');
    var world = document.getElementById('world');
    var markersLayer = document.getElementById('markers');
    var tiles = {};
    var pool = [];

    function scale() { return TS * Math.pow(2, z); }
    function vw() { return window.innerWidth; }
    function vh() { return window.innerHeight; }

    function project(lat, lng) {
      var s = scale();
      var sinLat = Math.sin(lat * Math.PI / 180);
      return {
        x: (lng + 180) / 360 * s,
        y: (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * s
      };
    }
    function unproject(x, y) {
      var s = scale();
      var n = Math.PI - 2 * Math.PI * y / s;
      return {
        lat: 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))),
        lng: x / s * 360 - 180
      };
    }
    function origin() {
      var c = project(center.lat, center.lng);
      return { x: c.x - vw() / 2, y: c.y - vh() / 2 };
    }
    function screenToLatLng(x, y) {
      var o = origin();
      return unproject(o.x + x, o.y + y);
    }

    function postMarker(id) {
      if (window.$OFFERS_MAP_BRIDGE && window.$OFFERS_MAP_BRIDGE.onMarker) {
        window.$OFFERS_MAP_BRIDGE.onMarker(id);
      } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.$OFFERS_MAP_BRIDGE) {
        window.webkit.messageHandlers.$OFFERS_MAP_BRIDGE.postMessage({ id: id });
      }
    }

    function poolEl(i) {
      var el = pool[i];
      if (!el) { el = document.createElement('div'); markersLayer.appendChild(el); pool[i] = el; }
      return el;
    }

    /**
     * Faqat ko'rinadigan markerlarni ekran-fazoda katak-katak yig'ib chizadi:
     *  - katakda 1 ta -> narx pufagi (bosilsa e'lon ochiladi)
     *  - katakda >1   -> guruh (soni bilan; bosilsa zoom qiladi)
     * DOM elementlari qayta ishlatiladi (pool) — jami ko'rinadigan pufaklar soni bilan chegaralangan.
     */
    function layoutMarkers(o) {
      var minX = o.x - 80, maxX = o.x + vw() + 80, minY = o.y - 80, maxY = o.y + vh() + 80;
      var cells = {};
      for (var i = 0; i < MARKERS.length; i++) {
        var p = project(MARKERS[i].lat, MARKERS[i].lng);
        if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY) continue; // culling
        var sx = p.x - o.x, sy = p.y - o.y;
        var key = Math.floor(sx / CLUSTER_CELL) + '_' + Math.floor(sy / CLUSTER_CELL);
        var c = cells[key];
        if (!c) { c = { n: 0, sx: 0, sy: 0, first: i }; cells[key] = c; }
        c.n++; c.sx += sx; c.sy += sy;
      }

      var idx = 0;
      for (var key in cells) {
        var c = cells[key];
        var cx = c.sx / c.n, cy = c.sy / c.n;
        var el = poolEl(idx++);
        el.style.display = 'block';
        el.style.left = cx + 'px';
        el.style.top = cy + 'px';
        if (c.n === 1) {
          var m = MARKERS[c.first];
          el.className = 'mk' + (m.hl ? ' hl' : '');
          el.textContent = m.label;
          el.style.borderColor = m.hl ? '#fff' : m.color;
          el.onclick = (function (id) { return function (e) { e.stopPropagation(); postMarker(id); }; })(m.id);
        } else {
          el.className = 'mk cluster';
          el.textContent = c.n;
          el.style.borderColor = '#fff';
          el.onclick = (function (ax, ay) { return function (e) { e.stopPropagation(); zoomTo(z + 2, ax, ay); }; })(cx, cy);
        }
      }
      for (; idx < pool.length; idx++) pool[idx].style.display = 'none';
    }

    function ensureTiles(o) {
      var maxTile = Math.pow(2, z);
      var x0 = Math.floor(o.x / TS), x1 = Math.floor((o.x + vw()) / TS);
      var y0 = Math.floor(o.y / TS), y1 = Math.floor((o.y + vh()) / TS);
      var visible = {};
      for (var tx = x0; tx <= x1; tx++) {
        for (var ty = y0; ty <= y1; ty++) {
          if (ty < 0 || ty >= maxTile) continue;
          var wx = ((tx % maxTile) + maxTile) % maxTile;
          var key = z + '/' + tx + '/' + ty;
          visible[key] = true;
          if (!tiles[key]) {
            var img = document.createElement('img');
            img.className = 'tile';
            img.onerror = onTileError;
            img.src = tileUrl(z, wx, ty);
            img.style.left = (tx * TS) + 'px';
            img.style.top = (ty * TS) + 'px';
            tiles[key] = img;
            world.appendChild(img);
          }
        }
      }
      for (var k in tiles) {
        if (!visible[k]) { world.removeChild(tiles[k]); delete tiles[k]; }
      }
    }

    function render() {
      map.style.width = vw() + 'px';
      map.style.height = vh() + 'px';
      var o = origin();
      world.style.transform = 'translate3d(' + (-o.x) + 'px,' + (-o.y) + 'px,0)';
      ensureTiles(o);
      layoutMarkers(o);
    }

    var rafPending = false;
    function scheduleRender() {
      if (rafPending) return;
      rafPending = true;
      requestAnimationFrame(function () { rafPending = false; render(); });
    }

    function onTileError() {
      if (useFallback) return;
      useFallback = true;
      for (var k in tiles) { world.removeChild(tiles[k]); delete tiles[k]; }
      document.querySelector('.attr').textContent = '© OpenStreetMap';
      render();
    }

    function zoomTo(newZ, ax, ay) {
      newZ = Math.max(MIN_Z, Math.min(MAX_Z, newZ));
      if (newZ === z) return;
      var anchor = screenToLatLng(ax, ay);
      z = newZ;
      var p = project(anchor.lat, anchor.lng);
      center = unproject(p.x - ax + vw() / 2, p.y - ay + vh() / 2);
      render();
    }

    var pointers = {};
    var pinch = null;
    function pointerList() { var l = []; for (var id in pointers) l.push(pointers[id]); return l; }
    function distance(a, b) { var dx = a.x - b.x, dy = a.y - b.y; return Math.sqrt(dx * dx + dy * dy); }

    map.addEventListener('pointerdown', function (e) {
      map.setPointerCapture(e.pointerId);
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      var list = pointerList();
      if (list.length === 2) {
        pinch = { startDist: distance(list[0], list[1]), startZ: z,
                  midX: (list[0].x + list[1].x) / 2, midY: (list[0].y + list[1].y) / 2 };
      }
    });
    map.addEventListener('pointermove', function (e) {
      var p = pointers[e.pointerId];
      if (!p) return;
      var dx = e.clientX - p.x, dy = e.clientY - p.y;
      p.x = e.clientX; p.y = e.clientY;
      var list = pointerList();
      if (list.length >= 2 && pinch) {
        var d = distance(list[0], list[1]);
        if (d > 0 && pinch.startDist > 0) {
          var newZ = Math.round(pinch.startZ + Math.log(d / pinch.startDist) / Math.LN2);
          zoomTo(newZ, pinch.midX, pinch.midY);
        }
        return;
      }
      var o = project(center.lat, center.lng);
      center = unproject(o.x - dx, o.y - dy);
      scheduleRender();
    });
    function endPointer(e) {
      delete pointers[e.pointerId];
      if (pointerList().length < 2) pinch = null;
    }
    map.addEventListener('pointerup', endPointer);
    map.addEventListener('pointercancel', endPointer);

    document.getElementById('zin').addEventListener('click', function () { zoomTo(z + 1, vw() / 2, vh() / 2); });
    document.getElementById('zout').addEventListener('click', function () { zoomTo(z - 1, vw() / 2, vh() / 2); });
    window.addEventListener('resize', render);

    render();
  </script>
</body>
</html>
""".trimIndent()
