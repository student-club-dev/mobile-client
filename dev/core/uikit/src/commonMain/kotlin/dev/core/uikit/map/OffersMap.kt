package dev.core.uikit.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.core.uikit.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Xaritadagi bitta nuqta — bitta e'lon (narx yorlig'i) yoki bitta BIZNES (nomi + e'lonlar
 * soni) bo'lishi mumkin.
 *
 * Bir do'konning bir nechta e'loni bir xil koordinatada turadi va narx pufaklari ustma-ust
 * tushardi — shuning uchun ular bitta markerga yig'iladi: yorliqda biznes nomi, yonida soni.
 * Bosilganda [id] qaytadi va ekran o'sha guruhning e'lonlarini ko'rsatadi.
 */
data class OfferMarker(
    val id: String,        // e'lon id yoki guruh id'si — marker bosilganda qaytariladi
    val lat: Double,
    val lng: Double,
    val label: String,     // "21k so'm" yoki biznes nomi ("Evos")
    val colorHex: String,  // "#7C5CFF"
    val highlight: Boolean = false,
    /** Guruhdagi e'lonlar soni. 1 — oddiy narx markeri. */
    val count: Int = 1,
)

/** JS marker bosilganini Kotlin'ga uzatuvchi ko'prik nomi. */
internal const val OFFERS_MAP_BRIDGE = "OffersMapBridge"

/**
 * Barcha e'lonlarni xaritada narx markerlari bilan ko'rsatadi (suriladi/zoom).
 *
 * **MapLibre GL JS + OpenFreeMap** (vektor, GPU, bepul, API kalitsiz) — WebView ichida.
 * Uslub ilova mavzusiga ERGASHMAYDI, xarita doim yorug' ([mapStyleUrl] izohiga qarang).
 *
 * @param center xarita boshlang'ich markazi
 * @param onMarkerTap marker bosilganda uning e'lon id'si bilan chaqiriladi
 */
@Composable
expect fun OffersMap(
    markers: List<OfferMarker>,
    center: MapPoint,
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
 *
 * O'qish va `decodeToString` ATAYLAB fon oqimida ([Dispatchers.Default]): fayllar birgalikda
 * ~870 KB va ularni UI oqimida matnga aylantirish "Xarita" bosilganda ekranni bir necha
 * kadrga muzlatib qo'yardi.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberMapLibreReady(): Boolean {
    var ready by remember { mutableStateOf(MapLibreAssets.loaded) }
    LaunchedEffect(Unit) {
        prepareMapAssets()
        ready = MapLibreAssets.loaded
    }
    return ready
}

/**
 * MapLibre fayllarini oldindan xotiraga oladi — **ilova ishga tushganda**.
 *
 * Ilgari ular xarita birinchi marta ochilganda o'qilardi: 870 KB ni diskdan o'qib matnga
 * aylantirish, so'ng shu matndan sahifa yig'ish — hammasi foydalanuvchi "Xaritada ko'rish"
 * ni bosgandan KEYIN boshlanardi va ochilish sezilarli sekin edi (bug hisoboti #29).
 * Endi bu ish ilova ochilishida, foydalanuvchi hali bosh ekranni ko'rib turgan paytda
 * bajariladi; xarita ochilganda fayllar allaqachon tayyor.
 *
 * Qayta chaqirish xavfsiz: fayllar bir marta o'qiladi.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun prepareMapAssets() {
    if (MapLibreAssets.loaded) return
    withContext(Dispatchers.Default) {
        runCatching {
            MapLibreAssets.js = Res.readBytes("files/maplibre-gl.js").decodeToString()
            MapLibreAssets.css = Res.readBytes("files/maplibre-gl.css").decodeToString()
        }
    }
}

/**
 * Xarita sahifasi — **fon oqimida** yig'iladi; tayyor bo'lmaguncha `null`.
 *
 * Sahifa ichida MapLibre'ning butun JS/CSS matni turadi (~870 KB), ya'ni uni qurish oddiy
 * satr birlashtirish emas — megabaytlik nusxa ko'chirish. Kompozitsiya ichida (`remember`)
 * qilinganda bu ish UI oqimiga tushar va "Xaritada ko'rish" bosilgach ekran avval qotib,
 * keyin xarita ochilardi. Endi kompozitsiya darhol qaytadi, HTML esa fonda tayyorlanadi.
 *
 * [markers] va [center] FAQAT birinchi chaqiruvdan olinadi (keyingi o'zgarishlar sahifani
 * qayta yuklamasdan, `setMarkers` orqali qo'llaniladi) — shuning uchun ular kuzatilmaydi.
 */
@Composable
internal fun rememberOffersMapHtml(
    markers: List<OfferMarker>,
    center: MapPoint,
    userLocation: MapPoint?,
    bottomInset: Int,
): String? {
    val initialCenter = remember { center }
    val initialMarkers = remember { markers }
    // Joylashuv ham boshlang'ich qiymatidan olinadi: u kelganda sahifa qayta qurilmaydi,
    // `setMe(...)` bilan faqat ko'k nuqta ko'chadi.
    val initialMe = remember { userLocation }
    val html by produceState<String?>(null, bottomInset) {
        value = withContext(Dispatchers.Default) {
            offersMapHtml(initialCenter, initialMarkers, initialMe, bottomInset)
        }
    }
    return html
}

/** Sahifaga joylash uchun kutubxona matni — [OffersMap] va [MapPicker] ikkalasi ishlatadi. */
internal fun mapLibreJs(): String = MapLibreAssets.js.orEmpty()

internal fun mapLibreCss(): String = MapLibreAssets.css.orEmpty()

/** Markerlar ro'yxatini JS massiv literaliga aylantiradi. */
internal fun markersJs(markers: List<OfferMarker>): String =
    markers.joinToString(prefix = "[", postfix = "]") { m ->
        val label = m.label.replace("\\", "\\\\").replace("\"", "\\\"")
        val id = m.id.replace("\\", "\\\\").replace("\"", "\\\"")
        """{id:"$id",lat:${m.lat},lng:${m.lng},label:"$label",color:"${m.colorHex}",hl:${if (m.highlight) 1 else 0},n:${m.count}}"""
    }

/** Xarita sahifasi — MapLibre GL JS + OpenFreeMap. */
internal fun offersMapHtml(center: MapPoint, markers: List<OfferMarker>, userLocation: MapPoint?, bottomInset: Int): String {
    val styleUrl = mapStyleUrl()   // joy tanlash xaritasi bilan bir xil uslub
    // Chegirma yorliqlari va klaster nuqtasining rangi — ilovadagi "Success" yashili.
    val accentGreen = "#22C55E"
    // Marker rangi: oddiy e'lon — brend ko'ki, bir joyda bir nechtasi — yashil.
    val pinBlue = "#00AEEF"
    // Yorliqlar ham yorug' xaritaga moslangan — mavzuga qarab o'zgarmaydi.
    val labelBg = "#ffffff"
    val labelInk = "#14102D"
    val labelBorder = "rgba(15,42,67,.10)"
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
    #map { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: ${mapBackgroundColor()}; }
    /* Belgilar uchun CSS YO'Q — pin, yorliq, klaster va "mening joylashuvim" nuqtasi
       xaritaning O'Z canvas'i ichida, MapLibre qatlami sifatida chiziladi (pastdagi
       `ensureLayers`). Ilgari ular canvas USTIDAGI HTML elementlar edi: xarita GPU'da,
       belgilar esa DOM'da yangilanardi va WebView bu ikkisini bir kadrda birlashtira
       olmasdi — ikki barmoq bilan zoom qilinganda belgilar xarita ortidan "suzib"
       yurardi. Endi ular xarita bilan bitta kadrda chiziladi, ya'ni undan hech qachon
       ajralmaydi. Bu yerda faqat xarita tugmalari uslubi qoldi. */
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
    // Ikki barmoq FAQAT kattalashtiradi/kichraytiradi — burish va egish o'chirilgan.
    ${mapLockRotationJs()}
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

    // Dizayn ranglari — pastda canvasga chiziladigan rasmlar shulardan foydalanadi.
    var C_PIN = '$pinBlue', C_MULTI = '$accentGreen';
    var C_BG = '$labelBg', C_INK = '$labelInk', C_BORDER = '$labelBorder';

    // "Mening joylashuvim" ham qatlam sifatida chiziladi (HTML nuqta emas) — xarita bilan
    // bir kadrda yangilanadi, ya'ni u ham ishora paytida qimirlamaydi.
    var ME_SRC = 'me';
    function meCollection() {
      return {
        type: 'FeatureCollection',
        features: ME ? [{ type: 'Feature', properties: {},
          geometry: { type: 'Point', coordinates: [ME.lng, ME.lat] } }] : [],
      };
    }
    function placeMe() {
      var s = map.getSource(ME_SRC);
      if (s) s.setData(meCollection());
    }
    // Kotlin tomondan jonli joylashuv yangilanganda chaqiriladi (xarita qayta yuklanmaydi).
    function setMe(lat, lng) {
      ME = { lat: lat, lng: lng };
      if (!map.loaded()) { map.once('load', placeMe); return; }
      placeMe();
    }

    // Shundan past masshtabda yorliqlar yashiriladi (faqat pin ikonasi qoladi).
    var LABEL_ZOOM = $MAP_LABEL_ZOOM;

    // ---------------------------------------------------------------------
    // Belgilar: GeoJSON manbasi + MapLibre qatlamlari
    //
    // Ilgari har bir e'lon canvas USTIGA qo'yilgan HTML element (`maplibregl.Marker`) edi.
    // Xarita GPU'da WebGL bilan chiziladi, HTML element esa DOM'da joylashtiriladi —
    // WebView bu ikkisini bitta kadrda birlashtira olmaydi. Natijada ikki barmoq bilan
    // zoom qilinganda belgilar xarita ortidan "suzib" yurar, ishora tugagach joyiga
    // tushardi. Buni hech qanday sozlash bilan tuzatib bo'lmaydi — DOM va WebGL har xil
    // yo'l bilan chiziladi.
    //
    // Endi belgilar xaritaning O'Z qatlamlari: pin, narx pufagi, klaster va joylashuv
    // nuqtasi — hammasi xarita bilan bir GL kadrida chiziladi. Ular endi xaritaning
    // ajralmas qismi, shuning uchun undan siljishi jismonan mumkin emas.
    //
    // Klasterlashni ham manbaning o'zi hisoblaydi (`cluster: true`): uzoqlashtirilganda
    // yaqin e'lonlar bitta dumaloq belgiga yig'iladi, bosilganda ular ajraladigan
    // masshtabga yaqinlashadi.
    // ---------------------------------------------------------------------
    var SRC = 'offers';

    function featureCollection(list) {
      return {
        type: 'FeatureCollection',
        features: list.map(function (m) {
          return {
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [m.lng, m.lat] },
            properties: { id: m.id, label: m.label, n: m.n, hl: m.hl },
          };
        }),
      };
    }

    function ensureSource() {
      if (map.getSource(SRC)) { map.getSource(SRC).setData(featureCollection(MARKERS)); return; }
      map.addSource(SRC, {
        type: 'geojson',
        data: featureCollection(MARKERS),
        cluster: true,
        clusterRadius: 60,     // px — pin + pufak kengligiga yaqin
        clusterMaxZoom: 16,    // bundan yaqinroqda har bir e'lon alohida turadi
        // Klaster ichidagi E'LONLAR soni: bitta marker ortida bir do'konning bir nechta
        // e'loni turishi mumkin, shuning uchun markerlar emas, `n` lar qo'shiladi.
        clusterProperties: { total: ['+', ['get', 'n']] },
      });
    }

    // --- Belgi rasmlari: canvasda chiziladi, hech qanday fayl yoki base64 kerak emas ---

    /**
     * Tomchi pin: aylana + pastga qaragan o'tkir uch. Uch rasmning PASTKI MARKAZIDA
     * tugaydi, ya'ni `icon-anchor: 'bottom'` bilan u aynan e'lon koordinatasiga tushadi.
     */
    function pinImage(color) {
      var R = 2, w = 24, h = 29;                 // R — hi-dpi ekranda tiniq chiqishi uchun
      var c = document.createElement('canvas');
      c.width = w * R; c.height = h * R;
      var g = c.getContext('2d');
      g.scale(R, R);
      g.beginPath();
      // 45° va 135° — (12,29) uchidan (12,12) markazli, r=12 aylanaga urinma tegadigan
      // nuqtalar. Yoy ular orasidan TEPA tomondan o'tadi, so'ng ikkalasi uchga tutashadi.
      g.arc(12, 12, 12, Math.PI * 0.25, Math.PI * 0.75, true);
      g.lineTo(12, 29);
      g.closePath();
      g.fillStyle = color; g.fill();
      g.strokeStyle = 'rgba(0,0,0,.18)'; g.lineWidth = 1; g.stroke();
      g.beginPath(); g.arc(12, 12, 5, 0, Math.PI * 2);
      g.fillStyle = '#ffffff'; g.fill();
      return g.getImageData(0, 0, c.width, c.height);
    }

    /**
     * Narx pufagining foni — cho'ziladigan (9-slice) dumaloq to'rtburchak. Burchaklari
     * cho'zilmaydi, faqat o'rtasi: shuning uchun bitta rasm istalgan uzunlikdagi yozuvga
     * moslashadi va radius hech qachon buzilmaydi.
     */
    function pillImage() {
      var R = 2, s = 26, r = 12;
      var c = document.createElement('canvas');
      c.width = s * R; c.height = s * R;
      var g = c.getContext('2d');
      g.scale(R, R);
      var x = 0.5, y = 0.5, w = s - 1, h = s - 1;
      g.beginPath();
      g.moveTo(x + r, y);
      g.arcTo(x + w, y, x + w, y + h, r);
      g.arcTo(x + w, y + h, x, y + h, r);
      g.arcTo(x, y + h, x, y, r);
      g.arcTo(x, y, x + w, y, r);
      g.closePath();
      g.fillStyle = C_BG; g.fill();
      g.strokeStyle = C_BORDER; g.lineWidth = 1; g.stroke();
      return g.getImageData(0, 0, c.width, c.height);
    }

    /**
     * Yozuv uchun shrift nomi. Gliflar uslub serveridan keladi, ya'ni mavjud nomlar
     * uslubga bog'liq — nomni qattiq yozib qo'ysak, boshqa uslubda (masalan qorong'i
     * mavzuda) yozuv umuman chiqmay qolardi. Shuning uchun uslubning o'zidan olamiz.
     */
    function pickFont() {
      var ls = (map.getStyle() || {}).layers || [];
      var any = null;
      for (var i = 0; i < ls.length; i++) {
        var f = ls[i].layout && ls[i].layout['text-font'];
        if (!f || !f.length || typeof f[0] !== 'string') continue;
        if (!any) any = f;
        if (/Bold|Semibold|Medium/i.test(f[0])) return f;   // qalin variant afzal
      }
      return any || ['Noto Sans Regular'];
    }

    var layersReady = false;

    /** Barcha belgi qatlamlarini bir marta qo'shadi (uslub yuklangandan keyin). */
    function ensureLayers() {
      if (layersReady) return;
      layersReady = true;

      if (!map.hasImage('pin-one')) map.addImage('pin-one', pinImage(C_PIN), { pixelRatio: 2 });
      if (!map.hasImage('pin-many')) map.addImage('pin-many', pinImage(C_MULTI), { pixelRatio: 2 });
      if (!map.hasImage('pill')) {
        // `content` — yozuv joylashadigan soha, `stretchX/Y` — cho'ziladigan o'rta qism.
        // O'lchovlar rasmning xom piksellarida (26 * 2 = 52).
        map.addImage('pill', pillImage(), {
          pixelRatio: 2, content: [4, 4, 48, 48], stretchX: [[24, 28]], stretchY: [[24, 28]],
        });
      }

      var FONT = pickFont();

      // "Mening joylashuvim" — e'lon pinlaridan ostida tursin.
      map.addSource(ME_SRC, { type: 'geojson', data: meCollection() });
      map.addLayer({
        id: 'me-halo', type: 'circle', source: ME_SRC,
        paint: { 'circle-radius': 17, 'circle-color': 'rgba(37,99,235,.20)' },
      });
      map.addLayer({
        id: 'me-dot', type: 'circle', source: ME_SRC,
        paint: {
          'circle-radius': 7, 'circle-color': '#2563EB',
          'circle-stroke-width': 3, 'circle-stroke-color': '#ffffff',
        },
      });

      // Alohida e'lon: tomchi pin. Bir joyda bir nechta e'lon bo'lsa — yashil.
      // `icon-allow-overlap` — pin HECH QACHON yashirilmaydi, e'lon bor joyda pin bor.
      map.addLayer({
        id: 'offers-pin', type: 'symbol', source: SRC,
        filter: ['!', ['has', 'point_count']],
        layout: {
          'icon-image': ['case', ['>', ['get', 'n'], 1], 'pin-many', 'pin-one'],
          'icon-anchor': 'bottom',
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
      });

      // Narx pufagi. Bu qatlamda `allow-overlap` ATAYLAB yoqilmagan: ustma-ust tushadigan
      // yorliqlarni MapLibre o'zi yashiradi, shuning uchun ular "ustun" bo'lib yig'ilmaydi.
      map.addLayer({
        id: 'offers-label', type: 'symbol', source: SRC,
        filter: ['!', ['has', 'point_count']],
        minzoom: LABEL_ZOOM,
        layout: {
          'icon-image': 'pill',
          'icon-text-fit': 'both',
          'icon-text-fit-padding': [3, 9, 3, 9],
          // Bir do'konning bir nechta e'loni bo'lsa yozuvga uning soni qo'shiladi.
          'text-field': ['case', ['>', ['get', 'n'], 1],
            ['concat', ['get', 'label'], '  ', ['to-string', ['get', 'n']]],
            ['get', 'label']],
          'text-font': FONT,
          'text-size': 11.5,
          'text-anchor': 'bottom',
          'text-offset': [0, -2.9],   // em: 2.9 * 11.5 ≈ 33 px — pin uchidan tepada
          'text-padding': 3,
        },
        paint: { 'text-color': C_INK },
      });

      // Klaster: yashil doira, ichida e'lonlar soni.
      map.addLayer({
        id: 'offers-cluster', type: 'circle', source: SRC,
        filter: ['has', 'point_count'],
        paint: {
          'circle-color': C_MULTI,
          'circle-radius': ['step', ['get', 'total'], 17, 10, 20, 100, 24],
          'circle-stroke-width': 3,
          'circle-stroke-color': 'rgba(255,255,255,.92)',
        },
      });
      map.addLayer({
        id: 'offers-cluster-count', type: 'symbol', source: SRC,
        filter: ['has', 'point_count'],
        layout: {
          'text-field': ['to-string', ['get', 'total']],
          'text-font': FONT, 'text-size': 13,
          'text-allow-overlap': true, 'text-ignore-placement': true,
        },
        paint: { 'text-color': '#ffffff' },
      });

      map.on('click', 'offers-pin', tapOffer);
      map.on('click', 'offers-label', tapOffer);
      map.on('click', 'offers-cluster', tapCluster);
    }

    /** Pin yoki narx pufagi bosildi — e'lon id'si Kotlin tomonga uzatiladi. */
    function tapOffer(e) {
      if (e.features && e.features.length) postMarker(e.features[0].properties.id);
    }

    /** Klaster bosildi — u parchalanadigan masshtabga yumshoq yaqinlashamiz. */
    function tapCluster(e) {
      var f = e.features && e.features[0];
      if (!f) return;
      var at = f.geometry.coordinates;
      // Klaster aynan qaysi masshtabda parchalanishini manbaning o'zi biladi.
      map.getSource(SRC).getClusterExpansionZoom(f.properties.cluster_id).then(function (z) {
        map.easeTo({ center: at, zoom: z, duration: 400 });
      }).catch(function () {
        map.easeTo({ center: at, zoom: map.getZoom() + 2, duration: 400 });
      });
    }

    function render() {
      // Manba va qatlamlar uslub (style) yuklangandan keyingina qo'shiladi.
      if (!map.isStyleLoaded()) { map.once('load', render); return; }
      ensureSource();
      ensureLayers();
      placeMe();
    }

    // Kamera bir marta moslangach, foydalanuvchining pan/zoom holati saqlanadi.
    var didFit = false;
    var pendingFit = false;

    /** Ekranga sig'mayotgan marker bormi (yorliq balandligiga ozgina joy qoldirib). */
    function anyOffscreen() {
      var w = map.getCanvas().clientWidth, h = map.getCanvas().clientHeight;
      for (var i = 0; i < MARKERS.length; i++) {
        var p = map.project([MARKERS[i].lng, MARKERS[i].lat]);
        if (p.x < 20 || p.x > w - 20 || p.y < 60 || p.y > h - 20) return true;
      }
      return false;
    }

    /** Kamerani markerlar chegarasiga moslash (o'lcham allaqachon to'g'ri deb hisoblanadi). */
    function doFit() {
      var minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
      MARKERS.forEach(function (m) {
        if (m.lat < minLat) minLat = m.lat;
        if (m.lat > maxLat) maxLat = m.lat;
        if (m.lng < minLng) minLng = m.lng;
        if (m.lng > maxLng) maxLng = m.lng;
      });
      // Hammasi bitta nuqtada bo'lsa `fitBounds` cheksiz yaqinlashtiradi — markazlaymiz.
      if (maxLat - minLat < 0.0005 && maxLng - minLng < 0.0005) {
        map.easeTo({ center: [minLng, minLat], zoom: 15, duration: 0 });
      } else {
        map.fitBounds([[minLng, minLat], [maxLng, maxLat]], {
          // Tepada panel va kategoriya chiplari turadi — ular ostida marker qolmasin.
          padding: { top: 165, bottom: 70, left: 30, right: 30 },
          maxZoom: 15,
          duration: 0,
        });
      }
      didFit = true;
      pendingFit = false;
      render();
    }

    /**
     * Kamerani BARCHA markerlar sig'adigan qilib sozlaydi.
     *
     * MapLibre masshtabni AYNAN konteyner o'lchamiga qarab hisoblaydi, WebView o'lchami esa
     * sahifa yuklanganda hali yakuniy bo'lmaydi — erta chaqirilsa kamera butun viloyatni
     * yoki bitta ko'chani ko'rsatib qolardi. Shuning uchun fit "kutish"ga qo'yiladi va
     * o'lcham haqiqatan kelganda bajariladi (`ResizeObserver` / `idle`).
     */
    function fitToMarkers() {
      if (!MARKERS.length) return;
      // Qayta moslash FAQAT markerlar ro'yxati o'zgarganda (filtr/qidiruv) va ulardan
      // birortasi ekranga sig'mayotgan bo'lsa bo'ladi. Masshtab yoki surish kamerani
      // hech qachon qaytarmaydi — foydalanuvchining ko'rinishi saqlanadi.
      if (didFit && !anyOffscreen()) return;
      pendingFit = true;
      tryFit();
    }

    function tryFit() {
      if (!pendingFit || !MARKERS.length) return;
      map.resize();
      var c = map.getCanvas();
      if (c.clientWidth < 200 || c.clientHeight < 200) return; // o'lcham hali kelmadi
      doFit();
    }

    // O'lcham o'zgarishi — fit uchun eng ishonchli signal.
    if (window.ResizeObserver) {
      new ResizeObserver(function () { map.resize(); tryFit(); }).observe(document.getElementById('map'));
    }
    map.on('idle', tryFit);

    function addMarkers() { fitToMarkers(); render(); }
    // Ekran o'lchami o'zgarsa (klaviatura, aylantirish) — xarita ham qayta o'lchanadi.
    window.addEventListener('resize', function () { map.resize(); });

    // DIQQAT: surish/masshtab uchun HECH QANDAY hodisa kuzatilmaydi va shu ataylab.
    // Belgilar endi xaritaning o'z qatlamlari — MapLibre ularni xarita bilan bitta GL
    // kadrida chizadi. Ya'ni ularni "xarita ortidan yetkazib borish" kerak emas; JS
    // aralashsa aksincha kechikish paydo bo'lardi.

    /**
     * Kotlin tomondan filtr/qidiruv o'zgarganda chaqiriladi. MUHIM: xarita QAYTA YUKLANMAYDI —
     * shuning uchun MapLibre skripti/plitkalari qaytadan tortilmaydi va foydalanuvchining
     * pan/zoom holati saqlanadi. Ilgari har harf yozilganda butun sahifa qayta yuklanardi.
     */
    function setMarkers(list) {
      MARKERS = list;
      // Qatlamlar joyida qoladi — faqat manbadagi ma'lumot almashadi (`ensureSource`
      // ichidagi `setData`), qolganini MapLibre o'zi qayta chizadi.
      if (!map.loaded()) { map.once('load', addMarkers); return; }
      addMarkers();
    }
    if (map.loaded()) addMarkers(); else map.on('load', addMarkers);
  </script>
</body>
</html>
""".trimIndent()
}
