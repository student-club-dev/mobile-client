package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Pleyerning **tashqi boshqaruvi** — o'z boshqaruv paneli chizadigan ekranlar uchun.
 *
 * Nega kerak: tizimning tayyor paneli har platformada boshqacha ko'rinadi va uni loyihaning
 * dizayniga bo'yab bo'lmaydi. Chat videosining to'liq ekrani (Telegramdagidek: tepada
 * yuboruvchi, pastda izoh va ko'chirgich) shu holat orqali pleyerni boshqaradi.
 *
 * Holat pleyerdan **o'qiydi** ham ([positionMs], [durationMs], [isPlaying]) — ya'ni
 * ko'chirgich haqiqiy ijro bo'yicha yuradi, taymer bo'yicha emas: buferlanish paytida u
 * ham to'xtab turadi.
 */
@Stable
class ScVideoState {

    /** Joriy pozitsiya (ms). Ko'chirilganda darhol yangilanadi — chiziq "orqada" qolmasin. */
    var positionMs: Long by mutableStateOf(0L)
        internal set

    /** Umumiy davomiylik (ms). Metadata hali o'qilmagan bo'lsa `0`. */
    var durationMs: Long by mutableStateOf(0L)
        internal set

    /** Haqiqatan o'ynayaptimi — buferlanish/pauza ham shu yerda aks etadi. */
    var isPlaying: Boolean by mutableStateOf(false)
        internal set

    /** Bajarilishi kutilayotgan buyruqlar. `null` — buyruq yo'q. */
    internal var playRequest: Boolean? by mutableStateOf(null)
    internal var seekRequest: Long? by mutableStateOf(null)

    fun play() { playRequest = true }

    fun pause() { playRequest = false }

    fun togglePlay() { playRequest = !isPlaying }

    /**
     * Berilgan nuqtaga o'tadi. [positionMs] **darhol** yangilanadi: pleyerning javobini
     * kutsak, barmoq qo'yib yuborilgandan keyin ko'chirgich bir lahza orqaga sakrardi.
     */
    fun seekTo(ms: Long) {
        positionMs = ms.coerceAtLeast(0L)
        seekRequest = positionMs
    }

    internal fun consumePlay() { playRequest = null }

    internal fun consumeSeek() { seekRequest = null }
}

@Composable
fun rememberScVideoState(): ScVideoState = remember { ScVideoState() }

/**
 * Umumiy video pleyer (Android: ExoPlayer + `PlayerView`, iOS: `AVPlayer` + `AVPlayerLayer`).
 *
 * Har platformada tizimning **o'z** pleyeri ishlatiladi — Compose ustida o'z dekoderimizni
 * qurish o'rniga: apparat dekodlash, HLS/progressive, ovoz fokusi va energiya sarfi shunda
 * to'g'ri chiqadi. Shu sababli komponent `expect/actual` — umumiy qatlamda faqat **kontrakt**
 * turadi, chaqiruvchi ekranlar (chat videosi, GIF, e'lon galereyasi) esa bitta API ko'radi.
 *
 * ⚠️ Pleyer bitta `url` ga bog'liq **og'ir** resurs: dekoder, bufer va tarmoq ulanishi.
 * Shuning uchun u kompozitsiyada `remember` qilinadi va ekran yopilganda majburan
 * bo'shatiladi. Ro'yxat ichida ishlatganda faqat ekranda **ko'rinib turgan** elementga
 * qo'yish kerak — aks holda o'nlab dekoder bir vaqtda ochilib qurilma qotadi.
 *
 * @param url To'liq media havolasi (http/https yoki lokal fayl).
 * @param headers Ilovaning o'z serveridagi media token talab qiladi — chaqiruvchi Bearer
 *   sarlavhasini shu yerda beradi. ⚠️ Havolaning o'ziga token yozib qo'yish mumkin emas:
 *   u loglarga va keshga tushadi, shuning uchun avtorizatsiya faqat sarlavhada boradi.
 *   Xarita/kesh kalitiga aylanmasin uchun bu qiymat `remember` kalitida ham qatnashadi —
 *   ya'ni sarlavha o'zgarsa pleyer qaytadan quriladi.
 * @param autoPlay Ochilishi bilan o'ynasinmi. Foydalanuvchi qo'lda to'xtatgandan keyin
 *   qayta kompozitsiya uni **qayta ishga tushirmaydi** (faqat qiymatning o'zgarishi ta'sir qiladi).
 * @param loop Cheksiz takror — GIF/stiker uchun. Bunda [onEnded] baribir har aylanishda chaqiriladi.
 * @param muted Ovozsiz — lentada avtomatik o'ynaydigan videolar uchun.
 * @param showControls Tizimning boshqaruv paneli (play/pause, ko'chirgich) ko'rsatilsinmi.
 * @param contentScaleFit `true` — video butunlay sig'adi (chetlarda bo'sh joy qolishi mumkin),
 *   `false` — maydonni to'ldiradi va ortiqchasi qirqiladi.
 * @param onEnded Video oxiriga yetganda chaqiriladi (stories'da keyingisiga o'tish uchun).
 * @param state Tashqi boshqaruv ([ScVideoState]) — o'z boshqaruv panelini chizadigan
 *   ekranlar uchun. Berilsa, `showControls = false` qilinadi va panel chaqiruvchida
 *   chiziladi. Berilmasa hech narsa o'zgarmaydi.
 * @param onProgress Ijro pozitsiyasi va umumiy davomiylik (millisekundda), ~10 marta/sek.
 *   Story'ning tepasidagi chiziq **aynan shu bo'yicha** to'ladi: taymer bilan chizilsa,
 *   video buferlanayotganda chiziq oldinga ketib qolardi. Davomiylik hali noma'lum bo'lsa
 *   `0` keladi (metadata o'qilmagan).
 */
@Composable
expect fun ScVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    /** Ilovaning o'z serveridagi media token talab qiladi — chaqiruvchi Bearer sarlavhasini shu yerda beradi. */
    headers: Map<String, String> = emptyMap(),
    autoPlay: Boolean = true,
    loop: Boolean = false,
    muted: Boolean = false,
    showControls: Boolean = true,
    contentScaleFit: Boolean = true,
    state: ScVideoState? = null,
    onEnded: () -> Unit = {},
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
)
