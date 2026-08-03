package dev.feature.calls.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScGlyph
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.feature.calls.domain.model.CallDirection
import dev.feature.calls.domain.model.CallEndReason
import dev.feature.calls.domain.model.CallMedia
import dev.feature.calls.domain.model.CallSession
import dev.feature.calls.domain.model.CallStatus

/**
 * Qo'ng'iroq ekrani — kiruvchi, chiquvchi va jonli qo'ng'iroq **bitta** ekranda.
 *
 * Uchtasini ajratmaslikning sababi: ular orasidagi o'tish (jiringlash → ulanmoqda →
 * suhbat) bir necha soniyada bo'ladi va alohida ekranlar bo'lsa har o'tishda ko'rinish
 * sakrab, avatar qayta yuklanardi. Farq faqat pastdagi tugmalar qatorida.
 *
 * Fon doim to'q: video kadr ustida oq matn o'qilishi kerak va yorug' rejimda ham
 * qo'ng'iroq ekrani "telefon" bo'lib qolsin.
 */
@Composable
fun CallScreen(viewModel: CallViewModel, onClose: () -> Unit) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Qo'ng'iroq tugadi (yoki umuman boshlanmadi) — ekranni yopamiz. Terminal holat
    // `CallSessionManager` da bir necha soniya ushlab turiladi, ya'ni foydalanuvchi
    // «Rad etildi» yozuvini ko'rib ulguradi.
    LaunchedEffect(session) {
        if (session == null) onClose()
    }

    val current = session ?: return

    Box(Modifier.fillMaxSize().background(CallBackground)) {
        // Suhbatdoshning videosi — butun ekran; ostida avatar turadi, ya'ni oqim
        // kelmaguncha ekran qora bo'lib qolmaydi.
        if (current.remoteVideo) {
            CallVideo(local = false, mirror = false, modifier = Modifier.fillMaxSize())
        }

        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Sc.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            if (!current.remoteVideo) {
                ScAvatar(
                    name = current.peer.fullName.orEmpty().ifBlank { current.peer.username.orEmpty() },
                    size = 128.dp,
                    avatarUrl = current.peer.avatarUrl,
                    background = Color.White.copy(alpha = 0.12f),
                    initialColor = Color.White,
                )
                Spacer(Modifier.height(20.dp))
            }
            ScText(
                text = current.peer.fullName.orEmpty().ifBlank { current.peer.username.orEmpty() },
                size = 24f,
                weight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            ScText(
                text = statusText(current, timer),
                size = 15f,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                ScText(it, size = 14f, color = Sc.Danger, maxLines = 2)
            }

            Spacer(Modifier.weight(1f))

            CallControls(
                session = current,
                onToggleMic = viewModel::toggleMic,
                onToggleCamera = viewModel::toggleCamera,
                onSwitchCamera = viewModel::switchCamera,
                onToggleSpeaker = viewModel::toggleSpeaker,
                onAccept = viewModel::accept,
                onDecline = viewModel::decline,
                onHangUp = viewModel::hangUp,
            )
            Spacer(Modifier.height(32.dp))
        }

        // O'z kameramiz — o'ng yuqori burchakda kichik oyna. Ko'zguga aylantiriladi:
        // odam o'zini ko'zgudagidek ko'rishni kutadi (old kamerada).
        if (current.cameraEnabled) {
            CallVideo(
                local = true,
                mirror = current.frontCamera,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .size(width = 108.dp, height = 152.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
    }
}

/**
 * Holat qatori.
 *
 * `MISSED`/`DECLINED`/`CANCELED` matnlari **terminal holatda** ko'rinadi — ekran shu
 * yozuv bilan bir necha soniya turadi, so'ng yopiladi.
 */
private fun statusText(session: CallSession, timer: String): String = when (session.status) {
    CallStatus.RINGING -> if (session.direction == CallDirection.OUTGOING) {
        "Jiringlamoqda…"
    } else {
        if (session.media == CallMedia.VIDEO) "Video qo'ng'iroq" else "Ovozli qo'ng'iroq"
    }

    CallStatus.CONNECTING -> "Ulanmoqda…"
    CallStatus.ACTIVE -> timer.ifEmpty { "Ulandi" }
    CallStatus.MISSED -> "Javobsiz qo'ng'iroq"
    CallStatus.DECLINED -> if (session.endReason == CallEndReason.BUSY) "Band" else "Rad etildi"
    CallStatus.CANCELED -> "Bekor qilindi"
    CallStatus.FAILED -> "Aloqa uzildi"
    CallStatus.ENDED -> "Qo'ng'iroq tugadi"
}

/**
 * Pastdagi tugmalar.
 *
 * Kiruvchi jiringlashda **ikkita** tugma (qabul/rad), qolgan hamma holatda mikrofon,
 * kamera va tugatish.
 */
@Composable
private fun CallControls(
    session: CallSession,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
) {
    val incomingRinging = session.status == CallStatus.RINGING &&
        session.direction == CallDirection.INCOMING

    if (incomingRinging) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallButton(ScIcons.CallEnd, "Rad etish", DangerRed, onDecline)
            CallButton(ScIcons.PhoneCall, "Javob berish", AcceptGreen, onAccept)
        }
        return
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallButton(
                icon = if (session.micEnabled) ScIcons.Mic else ScIcons.MicOff,
                label = if (session.micEnabled) "Mikrofon" else "O'chirilgan",
                background = toggleColor(session.micEnabled),
                onClick = onToggleMic,
                size = 56.dp,
            )
            CallButton(
                icon = if (session.cameraEnabled) ScIcons.Video else ScIcons.VideoOff,
                label = "Kamera",
                background = toggleColor(session.cameraEnabled),
                onClick = onToggleCamera,
                size = 56.dp,
            )
            CallButton(
                icon = ScIcons.Speaker,
                label = "Karnay",
                background = toggleColor(session.speakerOn),
                onClick = onToggleSpeaker,
                size = 56.dp,
            )
            if (session.cameraEnabled) {
                CallButton(
                    icon = ScIcons.CameraSwitch,
                    label = "Almashtirish",
                    background = toggleColor(false),
                    onClick = onSwitchCamera,
                    size = 56.dp,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        CallButton(ScIcons.CallEnd, "Tugatish", DangerRed, onHangUp)
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 68.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(percent = 50)).background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            ScGlyph(icon, size * ICON_RATIO)
        }
        Spacer(Modifier.height(8.dp))
        ScText(label, size = 12f, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** Yoqilgan tugma — oq shaffof, o'chirilgan — deyarli ko'rinmas. */
private fun toggleColor(enabled: Boolean): Color =
    if (enabled) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)

/** Qo'ng'iroq ekrani doim to'q — video kadr ustidagi oq matn o'qilishi uchun. */
private val CallBackground = Color(0xFF0B1622)
private val DangerRed = Color(0xFFE5484D)
private val AcceptGreen = Color(0xFF2FBF71)

private const val ICON_RATIO = 0.42f
