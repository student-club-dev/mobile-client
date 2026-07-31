package dev.feature.profile.presentation

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.scUploadPercent
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.theme.Sc
import dev.feature.ads.domain.model.Ad
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profil bo'limlari — Telegram maketidagi «Postlar / Arxivlangan postlar» qatorining
 * o'rnida ilovaning o'z ro'yxatlari turadi (ilovada post tushunchasi yo'q).
 */
private enum class ProfileTab(val label: String) {
    MY_ADS("E'lonlarim"),
    SAVED("Saqlangan"),
    APPLICATIONS("Arizalarim"),
}

/**
 * **O'z profilim** — suhbatdosh profili (`PeerProfileSheet`) bilan bir xil Telegram
 * maketida: katta markazlashgan avatar, ism va holat, uch amal tugmasi, ma'lumot kartasi
 * va bo'limlar.
 *
 * Bo'lim tanlanganda ([ProfileTab]) ro'yxat **shu yerda**, tab ostida chiziladi — alohida
 * ekran emas, ichki holat.
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
    val photos by vm.photos.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(ProfileTab.MY_ADS) }
    var adToDelete by remember { mutableStateOf<Ad?>(null) }

    // Avatar profil rasmlari to'plamidan olinadi: yangi rasm qo'yilgach `avatarUrl` faqat
    // profil qayta yuklangandan keyin yangilanadi, to'plam esa darhol.
    LaunchedEffect(Unit) { vm.loadPhotos() }
    val avatarUrl = photos.items.firstOrNull()?.url ?: state.profile?.avatarUrl

    // «Rasm belgilash» — yangi rasm DOIM birinchi o'ringa tushadi, ya'ni avatar bo'ladi.
    val imagePicker = rememberImagePicker { picked ->
        if (picked == null) return@rememberImagePicker
        vm.addPhoto(picked.bytes, picked.fileName)
    }

    Column(
        Modifier.fillMaxSize().background(Sc.Bg).verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(
            name = state.name,
            avatarUrl = avatarUrl,
            uploading = photos.uploading,
            progress = photos.progress,
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            onAvatarClick = { if (photos.canAdd) imagePicker.pick() },
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // --- Uchta amal tugmasi (maketdagidek) ---------------------------------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActionTile(
                    AppIcons.Camera,
                    when {
                        photos.uploading && photos.progress != null ->
                            "Yuklanmoqda ${scUploadPercent(photos.progress!!)}"
                        photos.uploading -> "Saqlanmoqda…"
                        else -> "Rasm belgilash"
                    },
                    Modifier.weight(1f),
                    enabled = photos.canAdd,
                ) { imagePicker.pick() }
                ActionTile(AppIcons.Pencil, "Axborotni tahrirlash", Modifier.weight(1f), onClick = onEditProfile)
                ActionTile(AppIcons.Settings, "Sozlamalar", Modifier.weight(1f), onClick = onOpenSettings)
            }

            photos.error?.let { ScText(it, 12.5f, FontWeight.SemiBold, Sc.Danger) }

            // --- Ma'lumotlar --------------------------------------------------------
            val university = state.universities
                .firstOrNull { it.id == state.profile?.universityId }?.name
            val phone = state.profile?.phoneNumber ?: state.contact.takeIf { it.isNotBlank() }
            InfoCard {
                phone?.let { InfoRow(it, "Mobil raqam") }
                state.profile?.bio?.takeIf { it.isNotBlank() }?.let { InfoRow(it, "Tarjimayi hol") }
                state.profile?.email?.takeIf { it.isNotBlank() }?.let { InfoRow(it, "Pochta") }
                university?.let { InfoRow(it, "Universitet") }
                state.courseLabel?.let { InfoRow(it, "Kurs") }
            }

            if (showMyBusiness) {
                MyBusinessCard(onOpenMyBusiness)
            }

            // --- Bo'limlar ----------------------------------------------------------
            TabBar(
                selected = tab,
                counts = mapOf(
                    ProfileTab.MY_ADS to state.myAds.size,
                    ProfileTab.SAVED to state.savedDiscounts.size,
                    ProfileTab.APPLICATIONS to state.applications.size,
                ),
                onSelect = { tab = it },
            )

            when (tab) {
                ProfileTab.MY_ADS -> if (state.myAds.isEmpty()) {
                    EmptySection("Hozircha e'lon yo'q…", "Qo'ygan e'lonlaringiz shu yerda ko'rinadi")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        state.myAds.forEach { ad ->
                            DeletableAdRow(
                                ad.title,
                                "${ad.category} · ${ad.price}",
                                ad.createdAgo,
                                onEdit = { onEditAd(ad.id) },
                                onDelete = { adToDelete = ad },
                            )
                        }
                    }
                }

                ProfileTab.SAVED -> if (state.savedDiscounts.isEmpty()) {
                    EmptySection("Saqlangan chegirma yo'q…", "Yoqqan chegirmani saqlab qo'ying")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        state.savedDiscounts.forEach {
                            SimpleRow(
                                "${it.merchant} — ${it.title}",
                                "−${it.discountPercent}%",
                                it.expiry ?: "",
                            )
                        }
                    }
                }

                ProfileTab.APPLICATIONS -> if (state.applications.isEmpty()) {
                    EmptySection("Ariza yo'q…", "Yuborgan ish arizalaringiz shu yerda ko'rinadi")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        state.applications.forEach {
                            SimpleRow(it.jobTitle, it.company, statusLabel(it.status.name))
                        }
                    }
                }
            }

            LogoutRow { vm.logout(onLoggedOut) }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
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
                TextButton(onClick = { vm.deleteAd(target.id); adToDelete = null }) {
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

// ---------------------------------------------------------------------------
// Sarlavha
// ---------------------------------------------------------------------------

/**
 * Gradient topbar ichida markazlashgan katta avatar, ism va holat — Telegram maketi.
 *
 * Holat matni doim «onlayn»: bu **mening** profilim, ilovani ochib turgan odamning o'zi.
 * Avatar bosilsa yangi rasm tanlanadi (u avtomatik asosiy bo'ladi).
 */
@Composable
private fun ProfileHeader(
    name: String,
    avatarUrl: String?,
    uploading: Boolean,
    /** Yuklash foizi; `null` — fayl ketib bo'lgan, server saqlamoqda. */
    progress: Float?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    ScHeader(horizontalPadding = 18.dp, bottomPadding = 26.dp) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
            Spacer(Modifier.weight(1f))
            ScCircleButton(AppIcons.Settings, onOpenSettings, contentDescription = "Sozlamalar")
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ScAvatar(
                    name = name,
                    size = 104.dp,
                    modifier = Modifier.clickable(enabled = !uploading, onClick = onAvatarClick),
                    avatarUrl = avatarUrl,
                    fontSize = 40f,
                    // Gradient ustida oq doira — maketdagi kabi kontrast.
                    background = Color.White.copy(alpha = 0.92f),
                    initialColor = Sc.Brand,
                )
                // Foiz avatar ustida: eski rasm ko'rinib turadi, yangisi esa ketmoqda.
                if (uploading) {
                    ScUploadRing(progress, size = 104.dp, stroke = 3.5.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            ScText(name, 22f, FontWeight.ExtraBold, Color.White, letterSpacing = -0.4f, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            ScText(
                when {
                    uploading && progress != null -> "rasm yuklanmoqda ${scUploadPercent(progress)}"
                    uploading -> "rasm saqlanmoqda…"
                    else -> "onlayn"
                },
                13.5f,
                FontWeight.Medium,
                Color.White.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bo'laklar
// ---------------------------------------------------------------------------

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(Sc.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (enabled) Sc.Brand else Sc.MutedLight, modifier = Modifier.size(21.dp))
        ScText(label, 11.5f, FontWeight.SemiBold, Sc.InkSoft, maxLines = 2)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) { content() }
}

@Composable
private fun InfoRow(value: String, label: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        ScText(value, 15f, FontWeight.Bold, Sc.Ink, maxLines = 3)
        Spacer(Modifier.height(2.dp))
        ScText(label, 12f, FontWeight.Medium, Sc.MutedLight)
    }
}

/** Bo'lim tanlagich — faol bo'lim brend rangida, yonida qatorlar soni. */
@Composable
private fun TabBar(
    selected: ProfileTab,
    counts: Map<ProfileTab, Int>,
    onSelect: (ProfileTab) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sc.Card).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProfileTab.entries.forEach { item ->
            val active = item == selected
            val count = counts[item] ?: 0
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Sc.Brand.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScText(
                    if (count > 0) "${item.label} $count" else item.label,
                    12.5f,
                    FontWeight.Bold,
                    if (active) Sc.Brand else Sc.Muted,
                    maxLines = 1,
                )
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

/** Bo'sh bo'lim — maketdagidek sarlavha va bir qatorlik izoh. */
@Composable
private fun EmptySection(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)
            .padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ScText(title, 16f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
        ScText(subtitle, 13f, FontWeight.Medium, Sc.MutedLight, maxLines = 2)
    }
}

// ---------------------------------------------------------------------------
// Ro'yxat qatorlari
// ---------------------------------------------------------------------------

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
