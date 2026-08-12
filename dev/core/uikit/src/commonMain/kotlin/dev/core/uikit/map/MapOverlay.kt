package dev.core.uikit.map

import dev.core.uikit.components.scTopInset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.Sc
import dev.core.uikit.locale.uiStrings

/**
 * Ro'yxatdan xaritaga o'tish tugmasi.
 *
 * Filtr bor har bir ekranda shu tugma turadi — foydalanuvchi qayerda filtrlagan bo'lsa,
 * o'sha natijani xaritada ham ko'ra oladi. Ko'rinishi hamma joyda bir xil bo'lishi uchun
 * bu yerda, ekranlarda takrorlanmaydi.
 */
@Composable
fun MapLinkButton(
    palette: AppPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = uiStrings().viewOnMap,
) {
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(13.dp))
            .background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🗺", style = TextStyle(fontSize = 16.sp))
        Text(
            label,
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.ink),
            modifier = Modifier.weight(1f),
        )
        Icon(AppIcons.ChevronRight, null, tint = palette.inkFaint, modifier = Modifier.size(16.dp))
    }
}

/**
 * To'liq ekranli xarita ko'rinishi — [OffersMap] ustiga suzuvchi panel, natija soni va
 * (marker bosilganda) tafsilot kartasi.
 *
 * Har bir ekran o'z e'lonini o'zicha ko'rsatadi, lekin xaritaning o'zi, orqaga tugmasi,
 * "N ta e'lon xaritada" belgisi va joylashuvi hamma joyda bir xil bo'lsin — shuning uchun
 * karkas shu yerda, o'zgaradigan qismlari esa slot sifatida beriladi.
 *
 * @param markers ekrandagi joriy (filtrlangan) natija — xarita ro'yxat bilan bir xil bo'lishi shart
 * @param topBarExtras orqaga tugmasidan keyingi qism (odatda qidiruv maydoni + Filter tugmasi)
 * @param belowTopBar panel ostidagi qator (odatda kategoriya chiplari); natija soni undan keyin chiziladi
 * @param detail marker bosilganda pastda chiqadigan karta; bo'sh qoldirilsa hech narsa chiqmaydi
 */
@Composable
fun OffersMapOverlay(
    markers: List<OfferMarker>,
    palette: AppPalette,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    center: MapPoint = markersCenter(markers),
    userLocation: MapPoint? = rememberUserLocation(),
    bottomInset: Int = 16,
    onMarkerTap: (String) -> Unit = {},
    topBarExtras: @Composable RowScope.() -> Unit = {},
    belowTopBar: @Composable () -> Unit = {},
    detail: @Composable BoxScope.() -> Unit = {},
) {
    // Xarita to'liq ekran (full-bleed) — ustki panel ustida suzadi, kulrang band yo'q.
    //
    // Fon RANGI kerak: xarita sahifasi fon oqimida yig'ilgani uchun ([rememberOffersMapHtml])
    // birinchi kadrlarda WebView hali yo'q — fonsiz o'sha lahzada ostidagi ro'yxat ko'rinib
    // qolardi, go'yo ekran yarim ochilgandek.
    Box(modifier.fillMaxSize().background(Sc.Bg)) {
        OffersMap(
            markers = markers,
            center = center,
            dark = palette.dark,
            userLocation = userLocation,
            bottomInset = bottomInset,
            modifier = Modifier.fillMaxSize(),
            onMarkerTap = onMarkerTap,
        )

        // Panel, chiplar va natija soni — bitta ustunda: chiplar qo'shilganda son
        // ularning ostiga suriladi (ilgari qat'iy `top = 108.dp` edi va ustma-ust tushardi).
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).scTopInset(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(palette.glass)
                        .border(1.dp, palette.border, RoundedCornerShape(13.dp)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.ArrowLeft, uiStrings().close, tint = palette.ink, modifier = Modifier.size(18.dp)) }
                topBarExtras()
            }

            belowTopBar()

            // Natija soni — filtr xaritaga ham tegishli ekanini ko'rsatadi.
            Box(
                Modifier.padding(top = 10.dp).clip(RoundedCornerShape(11.dp))
                    // `palette.ink` EMAS: to'q rejimda u oqarib ketadi va ustidagi oq
                    // yozuv ko'rinmay qolardi. `InkSurface` ikkala rejimda ham to'q.
                    .background(Sc.InkSurface.copy(alpha = 0.82f)).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    // Marker soni EMAS, e'lonlar soni: bitta marker ortida bir biznesning
                    // bir nechta e'loni turishi mumkin.
                    if (markers.isEmpty()) uiStrings().noListingsForFilters
                    else "${markers.sumOf { it.count }} ta e'lon xaritada",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = Color.White),
                )
            }
        }

        detail()
    }
}

/**
 * Markerlarni qamrab oladigan markaz. Bo'sh bo'lsa Toshkent — foydalanuvchi bo'sh xaritada
 * okean o'rtasida qolmasin.
 */
fun markersCenter(markers: List<OfferMarker>): MapPoint =
    if (markers.isEmpty()) DefaultMapCenter
    else MapPoint(markers.map { it.lat }.average(), markers.map { it.lng }.average())
