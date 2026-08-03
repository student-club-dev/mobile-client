package dev.feature.calls.data.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * Video treklarni **media qatlamidan UI'ga** o'tkazadigan yagona nuqta.
 *
 * Nega global obyekt: `VideoTrack` va `EglBase` — `org.webrtc` turlari, ya'ni ular
 * `commonMain` ga chiqa olmaydi va `CallSession` ichida sayohat qila olmaydi. Ularni
 * Compose'gacha olib borishning boshqa yo'li — butun qo'ng'iroq holatini Android'ga
 * ko'chirish bo'lardi, bu esa holat mashinasini ikki marta yozish demak.
 *
 * Bir vaqtda faqat bitta qo'ng'iroq bo'ladi ([dev.feature.calls.domain.repository.CallController]),
 * shuning uchun bitta nusxa yetarli. [clear] qo'ng'iroq yopilganda chaqiriladi.
 */
object WebRtcVideoBus {

    /** Umumiy EGL konteksti — `SurfaceViewRenderer` ni shu bilan ishga tushirish kerak. */
    var eglBase: EglBase? = null
        internal set

    private val _local = MutableStateFlow<VideoTrack?>(null)
    private val _remote = MutableStateFlow<VideoTrack?>(null)

    /** O'z kameramiz — kichik oynada. */
    val local: StateFlow<VideoTrack?> = _local.asStateFlow()

    /** Suhbatdoshning oqimi — butun ekran. */
    val remote: StateFlow<VideoTrack?> = _remote.asStateFlow()

    internal fun setLocal(track: VideoTrack?) {
        _local.value = track
    }

    internal fun setRemote(track: VideoTrack?) {
        _remote.value = track
    }

    internal fun clear() {
        _local.value = null
        _remote.value = null
    }
}
