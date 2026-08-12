package dev.feature.connections.presentation

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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.common.format.formatUzPhoneFull
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScGlassButton
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScProfileHeader
import dev.core.uikit.components.ScText
import dev.core.uikit.components.rememberScCollapsingHeaderState
import dev.core.uikit.theme.Sc
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.Gender
import dev.feature.connections.domain.model.StudentSummary
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import dev.core.uikit.locale.uiStrings
import dev.core.common.locale.AppLocale
import androidx.compose.runtime.ReadOnlyComposable

/**
 * **Boshqa talabaning profili** — Telegram maketida: yig'iluvchi sarlavha (pastga tortilsa
 * rasm butun ekranni egallaydi), ma'lumot kartasi va amallar.
 *
 * Story ko'ruvchisida muallif ustiga bosilganda ochiladi. Chatdagi `PeerProfileSheet` dan
 * farqi — u **suhbatga** bog'langan (media/fayl/havola bo'limlari suhbat tarixidan keladi),
 * bu esa faqat odamning o'ziga: suhbat umuman bo'lmasa ham ishlaydi.
 */
@Composable
fun StudentProfileSheet(
    studentId: String,
    onClose: () -> Unit,
    /** «Xabar» tugmasi — suhbatni ochish (`null` bo'lsa tugma chizilmaydi). */
    onOpenChat: ((String) -> Unit)? = null,
    /**
     * Bo'limlar (tab) — **chaqiruvchi beradi**.
     *
     * Nega bu yerda emas: «Postlar» story modulida, «Media/Fayllar/Havolalar» esa chatda
     * yashaydi. Ularni shu faylga olib kirsak, `connections` ikkala modulga bog'lanardi va
     * bog'lanish halqasi paydo bo'lardi (story allaqachon shu varaqni ishlatadi).
     */
    sections: List<ProfileSection> = emptyList(),
    /**
     * Suhbat konteksti bo'lsa — holat qatori («yozmoqda…», «ulanmoqda…»). `null` bo'lsa
     * onlayn/oxirgi faollik profilning o'zidan olinadi.
     */
    statusOverride: String? = null,
    /** Hali tayyor bo'lmagan amallar («Sukut qilish», «Chaqiruv»…). */
    onSoon: (String) -> Unit = {},
    /** Shikoyat — chaqiruvchining o'z oynasi bilan (`null` bo'lsa qator chizilmaydi). */
    onReport: (() -> Unit)? = null,
    /** Bog'lanishni uzish/bloklash tasdiqlash oynasi chaqiruvchida bo'lsa — shular. */
    onDisconnect: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    /** Yoyilgan avatarga bosilganda — to'liq ekranli ko'rgich (chatdagi kabi). */
    onOpenPhoto: ((Int) -> Unit)? = null,
    /** Chaqiruvchida allaqachon bor qisqa profil — varaq bo'sh holatda ochilmasin. */
    known: StudentSummary? = null,
    vm: StudentProfileViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = connectionsStrings()
    val ui = uiStrings()
    // Status matni `when` ichida — u yerda Composable chaqirilmaydi, shuning uchun
    // qiymatlar oldindan olinadi (`onSoon` lambdalari uchun ham shunday).
    val profileOnline = profileOnlineLabel()
    val muteSoon = s.muteSoon
    val callSoon = s.callSoon
    val videoSoon = s.videoSoon
    LaunchedEffect(studentId) { vm.load(studentId, known) }
    // Bog'lanish uzilgan yoki odam bloklangan — ko'rsatadigan narsa qolmadi.
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    val student = state.student
    val photos = remember(student) {
        student?.photos?.map { it.url }
            ?.ifEmpty { listOfNotNull(student.avatarUrl?.takeIf { url -> url.isNotBlank() }) }
            .orEmpty()
    }
    var photoIndex by remember(photos.size) { mutableIntStateOf(0) }
    // Tanlov raqam bilan emas, **yorliq** bilan eslab qolinadi.
    //
    // Bo'limlar bir vaqtda kelmaydi: postlar bitta so'rovdan, media/fayl/havolalar
    // boshqasidan. Ro'yxat kengayganda raqamli indeks boshqa bo'limni ko'rsatib qolardi
    // (yoki `remember(sections.size)` uni nolga qaytarib, foydalanuvchi ochgan bo'limni
    // almashtirib yuborardi) — ya'ni ekran barmoq ostida o'zgarardi. Yorliq esa bo'lim
    // qayerga surilishidan qat'i nazar o'shaligicha qoladi.
    var selectedLabel by remember(student?.id) { mutableStateOf<String?>(null) }
    val tab = sections.indexOfFirst { it.label == selectedLabel }.takeIf { it >= 0 } ?: 0

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Sc.Bg)) {
            val header = rememberScCollapsingHeaderState(
                collapsedHeight = COLLAPSED_HEADER,
                expandedHeight = maxWidth,
                expandable = photos.isNotEmpty(),
            )

            // Sarlavha aylanadigan qismning ICHIDA emas, USTIDA — Telegramdagidek
            // **mixlangan**: u yig'ilib topbarga aylanadi, lekin hech qachon ekrandan
            // surilib ketmaydi. Ilgari u ustunning birinchi bolasi edi, ya'ni topbargacha
            // yig'ilgach ro'yxat bilan birga yuqoriga chiqib ketardi va orqaga tugmasi
            // ham u bilan yo'qolardi.
            Column(Modifier.fillMaxSize()) {
                ScProfileHeader(
                    state = header,
                    name = student?.displayName ?: ui.student,
                    status = statusOverride ?: when {
                        state.loading -> s.loadingProfile
                        student?.online == true -> profileOnline
                        else -> lastSeenLabel(student?.lastSeenAt)
                    },
                    photoUrls = photos,
                    photoIndex = photoIndex,
                    initialColor = Sc.Violet,
                    onAvatarClick = {
                        when {
                            // Yig'ilgan holatda bosish — yoyadi; yoyilganda — to'liq ekran.
                            !header.expanded -> header.expand()
                            else -> onOpenPhoto?.invoke(photoIndex)
                        }
                    },
                    onStep = { forward ->
                        if (photos.size > 1) {
                            photoIndex = (photoIndex + if (forward) 1 else photos.size - 1) % photos.size
                        }
                    },
                    topBar = { ScGlassButton(ScIcons.ChevronLeft, ui.back, onClose) },
                )

                Column(
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        // Aylanish shu yerda: sarlavha `nestedScroll` orqali avval o'zi
                        // yig'iladi, kontent esa undan keyin suriladi.
                        .nestedScroll(header.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(2.dp))

                    // --- Amallar — bog'lanish holatiga qarab -----------------------------
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        when (state.connection) {
                            // Bog'langanmiz — suhbat ochiladi (chat eshigi aynan shu).
                            ConnectionView.CONNECTED -> onOpenChat?.let { open ->
                                ProfileTile(ScIcons.ChatRound, s.message, Modifier.weight(1f)) { open(studentId) }
                            }
                            ConnectionView.NONE -> ProfileTile(
                                ScIcons.Users,
                                s.connect,
                                Modifier.weight(1f),
                                onClick = vm::connect,
                            )
                            // Kutilayotgan so'rovga bu yerdan javob berilmaydi — u «Do'stlar»
                            // ekranidagi so'rovlar bo'limining ishi.
                            ConnectionView.PENDING_OUT -> ProfileTile(
                                ScIcons.Bell,
                                s.requestSent,
                                Modifier.weight(1f),
                                enabled = false,
                            ) {}
                            ConnectionView.PENDING_IN -> ProfileTile(
                                ScIcons.Bell,
                                s.requestedYou,
                                Modifier.weight(1f),
                                enabled = false,
                            ) {}
                        }
                        // Hali tayyor bo'lmagan amallar — chatdagi varaqdagi bilan bir xil
                        // to'rtlik saqlanadi, aks holda profil chatdan ochilganda tugmalar
                        // "yo'qolgandek" ko'rinardi.
                        ProfileTile(ScIcons.Bell, s.mute, Modifier.weight(1f)) {
                            onSoon(muteSoon)
                        }
                        ProfileTile(ScIcons.PhoneCall, s.call, Modifier.weight(1f)) {
                            onSoon(callSoon)
                        }
                        ProfileTile(AppIcons.Camera, s.video, Modifier.weight(1f)) {
                            onSoon(videoSoon)
                        }
                    }

                    // --- Ma'lumotlar -----------------------------------------------------
                    if (student != null) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
                            student.username?.takeIf { it.isNotBlank() }?.let {
                                InfoLine("@$it", s.usernameLabel)
                            }
                            // ⚠️ Telefon ko'pincha `null`: sukut sozlama — `NOBODY`.
                            student.phoneNumber?.let { InfoLine(formatUzPhoneFull(it), s.phoneLabel) }
                            student.bio?.takeIf { it.isNotBlank() }?.let { InfoLine(it, s.bioLabel) }
                            state.universityName?.let { InfoLine(it, s.universityLabel) }
                            student.courseYear?.let { InfoLine(courseLabel(it), s.courseLabel) }
                            student.gender?.let {
                                InfoLine(if (it == Gender.MALE) s.genderMale else s.genderFemale, s.genderLabel)
                            }
                        }
                    }

                    // --- Bo'limlar: Postlar / Media / Fayllar / Havolalar ----------------
                    if (sections.isNotEmpty()) {
                        SectionTabs(
                            labels = sections.map { it.label },
                            selected = tab,
                            onSelect = { selectedLabel = sections[it].label },
                        )
                        sections[tab].content()
                    }

                    state.message?.let { ScText(it, 12.5f, FontWeight.SemiBold, Sc.Danger) }

                    // --- Xavfli amallar --------------------------------------------------
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sc.Card)) {
                        if (state.connection == ConnectionView.CONNECTED) {
                            DangerRow(ScIcons.Close, s.disconnect, onDisconnect ?: vm::disconnect)
                        }
                        DangerRow(ScIcons.Users, s.block, onBlock ?: vm::block, danger = true)
                        onReport?.let { DangerRow(ScIcons.Bell, s.report, it, danger = true) }
                    }

                    Spacer(Modifier.height(8.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(
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
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (enabled) Sc.Brand else Sc.MutedLight, modifier = Modifier.size(21.dp))
        ScText(label, 11.5f, FontWeight.SemiBold, Sc.InkSoft, maxLines = 1)
    }
}

@Composable
private fun InfoLine(value: String, label: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        ScText(value, 15f, FontWeight.Bold, Sc.Ink, maxLines = 3)
        Spacer(Modifier.height(2.dp))
        ScText(label, 12f, FontWeight.Medium, Sc.MutedLight)
    }
}

@Composable
private fun DangerRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) Sc.Danger else Sc.Ink
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        ScText(label, 14.5f, FontWeight.SemiBold, color)
    }
}

/**
 * «oxirgi faollik 12.07» — chatdagi sarlavha bilan bir xil ohangda.
 *
 * ⚠️ `null` — odam `lastSeenVisibility` ni yopgan yoki server bermagan; o'shanda «oflayn»
 * deb yozamiz, chunki aniq vaqtni bilmaymiz.
 */
private fun lastSeenLabel(instant: Instant?): String {
    if (instant == null) return AppLocale.pick(en = "offline", ru = "не в сети", uz = "oflayn")
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val day = "${date.dayOfMonth}.${date.monthNumber.toString().padStart(2, '0')}"
    return connectionsStringsNow().lastSeenOn(day)
}

private fun courseLabel(courseYear: String): String {
    val s = connectionsStringsNow()
    return when (courseYear.uppercase()) {
        "MASTER" -> s.masterDegree
        else -> s.courseYear(courseYear)
    }
}

/** «onlayn» — profil sarlavhasidagi holat. */
@Composable
@ReadOnlyComposable
private fun profileOnlineLabel(): String =
    AppLocale.pick(en = "online", ru = "в сети", uz = "onlayn")

/** Yig'ilgan sarlavha balandligi — chatdagi suhbatdosh varag'i bilan bir xil. */
private val COLLAPSED_HEADER = 250.dp

/**
 * Profildagi bitta bo'lim — sarlavhasi va mazmuni.
 *
 * Mazmun **lambda**: uni chaqiruvchi modul quradi, ya'ni bu varaq post/chat modullariga
 * bog'lanmaydi.
 */
@Immutable
data class ProfileSection(val label: String, val content: @Composable () -> Unit)

@Composable
private fun SectionTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sc.Card).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Sc.Brand.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScText(label, 12.5f, FontWeight.Bold, if (active) Sc.Brand else Sc.Muted, maxLines = 1)
            }
        }
    }
}
