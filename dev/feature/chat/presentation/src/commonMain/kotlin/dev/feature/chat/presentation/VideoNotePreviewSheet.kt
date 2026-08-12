package dev.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.media.MAX_VIDEO_NOTE_MS
import dev.core.uikit.media.PickedVideo
import dev.core.uikit.media.ScVideoPlayer
import dev.core.uikit.media.playbackUrl
import dev.core.uikit.theme.Sc
import dev.core.uikit.locale.uiStrings

/**
 * Yozib olingan dumaloq video xabarni **yuborishdan oldin** ko'rsatadi.
 *
 * Nega kerak: yozib olish tizim kamerasi bilan ketadi va u to'rtburchak kadr beradi —
 * foydalanuvchi doiraga nima tushishini **ko'rmaydi**. Bu yerda video aynan yuboriladigan
 * shaklda (aylana, markazidan kesilgan) chiziladi, ya'ni «boshim kesilib qolibdi» degan
 * holat yuborilgandan **keyin** emas, oldin ko'rinadi.
 *
 * Izoh maydoni **ataylab yo'q**: server dumaloq xabarga matn qabul qilmaydi.
 *
 * ⚠️ Bu yerda ko'rsatilayotgani hali **kesilmagan** fayl — doira faqat kadrni qirqib
 * ko'rsatadi. Haqiqiy kesish yuborilgandan keyin, yuklash halqasi ichida bo'ladi
 * (`rememberVideoNotePreparer`), Telegramdagi kabi.
 */
@Composable
internal fun VideoNotePreviewSheet(
    video: PickedVideo,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = Sc.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(PREVIEW_SIZE).clip(CircleShape).background(Sc.Chip)) {
                    ScVideoPlayer(
                        url = video.playbackUrl,
                        modifier = Modifier.fillMaxSize(),
                        loop = true,
                        // Boshqaruv paneli doirani kesib o'tardi; xabar qisqa, uni
                        // to'xtatib turishning ma'nosi ham yo'q.
                        showControls = false,
                        // ⚠️ `false` — kadr doirani TO'LDIRADI (markazidan kesiladi), ya'ni
                        // ekranda yuboriladigan natijaning o'zi ko'rinadi. `true` bo'lsa
                        // butun 16:9 kadr doiraga sig'dirilib, chetlarda qora joy qolardi
                        // va foydalanuvchi kesishni umuman ko'rmasdi.
                        contentScaleFit = false,
                    )
                }
                Spacer(Modifier.height(18.dp))
                ScText(
                    text = tooLongOrNull(video) ?: chatStrings().videoNote,
                    size = 14f,
                    weight = FontWeight.SemiBold,
                    color = if (tooLongOrNull(video) != null) Sc.Danger else Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(28.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundAction(ScIcons.Close, uiStrings().cancel, Color.White.copy(alpha = 0.14f), onCancel)
                    RoundAction(ScIcons.Return, chatStrings().send, Sc.Brand, onSend)
                }
            }
        }
    }
}

/**
 * 60 soniyadan uzun bo'lsa ogohlantiramiz.
 *
 * Yuborish **to'sib qo'yilmaydi**: tayyorlovchi videoni baribir 60 soniyagacha qirqadi
 * (`MediaItem.ClippingConfiguration`), ya'ni xabar ketadi — foydalanuvchi faqat nima
 * bo'lishini oldindan bilib turadi.
 */
private fun tooLongOrNull(video: PickedVideo): String? {
    val duration = video.durationMs ?: return null
    if (duration <= MAX_VIDEO_NOTE_MS) return null
    return chatStringsNow().videoNoteTrimmed
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        ScText(label, 12f, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** Ko'rish doirasining diametri — chatdagi pufakchadan biroz kattaroq. */
private val PREVIEW_SIZE = 260.dp
