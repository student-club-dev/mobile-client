package dev.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scStyle
import dev.core.uikit.media.PickedVideo
import dev.core.uikit.media.VideoPreparer
import dev.core.uikit.media.videoNeedsPreparing
import dev.core.uikit.media.ScVideoPlayer
import dev.core.uikit.media.playbackUrl
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.OutgoingVideo

/**
 * Yuborishdan **oldingi** ekran: video o'ynab turadi, ostida izoh maydoni va yuborish tugmasi.
 *
 * Nega kerak: ilgari tanlangan video darrov ketardi va noto'g'ri faylni tanlash yoki izoh
 * qo'shishni unutish tuzatib bo'lmaydigan xato edi — xabarni faqat o'chirish qolardi.
 * Telegram ham aynan shu oraliq qadamni qo'yadi.
 *
 * ⚠️ Ekran yopilganda fayl **o'chirilishi** shart (`onCancel`): u ilova keshida turadi va
 * hech kim tozalamasa har bir bekor qilingan tanlov o'nlab MB bo'lib qolib ketardi.
 */
@Composable
internal fun VideoPreviewSheet(
    video: PickedVideo,
    onCancel: () -> Unit,
    onSend: (caption: String) -> Unit,
) {
    var caption by remember(video) { mutableStateOf("") }

    // ⚠️ Tizim panellarining o'lchami `Dialog` dan **tashqarida** olinadi.
    //
    // Compose Multiplatform dialog oynasiga oyna insetlarini uzatmaydi: uning ichida
    // `navigationBarsPadding()` ham, `statusBarsPadding()` ham nolga tushadi. Natijada
    // yuborish tugmasi tizimning navigatsiya paneli (orqaga tugmasi) ostida qolib ketardi.
    // Bu yerda esa kompozitsiya hali asosiy oynada, ya'ni qiymatlar to'g'ri.
    //
    // `systemBars`, `safeDrawing` emas: oxirgisi klaviaturani ham qo'shadi va u pastdagi
    // `imePadding()` bilan ikki marta hisoblanardi.
    // ⚠️ Qiymatlar SHU YERDA `Dp` ga yozib olinadi. `asPaddingValues()` **dangasa** obyekt
    // qaytaradi: uning `calculate*Padding()` i chaqirilgan joydagi oynaning insetlarini
    // o'qiydi. Chaqiruvni `Dialog { }` ichida qoldirsak, u dialog oynasiga tegishli bo'lib
    // qoladi va yana nol qaytarardi — aynan shu sabab tugma birinchi urinishda ham
    // navigatsiya paneli ostida qolgan edi.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val topInset = systemBars.calculateTopPadding()
    val bottomInset = systemBars.calculateBottomPadding()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ScVideoPlayer(
                // `playbackUrl` — `file://` ham, `content://` ham bo'lishi mumkin: galereyadan
                // tanlangan video bu yergacha keshga ko'chirilmagan (qarang [PickedVideo.path]).
                url = video.playbackUrl,
                // Takror o'ynaydi: qisqa lavhani tekshirish uchun foydalanuvchi uni bir necha
                // marta ko'radi va har safar qo'lda qayta boshlash zerikarli bo'lardi.
                loop = true,
                modifier = Modifier.fillMaxSize(),
            )

            PreviewTopBar(
                video = video,
                onCancel = onCancel,
                topInset = topInset,
                modifier = Modifier.align(Alignment.TopStart),
            )

            CaptionComposer(
                caption = caption,
                onCaption = { caption = it },
                onSend = { onSend(caption) },
                bottomInset = bottomInset,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Yopish tugmasi va videoning "pasporti" — davomiyligi hamda hajmi. */
@Composable
private fun PreviewTopBar(
    video: PickedVideo,
    onCancel: () -> Unit,
    /** Holat paneli balandligi — qarang [VideoPreviewSheet] dagi izoh. */
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(top = topInset).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            ScIcons.Close,
            "Bekor qilish",
            tint = Color.White,
            modifier = Modifier.size(22.dp).clickable(onClick = onCancel),
        )
        Column {
            ScText("Video", 15f, FontWeight.SemiBold, Color.White, maxLines = 1)
            // Hajm ataylab ko'rsatiladi: siqishdan keyin fayl necha MB bo'lganini bilish
            // mobil internetdagi foydalanuvchi uchun muhim.
            ScText(
                buildString {
                    video.durationMs?.let { append(ChatFormat.duration(it)).append(" · ") }
                    append(ChatFormat.fileSize(video.sizeBytes))
                },
                12.5f,
                FontWeight.Medium,
                Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/** Izoh maydoni va yuborish tugmasi — chatdagi kompozitorning soddalashtirilgan ko'rinishi. */
@Composable
private fun CaptionComposer(
    caption: String,
    onCaption: (String) -> Unit,
    onSend: () -> Unit,
    /** Navigatsiya paneli balandligi — qarang [VideoPreviewSheet] dagi izoh. */
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        // `imePadding` — klaviatura ochilganda maydon uning ustiga chiqadi, aks holda
        // foydalanuvchi yozayotgan matnini ko'rmasdi.
        modifier.imePadding().padding(bottom = bottomInset).fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (caption.isEmpty()) {
                    ScText("Izoh qo'shing…", 15f, FontWeight.Medium, Color.White.copy(alpha = 0.55f), maxLines = 1)
                }
                BasicTextField(
                    value = caption,
                    onValueChange = { if (it.length <= MAX_CAPTION) onCaption(it) },
                    textStyle = scStyle(15f, FontWeight.Medium, Color.White),
                    cursorBrush = SolidColor(Sc.Brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Sc.tileBrush)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ScIcons.Return, "Yuborish", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Izohning eng katta uzunligi — serverdagi bilan bir xil (`SendPayload.MAX_CAPTION`).
 *
 * Yozishning o'zida to'siladi: uzun matnni qabul qilib, keyin "yuborilmadi" deyish
 * foydalanuvchining mehnatini bekorga ketkazardi.
 */
private const val MAX_CAPTION = 1024

/**
 * Tanlangan videoni yuboriladigan ko'rinishga o'giradi.
 *
 * Siqish **shu yerda bajarilmaydi** — u faqat lambda bo'lib biriktiriladi va repozitoriy
 * uni yuklashning oldida, xabarning o'z halqasi ichida chaqiradi. Shuning uchun "Yuborish"
 * bosilishi bilan xabar ro'yxatda paydo bo'ladi va foydalanuvchi hech narsa kutmaydi.
 */
internal fun PickedVideo.toOutgoing(caption: String, preparer: VideoPreparer): OutgoingVideo =
    OutgoingVideo(
        path = path,
        fileName = fileName,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        posterBytes = posterBytes,
        caption = caption,
        needsPreparing = videoNeedsPreparing(sizeBytes),
        // ⚠️ Natijada `prepare = null`: siqilgan videoni qayta urinishda YANA siqish
        // kerak emas va u yana o'nlab soniya olardi.
        prepare = { onProgress -> preparer.prepare(this, onProgress)?.toPrepared(caption) },
    )

/**
 * Dumaloq video xabar — `VIDEO_NOTE`.
 *
 * Oddiy videodan uch farqi va uchalasi ham serverning talabi:
 * izoh **yo'q**, tayyorlovchi **kvadratga kesadi** ([rememberVideoNotePreparer]) va
 * `videoNote = true` tufayli xabar `type: VIDEO_NOTE` bo'lib ketadi.
 *
 * [OutgoingVideo.needsPreparing] doim `true`: kesish har doim kerak, hatto fayl kichik
 * bo'lsa ham — aks holda kvadrat bo'lmagan video serverga ketib `422 MEDIA_NOT_SQUARE`
 * bilan qaytardi.
 */
internal fun PickedVideo.toOutgoingVideoNote(preparer: VideoPreparer): OutgoingVideo =
    OutgoingVideo(
        path = path,
        fileName = fileName,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        posterBytes = posterBytes,
        caption = null,
        needsPreparing = true,
        videoNote = true,
        prepare = { onProgress -> preparer.prepare(this, onProgress)?.toPreparedVideoNote() },
    )

/** Kesib bo'lingan dumaloq xabar — qayta tayyorlanmaydi. */
private fun PickedVideo.toPreparedVideoNote(): OutgoingVideo =
    OutgoingVideo(
        path = path,
        fileName = fileName,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        posterBytes = posterBytes,
        caption = null,
        needsPreparing = false,
        videoNote = true,
    )

/** Siqib bo'lingan video — qayta siqilmaydi. */
private fun PickedVideo.toPrepared(caption: String): OutgoingVideo =
    OutgoingVideo(
        path = path,
        fileName = fileName,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        posterBytes = posterBytes,
        caption = caption,
        needsPreparing = false,
    )
