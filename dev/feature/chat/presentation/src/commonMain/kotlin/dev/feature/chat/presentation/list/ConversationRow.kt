package dev.feature.chat.presentation.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.theme.Sc
import dev.feature.chat.domain.model.ConversationItem
import dev.feature.chat.presentation.ChatFormat
import dev.feature.clubs.domain.model.Club
import dev.feature.chat.presentation.chatStrings

/** Suhbat avatarlari navbat bilan uch tint ranggida. */
private val avatarVisuals: List<Pair<Color, Color>>
    @Composable get() = listOf(
        Sc.TintViolet to Sc.Violet,
        Sc.TintBlue to Sc.Brand,
        Sc.TintGreenDeep to Sc.Success,
    )

/**
 * Suhbatlar ro'yxatidagi bitta qator.
 *
 * ⚠️ Yon tomonga surish imo-ishorasi ATAYLAB yo'q: bu ekranda gorizontal surish
 * allaqachon band — u papkalarni (`HorizontalPager`) almashtiradi va suhbatni ortga
 * yopadi (`ScSwipeBack`). Arxivlash uzoq bosish menyusida, bloklash va shikoyat bilan
 * bir joyda turadi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    item: ConversationItem,
    index: Int,
    /** Suhbatdosh AYNI PAYTDA yozmoqda (WS `typing`) — oxirgi xabar o'rniga ko'rsatiladi. */
    typing: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (tint, accent) = avatarVisuals[index.mod(avatarVisuals.size)]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Bosilganda kartochka bir oz "cho'kadi" — tegish sezilarli bo'lsin.
    val press = animateFloatAsState(
        if (pressed) PressedScale else 1f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )

    Row(
        modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            // `scCard` shaklni QIRQADI, bosish esa undan keyin qo'shiladi — shu sababli
            // ripple to'rtburchak emas, kartochka radiusi bo'yicha kesilgan chiqadi.
            .scCard(radius = 22.dp, elevation = 6.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(color = Sc.Brand),
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box {
            ScAvatar(
                name = item.other.displayName,
                size = 50.dp,
                avatarUrl = item.other.avatarUrl,
                background = tint,
                initialColor = accent,
            )
            // Onlayn holati SHU ro'yxatda haqiqiy (Redis'dan) — qidiruvdagidan farqli.
            if (item.other.online) {
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(1.dp)
                        .size(13.dp)
                        .background(Sc.Card, CircleShape)
                        .padding(2.5.dp)
                        .background(Sc.Success, CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            ScText(item.other.displayName, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            ConversationPreview(item, typing)
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ScText(
                ChatFormat.listStamp(item.conversation.lastMessageAt),
                12f, FontWeight.SemiBold, Sc.MutedLight, maxLines = 1,
            )
            UnreadBadge(item.unreadCount)
        }
    }
}

private const val PressedScale = 0.98f

/**
 * Oxirgi xabar qatori. Yangi xabar kelganda matn **almashadi**, sakramaydi; suhbatdosh
 * yozayotgan bo'lsa uning o'rnini pulsatsiyalanuvchi uch nuqta egallaydi.
 */
@Composable
private fun ConversationPreview(item: ConversationItem, typing: Boolean) {
    AnimatedContent(
        targetState = if (typing) null else item.lastMessage.preview(),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        label = "preview",
    ) { text ->
        if (text == null) TypingPreview() else {
            ScText(text, 13.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
        }
    }
}

/** «yozmoqda…» — uch nuqta navbat bilan yonib-o'chadi. */
@Composable
private fun TypingPreview() {
    val transition = rememberInfiniteTransition(label = "typing")
    val accent = Sc.Brand
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ScText("yozmoqda", 13.5f, FontWeight.SemiBold, accent, maxLines = 1)
        Spacer(Modifier.size(2.dp))
        repeat(TypingDots) { dot ->
            val alpha = transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(560, delayMillis = dot * 160),
                    RepeatMode.Reverse,
                ),
                label = "dot$dot",
            )
            Box(
                Modifier.size(3.5.dp)
                    .graphicsLayer { this.alpha = alpha.value }
                    .background(accent, CircleShape),
            )
        }
    }
}

private const val TypingDots = 3

/**
 * O'qilmaganlar belgichasi: son o'zgarganda "irg'ib" almashadi, nolga tushganda esa
 * kichrayib yo'qoladi (shunchaki g'oyib bo'lmaydi).
 */
@Composable
private fun UnreadBadge(count: Int) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) + fadeIn())
                .togetherWith(scaleOut(tween(140)) + fadeOut(tween(140)))
        },
        label = "unread",
    ) { value ->
        if (value <= 0) Spacer(Modifier.size(0.dp)) else {
            Box(
                Modifier.size(19.dp).background(Sc.Brand, CircleShape),
                contentAlignment = Alignment.Center,
            ) { ScText("$value", 10.5f, FontWeight.ExtraBold, Color.White) }
        }
    }
}

/** Klub kartalari dizaynda navbat bilan ko'k / binafsha / yashil bo'ladi. */
@Composable
private fun clubVisual(index: Int): Triple<Color, Color, ImageVector> = when (index.mod(3)) {
    0 -> Triple(Sc.TintBlue, Sc.Brand, ScIcons.Laptop)
    1 -> Triple(Sc.TintViolet, Sc.Violet, ScIcons.MessageLines)
    else -> Triple(Sc.TintGreen, Sc.Success, ScIcons.Medal)
}

@Composable
internal fun ClubRow(club: Club, onClick: () -> Unit, onToggleJoin: () -> Unit) {
    val (tint, accent, icon) = clubVisual((club.id - 1).toInt())
    Row(
        Modifier.fillMaxWidth()
            .scCard(radius = 22.dp, elevation = 6.dp, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // Rasmi bo'lsa — o'zi, bo'lmasa klub belgisi: bosh harf odam ismidek ko'rinardi.
        if (club.imageUrl.isNullOrBlank()) {
            ScIconTile(tint, size = 50.dp, radius = 18.dp) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
        } else {
            ScAvatar(
                name = club.name,
                size = 50.dp,
                avatarUrl = club.imageUrl,
                background = tint,
                initialColor = accent,
            )
        }
        Column(Modifier.weight(1f)) {
            ScText(club.name, 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            ScText(
                club.description.ifBlank { chatStrings().membersCount(club.membersCount) },
                13.5f, FontWeight.Medium, Sc.Muted, maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            ScText(chatStrings().membersCount(club.membersCount), 12f, FontWeight.Bold, accent, maxLines = 1)
        }
        JoinButton(club.joined, accent, onToggleJoin)
    }
}

/** Qo'shilish / chiqish — avval klublar ekranida edi, endi shu qatorning o'zida. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JoinButton(joined: Boolean, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        if (pressed) PressedScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "join-press",
    )
    Box(
        Modifier.graphicsLayer {
            scaleX = press.value
            scaleY = press.value
        }
            .clip(RoundedCornerShape(12.dp))
            .background(if (joined) Sc.Chip else accent)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScText(
            if (joined) chatStrings().notMember else chatStrings().join,
            12f, FontWeight.ExtraBold,
            if (joined) Sc.ChipInk else Color.White,
            maxLines = 1,
        )
    }
}
