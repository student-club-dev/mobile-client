package dev.feature.settings.presentation

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.domain.model.Region
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.theme.Sc
import dev.feature.profile.presentation.ProfileViewModel
import dev.feature.settings.domain.model.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sozlamalar ekrani (A3/C3). Hisob, mavzu, bildirishnoma va ilova ma'lumotlari —
 * gradient topbar + oq kartalar (dizayn tizimi tokenlari).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
    /** "Bloklangan talabalar" ro'yxati — `Connections` feature'idagi alohida ekran. */
    onOpenBlocked: () -> Unit = {},
    vm: ProfileViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.state.collectAsStateWithLifecycle()

    var aboutExpanded by remember { mutableStateOf(false) }
    var regionSheet by remember { mutableStateOf(false) }

    if (regionSheet) {
        val picker by settingsVm.regionPicker.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { settingsVm.loadRegions() }
        RegionPickerSheet(
            picker = picker,
            selected = settings.region,
            onSelect = {
                settingsVm.selectRegion(it)
                regionSheet = false
            },
            onDismiss = { regionSheet = false },
        )
    }

    Column(Modifier.fillMaxSize().background(Sc.Bg).verticalScroll(rememberScrollState())) {
        ScHeader(horizontalPadding = 18.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                ScHeaderTitle("Sozlamalar", modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Hisob")
            SettingRow(AppIcons.Pencil, Sc.TintBlue, Sc.Brand, "Profilni tahrirlash", state.name, onClick = onEditProfile)

            Spacer(Modifier.height(4.dp))
            SectionTitle("Mavzu")
            ThemeSelector(settings.themeMode) { settingsVm.setThemeMode(it) }

            Spacer(Modifier.height(4.dp))
            SectionTitle("E'lonlar")
            SettingRow(
                ScIcons.MapPin, Sc.TintOrange, Sc.Orange,
                "Viloyat",
                settings.region?.name ?: "Butun O'zbekiston",
            ) { regionSheet = true }
            Text(
                "Chegirma va e'lonlar shu viloyat bo'yicha ko'rsatiladi.",
                style = scStyle(12f, FontWeight.Medium, Sc.Muted, lineHeight = 17f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle("Maxfiylik")
            LastSeenSelector(state.profile?.lastSeenVisibility) { vm.setLastSeenVisibility(it) }
            Text(
                "\"Oxirgi ko'rilgan\" va \"onlayn\" holatini kim ko'rishi. \"Hech kim\" " +
                    "tanlansa, siz ham boshqalarnikini ko'rishda davom etasiz.",
                style = scStyle(12f, FontWeight.Medium, Sc.Muted, lineHeight = 17f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            // Ro'yxatda faqat foydalanuvchi bloklaganlari bo'ladi — ekranning o'zi ham shuni
            // yozadi, bu yerda esa tugma "kim meni bloklagan" degan kutuvni uyg'otmasligi kerak.
            SettingRow(
                AppIcons.Lock, Sc.TintAmber, Sc.Danger,
                "Bloklangan talabalar", null, onClick = onOpenBlocked,
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle("Bildirishnomalar")
            ToggleRow(AppIcons.Bell, Sc.TintPink, Sc.Pink, "Push bildirishnomalar", settings.pushEnabled) {
                settingsVm.setPush(it)
            }
            ToggleRow(AppIcons.Mail, Sc.TintViolet, Sc.Violet, "Email xabarnomalar", settings.emailEnabled) {
                settingsVm.setEmail(it)
            }

            Spacer(Modifier.height(4.dp))
            SectionTitle("Umumiy")
            SettingRow(
                AppIcons.ShieldCheck, Sc.TintGreenDeep, Sc.Success,
                "Ilova haqida", if (aboutExpanded) "Versiya 1.0.0" else null,
            ) { aboutExpanded = !aboutExpanded }
            if (aboutExpanded) {
                Text(
                    "Student Club — talabalar uchun super-app: chegirmalar, ishlar, " +
                        "e'lonlar va xabarlar.\nVersiya 1.0.0",
                    style = scStyle(12.5f, FontWeight.Medium, Sc.Muted, lineHeight = 19f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Sc.Danger.copy(alpha = 0.10f))
                    .clickable { vm.logout(onLoggedOut) }
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.LogOut, null, tint = Sc.Danger, modifier = Modifier.size(18.dp))
                ScText("Chiqish", 14f, FontWeight.ExtraBold, Sc.Danger, maxLines = 1)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Viloyat tanlash oynasi. "Butun O'zbekiston" — filtrni olib tashlaydi (feed butun mamlakat
 * bo'yicha keladi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerSheet(
    picker: RegionPickerUiState,
    selected: Region?,
    onSelect: (Region?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sc.Bg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScText("Viloyatni tanlang", 18f, FontWeight.ExtraBold, Sc.Ink)
            Spacer(Modifier.height(4.dp))

            RegionRow("Butun O'zbekiston", selected == null) { onSelect(null) }

            when {
                picker.loading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Sc.Brand) }

                picker.error != null -> ScText(
                    picker.error, 13f, FontWeight.Medium, Sc.Danger,
                )

                else -> LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(picker.regions, key = { it.id }) { region ->
                        RegionRow(region.name, region.id == selected?.id) { onSelect(region) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Sc.TintBlue else Sc.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScText(
            label, 14f, if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
            if (active) Sc.Brand else Sc.Ink, Modifier.weight(1f), maxLines = 1,
        )
        if (active) Icon(AppIcons.Check, null, tint = Sc.Brand, modifier = Modifier.size(18.dp))
    }
}

/**
 * "Oxirgi ko'rilgan" maxfiyligi (`PUT /v1/profile/me` → `lastSeenVisibility`).
 *
 * Server sukuti — `CONNECTIONS`, shuning uchun profil hali yuklanmagan bo'lsa ham shu
 * variant faol ko'rinadi. `NOBODY` da server `online` ni ham yashiradi va sizning
 * `presence:update` hodisangizni umuman yubormaydi.
 */
@Composable
private fun LastSeenSelector(current: String?, onSelect: (String) -> Unit) {
    val options = listOf(
        "EVERYONE" to "Hamma",
        "CONNECTIONS" to "Do'stlar",
        "NOBODY" to "Hech kim",
    )
    val selected = current ?: "CONNECTIONS"
    Row(
        Modifier.fillMaxWidth().scCard(radius = 18.dp).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.weight(1f).height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) Sc.TintBlue else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                ScText(label, 13f, FontWeight.Bold, if (active) Sc.Brand else Sc.Muted, maxLines = 1)
            }
        }
    }
}

/** Uch holatli mavzu selektori — faol variant ko'k tint bilan ajratiladi. */
@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to "Tizim",
        ThemeMode.LIGHT to "Yorug'",
        ThemeMode.DARK to "Tungi",
    )
    Row(
        Modifier.fillMaxWidth().scCard(radius = 18.dp).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEach { (mode, label) ->
            val active = mode == current
            Box(
                Modifier.weight(1f).height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) Sc.TintBlue else Color.Transparent)
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                ScText(
                    label, 13f, FontWeight.Bold,
                    if (active) Sc.Brand else Sc.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    ScText(
        text, 12.5f, FontWeight.Bold, Sc.Muted,
        Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        letterSpacing = 0.2f,
        maxLines = 1,
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tileBackground: Color,
    tint: Color,
    title: String,
    trailing: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 20.dp, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScIconTile(tileBackground, size = 42.dp, radius = 14.dp) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        ScText(title, 14.5f, FontWeight.Bold, Sc.Ink, Modifier.weight(1f), maxLines = 1)
        if (trailing != null) {
            ScText(trailing, 13f, FontWeight.Bold, Sc.Muted, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Icon(ScIcons.ChevronRight, null, tint = Sc.MutedLight, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    tileBackground: Color,
    tint: Color,
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 20.dp, onClick = { onToggle(!checked) }).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScIconTile(tileBackground, size = 42.dp, radius = 14.dp) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        ScText(title, 14.5f, FontWeight.Bold, Sc.Ink, Modifier.weight(1f), maxLines = 1)
        SwitchTrack(checked)
    }
}

@Composable
private fun SwitchTrack(checked: Boolean) {
    val knobOffset by animateDpAsState(if (checked) 20.dp else 2.dp)
    Box(
        Modifier.width(44.dp).height(26.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (checked) Sc.Brand else Sc.Handle),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = knobOffset).size(22.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
        )
    }
}
