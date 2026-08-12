package dev.feature.notifications.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScEmptyStateBox
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScShimmerList
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.ScPullRefresh
import dev.core.uikit.theme.Sc
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.model.NotificationTarget
import dev.feature.notifications.domain.model.NotificationType
import org.koin.compose.viewmodel.koinViewModel
import dev.core.uikit.locale.uiStrings

/**
 * Bildirishnomalar ekrani. Gradient topbar (aylana `‹` orqaga tugma) + rangli plitkali
 * kartalar; o'qilmaganida chap chetda ko'k nuqta va och fon.
 *
 * Ro'yxat serverdan keladi (`GET /v1/notifications`), local baza esa kesh: oflaynda ekran
 * oxirgi ko'rilgan ro'yxat bilan to'ladi.
 *
 * [onOpenTarget] — bildirishnoma bosilganda ochiladigan ekran. Route xaritasi bu yerda EMAS,
 * `StudentShell` da: navigatsiya grafi feature moduliga ko'rinmaydi.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenTarget: (NotificationTarget) -> Unit,
    vm: NotificationsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader(horizontalPadding = 18.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = uiStrings().back)
                // Sarlavha qatorda YOLG'IZ: ilgari yonida "Hammasi o'qildi" chipi turardi va
                // "Bildirishnomalar" uch nuqta bilan kesilib qolardi. Tugma endi pastda.
                ScHeaderTitle(notificationsStrings().title)
            }
            Spacer(Modifier.height(10.dp))
            ScHeaderSubtitle(state.subtitle())
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.items.isNotEmpty() -> NotificationList(state, vm, onOpenTarget)
                state.loading -> ScShimmerList(
                    rows = 6,
                    rowHeight = 64.dp,
                    modifier = Modifier.padding(horizontal = Sc.ScreenPadding, vertical = 20.dp),
                )
                state.error != null -> LoadFailed(state.error.orEmpty(), vm::refresh)
                // Bildirishnoma yo'q — plitka chizilmaydi. Sarlavha ostidagi izoh
                // ("Yangiliklar shu yerda to'planadi") buni allaqachon aytib turibdi.
                else -> Unit
            }

            FloatingMarkAllRead(state.unreadCount, vm::markAllRead)
        }
    }
}

/**
 * Tugma ro'yxat USTIDA suzadi (Column'ning oddiy bolasi emas): ro'yxat uning ostidan
 * o'tib ketadi va oxirgi karta gradient ostida yo'qolmasligi uchun ro'yxatga pastdan
 * qo'shimcha bo'shliq berilgan.
 *
 * Alohida funksiya, chunki chaqiruv joyida tashqarida `Column` ham turibdi va
 * `AnimatedVisibility` o'sha `ColumnScope` ga yopishib qolardi (DSL marker xatosi).
 */
@Composable
private fun BoxScope.FloatingMarkAllRead(unreadCount: Int, onMarkAll: () -> Unit) {
    AnimatedVisibility(
        visible = unreadCount > 0,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        MarkAllReadBar(unreadCount, onMarkAll)
    }
}

/** Topbar ostidagi izoh — o'qilmaganlar soni yoki tinch holat. */
private fun NotificationsUiState.subtitle(): String {
    val s = notificationsStringsNow()
    return when {
        unreadCount > 0 -> s.unreadCount(unreadCount)
        items.isNotEmpty() -> s.allRead
        else -> s.emptySubtitle
    }
}

@Composable
private fun NotificationList(
    state: NotificationsUiState,
    vm: NotificationsViewModel,
    onOpenTarget: (NotificationTarget) -> Unit,
) {
    // Tepadan tortish — ro'yxat serverdan qayta o'qiladi. Push kelmagan holatda ham
    // (bildirishnoma ruxsati berilmagan bo'lsa) yangisini shu yo'l bilan ko'rish mumkin.
    ScPullRefresh(refreshing = state.refreshing, onRefresh = vm::refresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Sc.ScreenPadding,
                end = Sc.ScreenPadding,
                top = 20.dp,
                // Suzuvchi tugma balandligi + havo: oxirgi karta uning ostida qolib ketmasin.
                bottom = if (state.unreadCount > 0) 104.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            items(state.items, key = { it.id }) { n ->
                NotificationCard(n) {
                    vm.onOpened(n)
                    // `None` — hech qayerga olib bormaydigan bildirishnoma (masalan "Xush
                    // kelibsiz"): u ham bosiladi, faqat o'qilgan bo'ladi.
                    if (n.target != NotificationTarget.None) onOpenTarget(n.target)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification, onClick: () -> Unit) {
    val (icon, tint, accent) = n.type.visual()
    Row(
        Modifier.fillMaxWidth()
            // O'qilmagan karta biroz och ko'k: ro'yxatni bir qarashda ajratadi, o'ng
            // chetdagi kichkina nuqtadan farqli ravishda periferik ko'rish ham ilg'aydi.
            .scCard(
                radius = 22.dp,
                background = if (n.read) Sc.Card else Sc.TintBlue,
                borderColor = if (n.read) Sc.Border else Sc.Brand.copy(alpha = 0.22f),
                onClick = onClick,
            )
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ScIconTile(if (n.read) tint else Sc.Card, size = 48.dp, radius = 15.dp) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Sarlavha to'liq ko'rinadi: bir qatorga sig'masa IKKINCHI qatorga tushadi.
                // Ilgari `maxLines = 1` edi va uzun sarlavha ("E'loningiz moderatsiyadan
                // o'tmadi…") aynan ma'noni tashuvchi joyidan kesilardi.
                ScText(
                    n.title, 15.5f, FontWeight.ExtraBold, Sc.Ink,
                    modifier = Modifier.weight(1f), lineHeight = 20f, maxLines = 2,
                )
                if (!n.read) {
                    Box(
                        Modifier.padding(top = 6.dp).size(8.dp)
                            .background(Sc.Brand, RoundedCornerShape(percent = 50)),
                    )
                }
            }
            if (n.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ScText(n.body, 13f, FontWeight.Medium, Sc.InkSoft, lineHeight = 19f, maxLines = 3)
            }
            val time = NotificationTime.label(n.createdAt)
            if (time.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                ScText(time, 12f, FontWeight.SemiBold, Sc.MutedLight)
            }
        }
    }
}

/**
 * Pastdagi "hammasini o'qish" tugmasi.
 *
 * Fon — pastga qarab qoraymaydigan, ekran foniga eriydigan gradient: tugma ostidan
 * o'tayotgan kartalar to'satdan kesilmasin.
 */
@Composable
private fun MarkAllReadBar(unreadCount: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Sc.Bg, Sc.Bg)))
            .navigationBarsPadding()
            .padding(start = Sc.ScreenPadding, end = Sc.ScreenPadding, top = 26.dp, bottom = 16.dp),
    ) {
        // Sondagi qiymat matnda: tugma "nima bo'ladi" ni ham, "nechtasiga" ni ham aytadi.
        ScGradientButton(notificationsStrings().markAllRead(unreadCount), onClick, radius = 18.dp)
    }
}

/**
 * Kesh BO'SH va so'rov ham yiqilgan — faqat shundagina ko'rsatiladigan hech narsa qolmaydi.
 * Xato matni typed [dev.core.common.Resource.Error] dan keladi ("Internet aloqasi yo'q…").
 */
@Composable
private fun LoadFailed(message: String, onRetry: () -> Unit) {
    ScEmptyStateBox(
        Modifier.fillMaxSize(),
        title = uiStrings().loadFailed,
        message = message,
        icon = ScIcons.Bell,
        tint = Sc.TintBlue,
        iconColor = Sc.Brand,
        actionText = uiStrings().retry,
        onAction = onRetry,
    )
}

/** Bildirishnoma turi → ikona, plitka foni va accent rangi (dizayn palitrasidan). */
@Composable
private fun NotificationType.visual(): Triple<ImageVector, Color, Color> = when (this) {
    NotificationType.JOB -> Triple(ScIcons.Briefcase, Sc.TintBlue, Sc.Brand)
    NotificationType.DISCOUNT -> Triple(ScIcons.DiscountTag, Sc.TintOrange, Sc.Orange)
    NotificationType.LISTING -> Triple(ScIcons.FileText, Sc.TintGreenDeep, Sc.Success)
    NotificationType.CHAT -> Triple(ScIcons.MessageLines, Sc.TintViolet, Sc.Violet)
    NotificationType.CONNECTION -> Triple(ScIcons.Users, Sc.TintAmber, Sc.Amber)
    NotificationType.SYSTEM -> Triple(ScIcons.Bell, Sc.TintPink, Sc.Pink)
}
