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
import dev.core.common.locale.AppLanguage
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
import dev.core.uikit.components.ScShimmerList
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
    val s = settingsStrings()

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
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = s.back)
                ScHeaderTitle(s.title, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(s.sectionAccount)
            SettingRow(AppIcons.Pencil, Sc.TintBlue, Sc.Brand, s.editProfile, state.name, onClick = onEditProfile)

            // Til — mavzudan OLDIN: ilova birinchi marta ingliz tilida ochiladi, shuning
            // uchun o'z tilini qidirayotgan foydalanuvchi uni ro'yxatning boshida topsin.
            Spacer(Modifier.height(4.dp))
            SectionTitle(s.sectionLanguage)
            LanguageSelector(settings.language) { settingsVm.setLanguage(it) }
            Text(
                s.languageHint,
                style = scStyle(12f, FontWeight.Medium, Sc.Muted, lineHeight = 17f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle(s.sectionAppearance)
            ThemeSelector(settings.themeMode) { settingsVm.setThemeMode(it) }

            Spacer(Modifier.height(4.dp))
            SectionTitle(s.sectionListings)
            SettingRow(
                ScIcons.MapPin, Sc.TintOrange, Sc.Orange,
                s.region,
                settings.region?.name ?: s.allUzbekistan,
            ) { regionSheet = true }
            Text(
                s.regionHint,
                style = scStyle(12f, FontWeight.Medium, Sc.Muted, lineHeight = 17f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle(s.sectionPrivacy)
            LastSeenSelector(state.profile?.lastSeenVisibility) { vm.setLastSeenVisibility(it) }
            Text(
                s.lastSeenHint,
                style = scStyle(12f, FontWeight.Medium, Sc.Muted, lineHeight = 17f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            Spacer(Modifier.height(10.dp))
            PhoneVisibilitySelector(state.profile?.phoneVisibility) { vm.setPhoneVisibility(it) }
            Text(
                s.phoneVisibilityHint,
                style = scStyle(12f, FontWeight.Medium, Sc.Muted, lineHeight = 17f),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            // Ro'yxatda faqat foydalanuvchi bloklaganlari bo'ladi — ekranning o'zi ham shuni
            // yozadi, bu yerda esa tugma "kim meni bloklagan" degan kutuvni uyg'otmasligi kerak.
            SettingRow(
                AppIcons.Lock, Sc.TintAmber, Sc.Danger,
                s.blockedStudents, null, onClick = onOpenBlocked,
            )

            Spacer(Modifier.height(4.dp))
            SectionTitle(s.sectionNotifications)
            ToggleRow(AppIcons.Bell, Sc.TintPink, Sc.Pink, s.pushNotifications, settings.pushEnabled) {
                settingsVm.setPush(it)
            }
            ToggleRow(AppIcons.Mail, Sc.TintViolet, Sc.Violet, s.emailNotifications, settings.emailEnabled) {
                settingsVm.setEmail(it)
            }

            Spacer(Modifier.height(4.dp))
            SectionTitle(s.sectionGeneral)
            SettingRow(
                AppIcons.ShieldCheck, Sc.TintGreenDeep, Sc.Success,
                s.about, if (aboutExpanded) s.version else null,
            ) { aboutExpanded = !aboutExpanded }
            if (aboutExpanded) {
                Text(
                    s.aboutBody,
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
                ScText(s.logout, 14f, FontWeight.ExtraBold, Sc.Danger, maxLines = 1)
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
    val s = settingsStrings()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sc.Bg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScText(s.selectRegion, 18f, FontWeight.ExtraBold, Sc.Ink)
            Spacer(Modifier.height(4.dp))

            RegionRow(s.allUzbekistan, selected == null) { onSelect(null) }

            when {
                // Viloyat qatorlari kelguncha — o'sha qatorlarning skeleti.
                picker.loading -> ScShimmerList(
                    rows = 6,
                    leading = false,
                    modifier = Modifier.padding(vertical = 10.dp),
                )

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
    val s = settingsStrings()
    val options = listOf(
        "EVERYONE" to s.visibilityEveryone,
        "CONNECTIONS" to s.visibilityConnections,
        "NOBODY" to s.visibilityNobody,
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

/**
 * Telefon raqami maxfiyligi (`PUT /v1/profile/me` → `phoneVisibility`).
 *
 * ⚠️ Server sukuti — **`NOBODY`** (`lastSeenVisibility` niki `CONNECTIONS`): talabalar
 * raqamini ko'rsatishga rozilik bermagan (`handoff/08-PROFILE.md` §4). Shuning uchun profil
 * hali yuklanmagan bo'lsa ham "Hech kim" faol ko'rinadi — bu yerda xavfsiz taxmin.
 *
 * Sozlama `lastSeenVisibility` dan **mustaqil**: presence ochiq, raqam yopiq bo'lishi mumkin.
 */
@Composable
private fun PhoneVisibilitySelector(current: String?, onSelect: (String) -> Unit) {
    val s = settingsStrings()
    val options = listOf(
        "EVERYONE" to s.visibilityEveryone,
        "CONNECTIONS" to s.visibilityConnections,
        "NOBODY" to s.visibilityNobody,
    )
    val selected = current ?: "NOBODY"
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

/**
 * Til selektori — uchta variant bir qatorda (mavzu selektori bilan bir xil ko'rinish).
 *
 * Tillar O'Z tilida yoziladi (`English` / `Русский` / `O'zbekcha`), tarjima qilinmaydi:
 * ilova ingliz tilida ochilganda o'zbek foydalanuvchi o'z tilini tanish yozuv bo'yicha
 * topishi kerak.
 */
@Composable
private fun LanguageSelector(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 18.dp).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            val active = language == current
            Box(
                Modifier.weight(1f).height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) Sc.TintBlue else Color.Transparent)
                    .clickable { onSelect(language) },
                contentAlignment = Alignment.Center,
            ) {
                ScText(
                    language.nativeName, 13f, FontWeight.Bold,
                    if (active) Sc.Brand else Sc.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Uch holatli mavzu selektori — faol variant ko'k tint bilan ajratiladi. */
@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val s = settingsStrings()
    val options = listOf(
        ThemeMode.SYSTEM to s.themeSystem,
        ThemeMode.LIGHT to s.themeLight,
        ThemeMode.DARK to s.themeDark,
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
