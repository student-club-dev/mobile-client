package dev.feature.calls.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.feature.calls.data.engine.WebRtcVideoBus
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * `SurfaceViewRenderer` — WebRTC kadrini chizadigan yagona ko'rinish.
 *
 * Renderer `remember` da quriladi, `AndroidView` esa uni shunchaki joylashtiradi. Buning
 * sababi: trekni ko'rinishga **tashqaridan** ulash kerak, `AndroidView` ning `factory` si
 * esa yaratilgan obyektni chaqiruvchiga qaytarmaydi.
 *
 * ⚠️ Trek almashganda eskisidan **uzish shart**: `SurfaceViewRenderer` bir vaqtda bitta
 * manbani chizadi va uzilmagan eski trek uni ushlab qolib, ekran qora bo'lib qolardi.
 * Shu sabab ulanish `DisposableEffect(track)` da.
 */
@Composable
actual fun CallVideo(local: Boolean, mirror: Boolean, modifier: Modifier) {
    val track by (if (local) WebRtcVideoBus.local else WebRtcVideoBus.remote).collectAsState()
    val eglBase = WebRtcVideoBus.eglBase ?: return
    val context = LocalContext.current

    val renderer = remember(eglBase) {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setScalingType(
                if (local) {
                    // O'z oynamiz kichik — kadr to'liq to'ldirilsin, chetlari kesilsa ham.
                    RendererCommon.ScalingType.SCALE_ASPECT_FILL
                } else {
                    RendererCommon.ScalingType.SCALE_ASPECT_FIT
                },
            )
            setEnableHardwareScaler(true)
            // Kichik oyna kattaning USTIDA turadi.
            setZOrderMediaOverlay(local)
        }
    }

    DisposableEffect(renderer) {
        // ⚠️ MAJBURIY: `release()` chaqirilmasa EGL sirti va u bilan birga GPU buferi
        // butun jarayon bo'yicha band qolib ketadi.
        onDispose { renderer.release() }
    }

    DisposableEffect(renderer, track) {
        val current = track
        current?.addSink(renderer)
        onDispose { current?.let { runCatching { it.removeSink(renderer) } } }
    }

    // Trek hali kelmagan — chaqiruvchi ostidagi avatar ko'rinib tursin.
    if (track == null) return

    AndroidView(
        modifier = modifier,
        factory = { renderer },
        update = { it.setMirror(mirror) },
    )
}
