package dev.feature.calls.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Video oqimini chizadi.
 *
 * Platformaga xos: Android'da `SurfaceViewRenderer`, iOS'da `RTCMTLVideoView`. Compose'ning
 * o'zi WebRTC kadrini chiza olmaydi — trek native ko'rinishga ulanadi.
 *
 * [local] `true` — o'z kameramiz (kichik oyna, **ko'zguga aylantirilgan**: odam o'zini
 * ko'zgudagidek ko'rishni kutadi), `false` — suhbatdoshning oqimi (butun ekran).
 *
 * Trek hali yo'q bo'lsa hech narsa chizilmaydi — chaqiruvchi ostiga avatar qo'yadi.
 */
@Composable
expect fun CallVideo(local: Boolean, mirror: Boolean, modifier: Modifier)
