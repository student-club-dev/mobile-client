package dev.feature.clubs.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.feature.clubs.domain.model.Club
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.AppIcons
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import org.koin.compose.viewmodel.koinViewModel

private val clubAccents = listOf(
    Color(0xFF6C47FF), Color(0xFF2563EB), Color(0xFF059669),
    Color(0xFFD97706), Color(0xFFBE185D), Color(0xFF0EA5E9),
)
private val clubEmojis = listOf("💻", "🗣️", "⚽", "🤝", "🎨", "🌍", "🎬", "📚")

/** Klublar ekrani (C4). Local DB'dan real ro'yxat; "Qo'shilish/Chiqish" ishlaydi. */
@Composable
fun ClubsScreen(onBack: () -> Unit, vm: ClubsViewModel = koinViewModel()) {
    val palette = appPalette
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                Column(Modifier.weight(1f)) {
                    ScHeaderTitle("Klublar")
                    if (state.joinedCount > 0) {
                        Spacer(Modifier.height(3.dp))
                        ScHeaderSubtitle("${state.joinedCount} ta klubga a'zosiz")
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.clubs, key = { it.id }) { club ->
                ClubCard(club, palette) { vm.toggleJoin(club) }
            }
        }
    }
}

@Composable
private fun ClubCard(club: Club, palette: AppPalette, onToggle: () -> Unit) {
    val idx = ((club.id - 1).toInt()).mod(clubAccents.size)
    val accent = clubAccents[idx]
    val emoji = clubEmojis[((club.id - 1).toInt()).mod(clubEmojis.size)]
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(emoji, style = TextStyle(fontSize = 22.sp))
        }
        Column(Modifier.weight(1f)) {
            Text(club.name, style = TextStyle(fontFamily = AppFontFamily, fontSize = 14.5f.sp, fontWeight = FontWeight.Black, color = palette.ink))
            Spacer(Modifier.height(2.dp))
            Text(club.description, style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, color = palette.inkMuted), maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(AppIcons.Users, null, tint = palette.inkFaint, modifier = Modifier.size(13.dp))
                Text("${club.membersCount} a'zo", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint))
            }
        }
        JoinButton(joined = club.joined, accent = accent, palette = palette, onClick = onToggle)
    }
}

@Composable
private fun JoinButton(joined: Boolean, accent: Color, palette: AppPalette, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).width(if (joined) 78.dp else 90.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (joined) palette.glass else accent)
            .border(1.dp, if (joined) palette.border else accent, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (joined) "A'zosiz" else "Qo'shilish",
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (joined) palette.inkMuted else Color.White),
        )
    }
}
