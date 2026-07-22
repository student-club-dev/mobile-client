package dev.feature.profile.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.feature.ads.domain.model.Ad
import dev.feature.profile.presentation.components.ProfileAvatar
import org.koin.compose.viewmodel.koinViewModel

private enum class ProfileSection(val title: String) {
    MY_ADS("Mening e'lonlarim"),
    SAVED("Saqlangan chegirmalar"),
    APPLICATIONS("Ish arizalarim"),
}

/**
 * Profil ekrani — gradient topbar ostidan chiqib turuvchi avatar kartasi, so'ng menyu
 * qatorlari va "Chiqish". Bo'lim tanlanganda ([ProfileSection]) o'sha bo'lim ro'yxati
 * shu ekranning o'rniga chiziladi — alohida route emas, ichki holat.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onEditProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditAd: (String) -> Unit = {},
    /** "Mening biznesim" — chegirma e'lonlari (feature:discounts). */
    onOpenMyBusiness: () -> Unit = {},
    /** Talaba shell'ida biznes kartasi yashiriladi — biznesmenda o'zining alohida bo'limi bor. */
    showMyBusiness: Boolean = true,
    vm: ProfileViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf<ProfileSection?>(null) }

    val current = section
    if (current != null) {
        SectionList(current, state, onBack = { section = null }, onDeleteAd = vm::deleteAd, onEditAd = onEditAd)
        return
    }

    Column(
        Modifier.fillMaxSize().background(Sc.Bg).verticalScroll(rememberScrollState()),
    ) {
        ScHeader(horizontalPadding = 18.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                ScHeaderTitle("Profilim", modifier = Modifier.weight(1f))
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
        ) {
            Spacer(Modifier.height(20.dp))
            ProfileCard(state, onEditProfile)

            if (showMyBusiness) {
                Spacer(Modifier.height(14.dp))
                MyBusinessCard(onOpenMyBusiness)
            }

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuRow(ScIcons.FileText, Sc.TintBlue, Sc.Brand, ProfileSection.MY_ADS.title, "${state.myAds.size}") {
                    section = ProfileSection.MY_ADS
                }
                MenuRow(AppIcons.Bookmark, Sc.TintOrange, Sc.Orange, ProfileSection.SAVED.title, "${state.savedDiscounts.size}") {
                    section = ProfileSection.SAVED
                }
                MenuRow(ScIcons.Briefcase, Sc.TintGreenDeep, Sc.Success, ProfileSection.APPLICATIONS.title, "${state.applications.size}") {
                    section = ProfileSection.APPLICATIONS
                }
                MenuRow(AppIcons.Settings, Sc.TintViolet, Sc.Violet, "Sozlamalar", null, onClick = onOpenSettings)
            }

            Spacer(Modifier.height(14.dp))
            LogoutRow { vm.logout(onLoggedOut) }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Avatar + ism + universitet/kurs va "Profilni tahrirlash" havolasi. */
@Composable
private fun ProfileCard(state: ProfileUiState, onEditProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 24.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileAvatar(
            name = state.name,
            size = 62.dp,
            fontSize = 24.sp,
            avatarUrl = state.profile?.avatarUrl,
        )
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ScText(state.name, 17f, FontWeight.ExtraBold, Sc.Ink, letterSpacing = -0.3f, maxLines = 1)
                Icon(AppIcons.ShieldCheck, null, tint = Sc.Success, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(2.dp))
            val sub = listOfNotNull(state.universityMonogram, state.courseLabel)
                .joinToString(" · ")
                .ifBlank { state.contact }
            ScText(sub, 13f, FontWeight.Medium, Sc.Muted, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onEditProfile),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ScText("Profilni tahrirlash", 13f, FontWeight.Bold, Sc.Brand, maxLines = 1)
                Icon(ScIcons.ChevronRight, null, tint = Sc.Brand, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/**
 * "Mening biznesim" — chegirma e'lonlarini shu yerdan qo'yiladi. Faqat biznes egasida
 * ko'rinadi (talaba shell'ida `showMyBusiness = false`).
 */
@Composable
private fun MyBusinessCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .scCard(radius = 22.dp, background = Sc.TintBlue, borderColor = Sc.BorderSoft, onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ScIconTile(Color.White, size = 46.dp, radius = 15.dp) {
            Icon(AppIcons.Store, null, tint = Sc.Brand, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            ScText("Mening biznesim", 15f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            ScText("Chegirma e'loni qo'yish va boshqarish", 12.5f, FontWeight.Medium, Sc.InkSoft, maxLines = 1)
        }
        Icon(ScIcons.ChevronRight, null, tint = Sc.Brand, modifier = Modifier.size(16.dp))
    }
}

/** Menyu qatori — rangli plitka, sarlavha, ixtiyoriy son va `›`. */
@Composable
private fun MenuRow(
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
private fun LogoutRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Sc.Danger.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.LogOut, null, tint = Sc.Danger, modifier = Modifier.size(18.dp))
        ScText("Chiqish", 14f, FontWeight.ExtraBold, Sc.Danger, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Bo'lim ro'yxati
// ---------------------------------------------------------------------------

@Composable
private fun SectionList(
    section: ProfileSection,
    state: ProfileUiState,
    onBack: () -> Unit,
    onDeleteAd: (String) -> Unit,
    onEditAd: (String) -> Unit,
) {
    var adToDelete by remember { mutableStateOf<Ad?>(null) }

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        ScHeader(horizontalPadding = 18.dp) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                ScHeaderTitle(section.title, size = 21f, modifier = Modifier.weight(1f))
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = Sc.ScreenPadding, end = Sc.ScreenPadding, top = 20.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            when (section) {
                ProfileSection.MY_ADS -> items(state.myAds, key = { it.id }) { ad ->
                    DeletableAdRow(
                        ad.title,
                        "${ad.category} · ${ad.price}",
                        ad.createdAgo,
                        onEdit = { onEditAd(ad.id) },
                        onDelete = { adToDelete = ad },
                    )
                }

                ProfileSection.SAVED -> items(state.savedDiscounts, key = { it.id }) {
                    SimpleRow("${it.merchant} — ${it.title}", "−${it.discountPercent}%", it.expiry ?: "")
                }

                ProfileSection.APPLICATIONS -> items(state.applications, key = { it.id }) {
                    SimpleRow(it.jobTitle, it.company, statusLabel(it.status.name))
                }
            }
        }
    }

    val target = adToDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { adToDelete = null },
            containerColor = Sc.Card,
            shape = RoundedCornerShape(24.dp),
            title = { Text("E'lonni o'chirish", style = scStyle(17f, FontWeight.ExtraBold, Sc.Ink)) },
            text = {
                Text(
                    "\"${target.title}\" e'lonini o'chirmoqchimisiz? Bu amalni ortga qaytarib bo'lmaydi.",
                    style = scStyle(14f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteAd(target.id); adToDelete = null }) {
                    Text("O'chirish", style = scStyle(14.5f, FontWeight.ExtraBold, Sc.Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { adToDelete = null }) {
                    Text("Bekor", style = scStyle(14.5f, FontWeight.Bold, Sc.Muted))
                }
            },
        )
    }
}

@Composable
private fun DeletableAdRow(
    title: String,
    subtitle: String,
    trailing: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 20.dp).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            ScText(title, 14.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            ScText(subtitle, 12.5f, FontWeight.Medium, Sc.InkSoft, maxLines = 1)
            if (trailing.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ScText(trailing, 11.5f, FontWeight.Bold, Sc.MutedLight, maxLines = 1)
            }
        }
        SquareAction(AppIcons.Pencil, "Tahrirlash", Sc.TintBlue, Sc.Brand, onEdit)
        SquareAction(ScIcons.Close, "O'chirish", Sc.TintPink, Sc.Danger, onDelete)
    }
}

/** Karta o'ng chetidagi kichik kvadrat amal tugmasi. */
@Composable
private fun SquareAction(
    icon: ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SimpleRow(title: String, subtitle: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().scCard(radius = 20.dp).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            ScText(title, 14.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            ScText(subtitle, 12.5f, FontWeight.Medium, Sc.InkSoft, maxLines = 1)
        }
        if (trailing.isNotBlank()) {
            ScText(trailing, 12.5f, FontWeight.Bold, Sc.Brand, maxLines = 1)
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
