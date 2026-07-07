package dev.feature.auth.presentation.main

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette
import org.koin.compose.viewmodel.koinViewModel

private enum class ProfileSection(val title: String) { MY_ADS("Mening e'lonlarim"), SAVED("Saqlangan chegirmalar"), APPLICATIONS("Ish arizalarim") }

@Composable
fun ProfileScreen(onBack: () -> Unit, onLoggedOut: () -> Unit, vm: ProfileViewModel = koinViewModel()) {
    val palette = authPalette
    val state by vm.state.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf<ProfileSection?>(null) }

    if (section != null) {
        SectionList(section!!, state, palette, onBack = { section = null })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Violet header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF6C47FF), Color(0xFF7C4DFF), Color(0xFF5B34D6))), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .padding(start = 20.dp, end = 20.dp, top = 54.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(AuthIcons.ArrowLeft, "Orqaga", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Profilim", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Profil kartasi
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text(state.name.take(1).uppercase(), style = TextStyle(fontFamily = AuthFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.primary))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(state.name, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Black, color = palette.ink))
                        Icon(AuthIcons.ShieldCheck, null, tint = palette.successDeep, modifier = Modifier.size(15.dp))
                    }
                    val sub = listOfNotNull(state.universityMonogram, state.courseLabel).joinToString(" · ").ifBlank { state.contact }
                    Text(sub, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Profilni tahrirlash", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary))
                        Icon(AuthIcons.ChevronRight, null, tint = palette.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Menyu
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MenuRow(AuthIcons.FileText, ProfileSection.MY_ADS.title, "${state.myAds.size}", palette) { section = ProfileSection.MY_ADS }
                MenuRow(AuthIcons.Bookmark, ProfileSection.SAVED.title, "${state.savedDiscounts.size}", palette) { section = ProfileSection.SAVED }
                MenuRow(AuthIcons.Briefcase, ProfileSection.APPLICATIONS.title, "${state.applications.size}", palette) { section = ProfileSection.APPLICATIONS }
                MenuRow(AuthIcons.Settings, "Sozlamalar", null, palette) {}
            }

            Spacer(Modifier.height(16.dp))

            // Chiqish
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFDC2626).copy(alpha = 0.10f)).clickable { vm.logout(onLoggedOut) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AuthIcons.LogOut, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                Text("Chiqish", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626)))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, trailing: String?, palette: AuthPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = palette.primary, modifier = Modifier.size(17.dp))
        }
        Text(title, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Bold, color = palette.ink), modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint))
            Spacer(Modifier.width(6.dp))
        }
        Icon(AuthIcons.ChevronRight, null, tint = palette.inkFaint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SectionList(section: ProfileSection, state: ProfileUiState, palette: AuthPalette, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(12.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(AuthIcons.ArrowLeft, "Orqaga", tint = palette.ink, modifier = Modifier.size(18.dp))
            }
            Text(section.title, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Black, color = palette.ink))
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            when (section) {
                ProfileSection.MY_ADS -> items(state.myAds, key = { it.id }) { SimpleRow(it.title, "${it.category} · ${it.price}", it.createdAgo, palette) }
                ProfileSection.SAVED -> items(state.savedDiscounts, key = { it.id }) { SimpleRow("${it.merchant} — ${it.title}", "−${it.discountPercent}%", it.expiry ?: "", palette) }
                ProfileSection.APPLICATIONS -> items(state.applications, key = { it.id }) { SimpleRow(it.jobTitle, it.company, statusLabel(it.status.name), palette) }
            }
        }
    }
}

@Composable
private fun SimpleRow(title: String, subtitle: String, trailing: String, palette: AuthPalette) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
            Text(subtitle, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
        }
        if (trailing.isNotBlank()) {
            Text(trailing, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.primary))
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "SENT" -> "Yuborilgan"
    "VIEWED" -> "Ko'rildi"
    "INTERVIEW" -> "Suhbat"
    "REJECTED" -> "Rad etildi"
    else -> status
}
