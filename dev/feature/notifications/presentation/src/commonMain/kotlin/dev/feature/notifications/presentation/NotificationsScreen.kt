package dev.feature.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.theme.Sc
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.model.NotificationType
import androidx.compose.material3.Text
import org.koin.compose.viewmodel.koinViewModel

/**
 * Bildirishnomalar ekrani. Gradient topbar (aylana `‹` orqaga tugma) + rangli
 * plitkali kartalar; o'qilmagan bildirishnomada o'ng chetda ko'k nuqta.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, vm: NotificationsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader(horizontalPadding = 18.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                ScHeaderTitle("Bildirishnomalar", modifier = Modifier.weight(1f))
                if (state.unreadCount > 0) {
                    Text(
                        "Hammasi o'qildi",
                        style = scStyle(12.5f, FontWeight.Bold, Color.White),
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { vm.markAllRead() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (state.items.isEmpty()) {
            EmptyNotifications()
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = Sc.ScreenPadding, end = Sc.ScreenPadding, top = 20.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                items(state.items, key = { it.id }) { n -> NotificationCard(n) { vm.markRead(n.id) } }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification, onClick: () -> Unit) {
    val (icon, tint, accent) = n.type.visual()
    Row(
        Modifier.fillMaxWidth().scCard(radius = 22.dp, onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ScIconTile(tint, size = 48.dp, radius = 15.dp) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            ScText(n.title, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            ScText(n.body, 13f, FontWeight.Medium, Sc.InkSoft, lineHeight = 19f)
            Spacer(Modifier.height(7.dp))
            ScText(n.timeLabel, 12f, FontWeight.SemiBold, Sc.MutedLight)
        }
        if (!n.read) {
            Box(
                Modifier.padding(top = 6.dp).size(8.dp)
                    .background(Sc.Brand, RoundedCornerShape(percent = 50)),
            )
        }
    }
}

@Composable
private fun EmptyNotifications() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScIconTile(Sc.TintBlue, size = 96.dp, radius = 30.dp) {
                Icon(ScIcons.Bell, null, tint = Sc.Brand, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(12.dp))
            ScText("Hozircha bo'sh", 19f, FontWeight.ExtraBold, Sc.Ink)
            Text(
                "Yangi e'lonlar, ish takliflari va xabarlar shu yerda ko'rinadi.",
                style = scStyle(14f, FontWeight.Medium, Sc.Muted, lineHeight = 21f)
                    .copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

/** Bildirishnoma turi → ikona, plitka foni va accent rangi (dizayn palitrasidan). */
@Composable
private fun NotificationType.visual(): Triple<ImageVector, Color, Color> = when (this) {
    NotificationType.JOB -> Triple(ScIcons.Briefcase, Sc.TintBlue, Sc.Brand)
    NotificationType.DISCOUNT -> Triple(ScIcons.DiscountTag, Sc.TintOrange, Sc.Orange)
    NotificationType.AD -> Triple(ScIcons.FileText, Sc.TintGreenDeep, Sc.Success)
    NotificationType.CHAT -> Triple(ScIcons.MessageLines, Sc.TintViolet, Sc.Violet)
    NotificationType.SYSTEM -> Triple(ScIcons.Bell, Sc.TintPink, Sc.Pink)
}
