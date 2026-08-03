package dev.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScGlassButton
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScProfileHeader
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.rememberScCollapsingHeaderState
import dev.core.uikit.components.scUploadPercent
import dev.core.uikit.components.scCard
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.theme.Sc
import dev.feature.stories.presentation.MyPostsSection
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profil bo'limlari — Telegramdagi kabi **postlar**.
 *
 * Post = lavha (`feature:stories`): 24 soat bog'langanlarga ko'rinadi, keyin yo'qolmaydi —
 * faqat egasiga ko'rinadigan [ARCHIVE] ga o'tadi (`STORY_ARCHIVE_BACKEND.md`).
 */
private enum class ProfileTab(val label: String) {
    POSTS("Postlar"),
    ARCHIVE("Arxivlangan postlar"),
}

/**
 * **O'z profilim** — suhbatdosh profili (`PeerProfileSheet`) bilan bir xil Telegram
 * maketida: yig'iluvchi sarlavha, uch amal tugmasi, ma'lumot kartasi va bo'limlar.
 *
 * Sarlavha [ScProfileHeader] — ro'yxat tepasida turib pastga tortilsa avatar butun
 * kenglikni egallaydigan rasmga aylanadi (bir nechta rasm bo'lsa chap/o'ng yarmiga tegib
 * ular orasida yuriladi).
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onEditProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** "Mening biznesim" — chegirma e'lonlari (feature:discounts). */
    onOpenMyBusiness: () -> Unit = {},
    /** Talaba shell'ida biznes kartasi yashiriladi — biznesmenda o'zining alohida bo'limi bor. */
    showMyBusiness: Boolean = true,
    vm: ProfileViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(ProfileTab.POSTS) }

    // Avatar profil rasmlari to'plamidan olinadi: yangi rasm qo'yilgach `avatarUrl` faqat
    // profil qayta yuklangandan keyin yangilanadi, to'plam esa darhol.
    LaunchedEffect(Unit) { vm.loadPhotos() }
    val photoUrls = remember(photos.items, state.profile?.avatarUrl) {
        photos.items.map { it.url }.ifEmpty {
            listOfNotNull(state.profile?.avatarUrl?.takeIf { it.isNotBlank() })
        }
    }
    var photoIndex by remember(photoUrls.size) { mutableIntStateOf(0) }

    // «Rasm belgilash» — yangi rasm DOIM birinchi o'ringa tushadi, ya'ni avatar bo'ladi.
    val imagePicker = rememberImagePicker { picked ->
        if (picked == null) return@rememberImagePicker
        vm.addPhoto(picked.bytes, picked.fileName)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Sc.Bg)) {
        // Yoyilgan sarlavha — kvadrat: balandligi ekran kengligiga teng.
        val header = rememberScCollapsingHeaderState(
            collapsedHeight = COLLAPSED_HEADER,
            expandedHeight = maxWidth,
            // Rasm bo'lmasa yoyadigan narsa ham yo'q — bosh harfni butun ekranga
            // cho'zish faqat g'alati ko'rinardi.
            expandable = photoUrls.isNotEmpty(),
        )

        Column(
            Modifier.fillMaxSize()
                .nestedScroll(header.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            ScProfileHeader(
                state = header,
                name = state.name,
                // Bu **mening** profilim — ilovani ochib turgan odamning o'zi.
                status = when {
                    photos.uploading && photos.progress != null ->
                        "rasm yuklanmoqda ${scUploadPercent(photos.progress!!)}"
                    photos.uploading -> "rasm saqlanmoqda…"
                    else -> "onlayn"
                },
                photoUrls = photoUrls,
                photoIndex = photoIndex,
                onAvatarClick = {
                    when {
                        // Rasm umuman yo'q — bosish darrov tanlagichni ochadi.
                        photoUrls.isEmpty() -> if (photos.canAdd) imagePicker.pick()
                        !header.expanded -> header.expand()
                        else -> Unit
                    }
                },
                onStep = { forward ->
                    if (photoUrls.size > 1) {
                        photoIndex = (photoIndex + if (forward) 1 else photoUrls.size - 1) %
                            photoUrls.size
                    }
                },
                topBar = { ScGlassButton(ScIcons.ChevronLeft, "Orqaga", onBack) },
                trailing = { ScGlassButton(AppIcons.Settings, "Sozlamalar", onOpenSettings) },
                avatarOverlay = {
                    // Foiz avatar ustida: eski rasm ko'rinib turadi, yangisi esa ketmoqda.
                    if (photos.uploading) {
                        ScUploadRing(photos.progress, size = 96.dp, stroke = 3.5.dp)
                    }
                },
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
                    ActionTile(AppIcons.Pencil, "Tahrirlash", Modifier.weight(1f), onClick = onEditProfile)
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
                TabBar(selected = tab, onSelect = { tab = it })

                MyPostsSection(
                    archived = tab == ProfileTab.ARCHIVE,
                    authorName = state.name,
                    authorAvatarUrl = photoUrls.firstOrNull(),
                )

                LogoutRow { vm.logout(onLoggedOut) }
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
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

/** Bo'lim tanlagich — faol bo'lim brend rangida. */
@Composable
private fun TabBar(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sc.Card).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProfileTab.entries.forEach { item ->
            val active = item == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Sc.Brand.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScText(
                    item.label,
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

/** Yig'ilgan sarlavha balandligi — avatar, ism va holat sig'adigan eng kichik o'lcham. */
private val COLLAPSED_HEADER = 250.dp
