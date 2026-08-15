package dev.core.uikit.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.core.uikit.components.ScOverlay
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette

/**
 * Bitta manzilni xaritada ko'rsatuvchi — **ilovadagi barcha e'lonlar uchun yagona yo'l**.
 *
 * Har bir e'londa manzil bor: kartada "📍 Chilonzor", tafsilotda esa filiallar ro'yxati.
 * Ilgari ularning hech biri bosilmasdi — matn shunchaki yozuv edi va foydalanuvchi "bu
 * qayerda ekan?" degan savol bilan qolardi. Endi manzil bosiladi va o'sha nuqta xaritada
 * ochiladi.
 *
 * Karkas bitta nusxa o'rnatadi ([ScLocationViewerHost]) va u xaritani o'zi chizadi;
 * ekranlar faqat [rememberShowOnMap] orqali "shu nuqtani ko'rsat" deydi.
 */
@Stable
class ScLocationViewer internal constructor() {

    internal var target: Target? by mutableStateOf(null)
        private set

    /**
     * Nuqtani xaritada ochadi. Koordinata yaroqsiz bo'lsa (`0,0` — server manzilni
     * geokodlamagan) **hech nima qilinmaydi**: bo'sh okean o'rtasidagi xarita javob emas.
     */
    fun show(label: String, lat: Double, lng: Double) {
        if (!hasLocation(lat, lng)) return
        target = Target(label, MapPoint(lat, lng))
    }

    fun hide() {
        target = null
    }

    @Immutable
    internal data class Target(val label: String, val point: MapPoint)
}

/**
 * Joriy manzil ko'ruvchi — [ScLocationViewerHost] o'rnatadi.
 *
 * CompositionLocal, parametr EMAS: manzil qatori kartochkalarning eng chuqurida chiziladi
 * (`MetaLine`, `OfferBanner`, filial qatori...) va uni har bir oraliq composable orqali
 * uzatish o'nlab imzoni "ko'ruvchi" parametri bilan ifloslantirardi. Bu — `LocalScOverlayHost`
 * bilan bir xil yondashuv: ko'ndalang xizmat daraxt bo'ylab tarqaladi.
 *
 * `null` — karkasdan tashqarida (preview, test): manzil shunchaki bosilmaydigan yozuv bo'ladi.
 */
val LocalScLocationViewer = staticCompositionLocalOf<ScLocationViewer?> { null }

/**
 * Manzil xaritasini butun karkas uchun bir marta o'rnatadi.
 *
 * ⚠️ Xarita karkasning ustki qatlamiga beriladi ([ScOverlay]), ya'ni pastki navigatsiya
 * panelidan ham yuqorida chiziladi. Shuning uchun bu host `LocalScOverlayHost` ICHIDA
 * bo'lishi kerak.
 */
@Composable
fun ScLocationViewerHost(palette: AppPalette = appPalette, content: @Composable () -> Unit) {
    val viewer = remember { ScLocationViewer() }

    CompositionLocalProvider(LocalScLocationViewer provides viewer) { content() }

    viewer.target?.let { target ->
        ScOverlay(LOCATION_SLOT) {
            OffersMapOverlay(
                markers = listOf(
                    OfferMarker(
                        id = MARKER_ID,
                        lat = target.point.lat,
                        lng = target.point.lng,
                        label = target.label,
                        colorHex = MARKER_COLOR,
                        highlight = true,
                    ),
                ),
                palette = palette,
                onClose = viewer::hide,
                center = target.point,
            )
        }
    }
}

/**
 * Manzil bosilganda xaritani ochadigan ishlovchi — koordinata yaroqsiz bo'lsa yoki ko'ruvchi
 * o'rnatilmagan bo'lsa `null` (ya'ni qator bosilmaydi va tagi chizilmaydi).
 *
 * Chaqiruv joyida `if` yozish shart emas: natijani to'g'ridan-to'g'ri [ScLocationLabel] ga
 * berish kifoya.
 */
@Composable
fun rememberShowOnMap(label: String, lat: Double, lng: Double): (() -> Unit)? {
    val viewer = LocalScLocationViewer.current ?: return null
    if (!hasLocation(lat, lng)) return null
    return { viewer.show(label, lat, lng) }
}

/**
 * "📍 Chilonzor" qatori — [onShowOnMap] berilgan bo'lsa **bosiladi** va tagi chiziladi.
 *
 * Tag chizig'i ataylab: bu matn kartaning rasmi ustida ham, oddiy fonda ham uchraydi va
 * rang bilan "bosiladi" deb aytib bo'lmaydi (rasm ustida har qanday rang yo'qoladi).
 * Chiziq esa har qanday fonda ko'rinadi va havolaning universal belgisi.
 */
@Composable
fun ScLocationLabel(
    text: String,
    size: Float,
    color: Color,
    onShowOnMap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.SemiBold,
) {
    val clickable = if (onShowOnMap == null) {
        modifier
    } else {
        // Rippl yo'q: qator kartaning ichida turadi va to'lqin butun kartani "bosilgandek"
        // ko'rsatib, e'lonni ochish bilan chalkashtirardi.
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onShowOnMap,
        )
    }
    ScText(
        text = "$PIN $text",
        size = size,
        weight = weight,
        color = color,
        modifier = clickable,
        maxLines = 1,
        decoration = if (onShowOnMap == null) null else TextDecoration.Underline,
    )
}

/** Koordinata haqiqiymi — `0,0` "noma'lum" degani, xaritaga chiqarilmaydi. */
fun hasLocation(lat: Double, lng: Double): Boolean = lat != 0.0 && lng != 0.0

private const val PIN = "📍"

/** Yagona nuqtaning markeri — brend ko'ki (xaritada boshqa nuqta yo'q). */
private const val MARKER_COLOR = "#00AEEF"
private const val MARKER_ID = "point"

/** Ustki qatlamdagi slot kaliti — karkasda bitta manzil xaritasi bo'ladi. */
private const val LOCATION_SLOT = "sc-location-map"
