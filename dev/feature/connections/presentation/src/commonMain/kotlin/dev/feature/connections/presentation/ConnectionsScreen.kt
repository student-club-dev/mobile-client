package dev.feature.connections.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScEmptyState
import dev.core.uikit.components.ScEmptyTitle
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScNotFoundTitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.ScShimmerList
import dev.core.uikit.components.ScPullRefresh
import dev.core.uikit.theme.Sc
import dev.feature.connections.domain.model.ConnectionRequest
import dev.feature.connections.domain.model.ConnectionView
import dev.feature.connections.domain.model.Gender
import dev.feature.connections.domain.model.ReportReason
import dev.feature.connections.domain.model.StudentSort
import dev.feature.connections.domain.model.StudentSummary
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.uikit.locale.uiStrings

/**
 * **"Do'stlar"** — `Connections` bo'limining ekrani (handoff: `connections.md` §14).
 *
 * Uch bo'lim: **Qidiruv** (talaba topish + bog'lanish), **So'rovlar** (kiruvchi/chiquvchi),
 * **Do'stlar** (bog'langanlar). Har qatorning "⋮" menyusida bloklash va shikoyat.
 *
 * [onOpenChat] — bog'langan talaba bilan suhbatni ochadi (`POST /v1/conversations` chat
 * ekranida bajariladi; bu yerdan faqat `studentId` uzatiladi).
 */
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit = {},
    onOpenChat: (studentId: String, name: String) -> Unit = { _, _ -> },
    /**
     * Talaba bosildi — profil varag'ini **karkas** ochadi (`StudentShell`).
     *
     * Nega bu yerda emas: varaqning bo'limlari (Postlar/Media/Fayllar/Havolalar) story va
     * chat modullarida yashaydi, bu modul esa ularni ko'rmaydi — ko'rsa bog'lanish halqasi
     * paydo bo'lardi (`StudentProfileSheet` izohiga q.).
     */
    onOpenStudent: (StudentSummary) -> Unit = {},
    /**
     * Ekran qaysi bo'lim ochilgan holda kelsin (Home'dagi tugmalar). `null` — sukut
     * bo'yicha "Do'stlar". Faqat BIR MARTA qo'llaniladi: aks holda foydalanuvchi tab'ni
     * qo'lda almashtirgach, har rekompozitsiyada u orqaga tortilib turardi.
     */
    initialTab: ConnectionsTab? = null,
    vm: ConnectionsViewModel = koinViewModel(),
) {
    LaunchedEffect(initialTab) { if (initialTab != null) vm.selectTab(initialTab) }

    val state by vm.state.collectAsStateWithLifecycle()
    val s = connectionsStrings()
    var menuFor by remember { mutableStateOf<StudentSummary?>(null) }
    var reportFor by remember { mutableStateOf<StudentSummary?>(null) }
    var disconnectFor by remember { mutableStateOf<StudentSummary?>(null) }
    var blockFor by remember { mutableStateOf<StudentSummary?>(null) }

    Box(Modifier.fillMaxSize().background(Sc.Bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScHeader {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = uiStrings().back)
                    ScHeaderTitle(s.title, modifier = Modifier.weight(1f))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabChip(s.tabConnections, state.tab == ConnectionsTab.CONNECTED, Modifier.weight(1f)) {
                    vm.selectTab(ConnectionsTab.CONNECTED)
                }
                TabChip(
                    s.tabRequests,
                    state.tab == ConnectionsTab.REQUESTS,
                    Modifier.weight(1f),
                    badge = state.incomingTotal,
                ) { vm.selectTab(ConnectionsTab.REQUESTS) }
                TabChip(s.tabSearch, state.tab == ConnectionsTab.SEARCH, Modifier.weight(1f)) {
                    vm.selectTab(ConnectionsTab.SEARCH)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Tepadan tortish — OCHIQ bo'lim serverdan qayta o'qiladi. Bog'lanish holati
            // ikki tomonlama: suhbatdosh so'rovni boshqa qurilmada qabul qilgan bo'lishi
            // mumkin va ekranga qaytmasdan buni bilishning boshqa yo'li yo'q edi.
            ScPullRefresh(
                refreshing = state.refreshing,
                onRefresh = vm::refreshCurrentTab,
                modifier = Modifier.weight(1f),
            ) {
                when (state.tab) {
                    ConnectionsTab.SEARCH -> SearchSection(
                        state = state,
                        onQuery = vm::onQueryChange,
                        onClear = vm::clearQuery,
                        onConnect = vm::connect,
                        onOpenChat = onOpenChat,
                        onMenu = { menuFor = it },
                        onOpenStudent = onOpenStudent,
                        vm = vm,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ConnectionsTab.REQUESTS -> RequestsSection(
                        state = state,
                        onSelect = vm::selectRequestsTab,
                        onAccept = vm::accept,
                        onDecline = vm::decline,
                        onMenu = { menuFor = it },
                        onOpenStudent = onOpenStudent,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ConnectionsTab.CONNECTED -> ConnectedSection(
                        state = state,
                        onOpenChat = onOpenChat,
                        onMenu = { menuFor = it },
                        onOpenStudent = onOpenStudent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Bir martalik xabar — 2.5 s dan keyin o'zi yo'qoladi.
        val message = state.message
        if (message != null) {
            LaunchedEffect(message) {
                delay(2_500)
                vm.messageShown()
            }
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = Sc.ScreenPadding, vertical = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sc.InkSurface.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { ScText(message, 13.5f, FontWeight.SemiBold, Color.White) }
        }
    }

    // --- Dialoglar ---------------------------------------------------------------------

    val menuTarget = menuFor
    if (menuTarget != null) {
        val connected = state.connections.any { it.student.id == menuTarget.id }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(menuTarget.displayName, style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (connected) {
                        ActionRow(ScIcons.Message, s.writeMessage) {
                            menuFor = null
                            onOpenChat(menuTarget.id, menuTarget.displayName)
                        }
                        ActionRow(ScIcons.Close, s.disconnect) {
                            menuFor = null
                            disconnectFor = menuTarget
                        }
                    }
                    ActionRow(ScIcons.Users, s.block, danger = true) {
                        menuFor = null
                        blockFor = menuTarget
                    }
                    ActionRow(ScIcons.Bell, s.report, danger = true) {
                        menuFor = null
                        reportFor = menuTarget
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuFor = null }) {
                    Text(s.cancel, style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }

    val disconnectTarget = disconnectFor
    if (disconnectTarget != null) {
        ConfirmDialog(
            title = s.disconnect,
            // Sovish muddati YO'Q — u faqat *rad etish* dan keyin bo'ladi (connections.md §8).
            message = s.disconnectBody(disconnectTarget.displayName),
            confirmLabel = s.disconnectConfirm,
            onConfirm = { vm.disconnect(disconnectTarget); disconnectFor = null },
            onDismiss = { disconnectFor = null },
        )
    }

    val blockTarget = blockFor
    if (blockTarget != null) {
        ConfirmDialog(
            title = s.block,
            message = s.blockBody(blockTarget.displayName),
            confirmLabel = s.block,
            onConfirm = { vm.block(blockTarget); blockFor = null },
            onDismiss = { blockFor = null },
        )
    }

    val reportTarget = reportFor
    if (reportTarget != null) {
        ReportDialog(
            name = reportTarget.displayName,
            onSend = { reason, note ->
                vm.report(reportTarget, reason, note)
                reportFor = null
            },
            onDismiss = { reportFor = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Bo'limlar
// ---------------------------------------------------------------------------

@Composable
private fun SearchSection(
    state: ConnectionsUiState,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onConnect: (StudentSummary) -> Unit,
    onOpenChat: (String, String) -> Unit,
    onMenu: (StudentSummary) -> Unit,
    onOpenStudent: (StudentSummary) -> Unit,
    vm: ConnectionsViewModel,
    modifier: Modifier = Modifier,
) {
    val s = connectionsStrings()
    Column(modifier.fillMaxWidth()) {
        SearchField(state.query, onQuery, onClear)
        Spacer(Modifier.height(10.dp))
        FilterBar(state, vm)
        Spacer(Modifier.height(12.dp))
        when {
            // Qidiruv natijalari o'rniga — o'sha qatorlarning skeleti.
            state.searching && state.results.isEmpty() -> ScShimmerList(
                rows = 6,
                modifier = Modifier.padding(horizontal = Sc.ScreenPadding),
            )
            state.searched && state.results.isEmpty() -> Hint(
                if (state.query.isNotBlank()) {
                    // Qidiruv `firstName` va `lastName` ni ALOHIDA tekshiradi — to'liq ism ishlamaydi.
                    s.searchOneWord
                } else {
                    s.noStudentsForFilters
                },
                icon = ScIcons.Search,
                title = ScNotFoundTitle,
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Sc.ScreenPadding, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.results, key = { it.student.id }) { result ->
                    PersonRow(
                        student = result.student,
                        subtitle = state.subtitleOf(result.student),
                        onMenu = { onMenu(result.student) },
                        onOpen = { onOpenStudent(result.student) },
                    ) {
                        val busy = result.student.id in state.busyIds
                        when (result.connectionStatus) {
                            ConnectionView.NONE -> PillButton(s.connect, enabled = !busy) {
                                onConnect(result.student)
                            }
                            // Yuborilgan so'rovni bekor qilish endpointi yo'q — tugma o'chirilgan.
                            ConnectionView.PENDING_OUT -> PillButton(s.requestSent, filled = false, enabled = false) {}
                            ConnectionView.PENDING_IN -> PillButton(s.respond, enabled = !busy) {
                                // Kiruvchi so'rov — "So'rovlar" bo'limida qabul qilinadi.
                                onConnect(result.student)
                            }
                            ConnectionView.CONNECTED -> PillButton(s.message, filled = false) {
                                onOpenChat(result.student.id, result.student.displayName)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * `GET /v1/students` filtrlari. Hammasi bir-birini toraytiradi (AND), bitta filtr ichida
 * esa OR — ya'ni "1-kurs" + "2-kurs" = birinchi **yoki** ikkinchi kurs.
 *
 * "Universitetim" faqat profilda universitet ko'rsatilgan bo'lsa chiqadi: serverda
 * universitetlar katalogi yo'q, filtr profildagi satrni aynan solishtiradi.
 */
@Composable
private fun FilterBar(state: ConnectionsUiState, vm: ConnectionsViewModel) {
    val s = connectionsStrings()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val mine = state.myUniversityId
        if (mine != null) {
            SmallChip(s.filterMyUniversity, mine in state.filter.universityIds, vm::toggleMyUniversity)
        }
        SmallChip(
            s.filterNewPeople,
            state.filter.connectionStatus == ConnectionView.NONE,
            vm::toggleOnlyNew,
        )
        SmallChip(s.filterMale, Gender.MALE in state.filter.genders) { vm.toggleGender(Gender.MALE) }
        SmallChip(s.filterFemale, Gender.FEMALE in state.filter.genders) { vm.toggleGender(Gender.FEMALE) }
        courseOptions().forEach { (value, label) ->
            SmallChip(label, value in state.filter.courseYears) { vm.toggleCourseYear(value) }
        }
        SmallChip(s.sortByName, state.filter.sort == StudentSort.NAME) {
            vm.setSort(if (state.filter.sort == StudentSort.NAME) StudentSort.RECENT else StudentSort.NAME)
        }
        if (state.hasActiveFilters) SmallChip(s.clear, active = false, onClick = vm::clearFilters)
    }
}

/** Filtr chiplaridagi va qatordagi kurs yorliqlari — profildagi qiymatlar bilan bir xil. */
private fun courseOptions(): List<Pair<String, String>> {
    val s = connectionsStringsNow()
    return listOf(
        "1" to s.courseYear("1"),
        "2" to s.courseYear("2"),
        "3" to s.courseYear("3"),
        "4" to s.courseYear("4"),
        "MASTER" to s.masterDegree,
    )
}

/**
 * Qator ostidagi matn: `@username · TATU · 2-kurs`. Universitet **monogrammasi** local
 * katalogdan olinadi — backend qisqa profilda faqat `universityId` ni qaytaradi.
 */
private fun ConnectionsUiState.subtitleOf(student: StudentSummary): String? =
    listOfNotNull(
        student.username?.let { "@$it" },
        universityLabel(student),
        student.courseYear?.let { year -> courseOptions().firstOrNull { it.first == year }?.second },
    ).joinToString(" · ").ifBlank { null }

@Composable
private fun RequestsSection(
    state: ConnectionsUiState,
    onSelect: (RequestsTab) -> Unit,
    onAccept: (ConnectionRequest) -> Unit,
    onDecline: (ConnectionRequest) -> Unit,
    onMenu: (StudentSummary) -> Unit,
    onOpenStudent: (StudentSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = connectionsStrings()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallChip(s.incoming, state.requestsTab == RequestsTab.INCOMING) { onSelect(RequestsTab.INCOMING) }
            SmallChip(s.outgoing, state.requestsTab == RequestsTab.OUTGOING) { onSelect(RequestsTab.OUTGOING) }
        }
        Spacer(Modifier.height(12.dp))
        val list = state.requests
        // So'rov yo'q bo'lsa hech nima chizilmaydi — chiplarning ostida bo'sh joy qoladi.
        if (list.isNotEmpty()) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Sc.ScreenPadding, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.connectionId }) { request ->
                    val busy = request.student.id in state.busyIds
                    PersonRow(
                        student = request.student,
                        subtitle = state.subtitleOf(request.student),
                        onMenu = { onMenu(request.student) },
                        onOpen = { onOpenStudent(request.student) },
                    ) {
                        if (state.requestsTab == RequestsTab.INCOMING) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PillButton(s.accept, enabled = !busy) { onAccept(request) }
                                PillButton(s.decline, filled = false, enabled = !busy) { onDecline(request) }
                            }
                        } else {
                            PillButton(s.pending, filled = false, enabled = false) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedSection(
    state: ConnectionsUiState,
    onOpenChat: (String, String) -> Unit,
    onMenu: (StudentSummary) -> Unit,
    onOpenStudent: (StudentSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = connectionsStrings()
    if (state.connections.isEmpty()) {
        // Yuklanayotgan bo'lsa skelet; bo'sh bo'lsa umuman hech nima.
        if (state.loading) {
            ScShimmerList(rows = 6, modifier = modifier.padding(horizontal = Sc.ScreenPadding))
        }
        return
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Sc.ScreenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.connections, key = { it.student.id }) { connected ->
            PersonRow(
                student = connected.student,
                subtitle = state.subtitleOf(connected.student),
                onMenu = { onMenu(connected.student) },
                onOpen = { onOpenStudent(connected.student) },
            ) {
                PillButton(s.message) { onOpenChat(connected.student.id, connected.student.displayName) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Elementlar
// ---------------------------------------------------------------------------

@Composable
private fun PersonRow(
    student: StudentSummary,
    subtitle: String?,
    onMenu: () -> Unit,
    onOpen: () -> Unit,
    action: @Composable () -> Unit,
) {
    Row(
        // Qatorning O'ZI profilni ochadi. Amal tugmalari ("Xabar", "Qabul") va menyu
        // o'z bosishlarini yutadi, ya'ni ular bilan to'qnashmaydi.
        Modifier.fillMaxWidth().scCard(radius = 18.dp, elevation = 5.dp)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ScAvatar(name = student.displayName, size = 46.dp, avatarUrl = student.avatarUrl)
        Column(Modifier.weight(1f)) {
            ScText(student.displayName, 14.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                ScText(subtitle, 12f, FontWeight.Medium, Sc.MutedLight, maxLines = 1)
            }
        }
        action()
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(percent = 50)).clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) { Icon(ScIcons.DotsVertical, connectionsStrings().menu, tint = Sc.MutedLight, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(Sc.FieldBg)
            .border(1.dp, Sc.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ScIcons.Search, null, tint = Sc.Muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) ScText(connectionsStrings().searchHint, 14.5f, FontWeight.Medium, Sc.NavIdle, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = scStyle(14.5f, FontWeight.Medium, Sc.Ink),
                cursorBrush = SolidColor(Sc.Brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(percent = 50)).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { Icon(ScIcons.Close, connectionsStrings().clear, tint = Sc.Muted, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) Sc.Brand else Sc.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        ScText(label, 12.5f, FontWeight.Bold, if (active) Color.White else Sc.ChipInk, maxLines = 1)
        if (badge > 0) {
            Box(
                Modifier.size(17.dp)
                    .background(if (active) Color.White else Sc.Danger, RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) { ScText("$badge", 9.5f, FontWeight.ExtraBold, if (active) Sc.Brand else Color.White) }
        }
    }
}

@Composable
private fun SmallChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) Sc.Brand else Sc.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) { ScText(label, 12.5f, FontWeight.Bold, if (active) Color.White else Sc.ChipInk, maxLines = 1) }
}

@Composable
private fun PillButton(
    label: String,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Sc.Chip
        filled -> Sc.Brand
        else -> Sc.Brand.copy(alpha = 0.12f)
    }
    val fg = when {
        !enabled -> Sc.MutedLight
        filled -> Color.White
        else -> Sc.Brand
    }
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { ScText(label, 11.5f, FontWeight.ExtraBold, fg, maxLines = 1) }
}

@Composable
private fun Hint(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    title: String = ScEmptyTitle,
) {
    ScEmptyState(title = title, message = text, icon = icon)
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) Sc.Danger else Sc.Brand
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        ScText(label, 14f, FontWeight.Bold, if (danger) tint else Sc.Ink)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = scStyle(17f, FontWeight.ExtraBold)) },
        text = { Text(message, style = scStyle(14f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = scStyle(14f, FontWeight.ExtraBold, Sc.Danger))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(connectionsStrings().cancel, style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
}

/** Shikoyat dialogi — sabab (majburiy) + ixtiyoriy izoh (≤1000 belgi). */
@Composable
private fun ReportDialog(
    name: String,
    onSend: (ReportReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = connectionsStrings()
    var reason by remember { mutableStateOf(ReportReason.SPAM) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.reportTitle(name), style = scStyle(17f, FontWeight.ExtraBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ReportReason.entries.forEach { r ->
                        SmallChip(r.label, reason == r) { reason = r }
                    }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Sc.FieldBg)
                        .border(1.dp, Sc.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    if (note.isEmpty()) ScText(s.reportNote, 13.5f, FontWeight.Medium, Sc.NavIdle)
                    BasicTextField(
                        value = note,
                        onValueChange = { if (it.length <= 1000) note = it },
                        textStyle = scStyle(13.5f, FontWeight.Medium, Sc.Ink),
                        cursorBrush = SolidColor(Sc.Brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(reason, note.takeIf { it.isNotBlank() }) }) {
                Text(s.reportSend, style = scStyle(14f, FontWeight.ExtraBold, Sc.Danger))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(connectionsStrings().cancel, style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
            }
        },
    )
}

private val ReportReason.label: String
    @Composable @ReadOnlyComposable get() = connectionsStrings().let {
        when (this) {
            ReportReason.SPAM -> it.reasonSpam
            ReportReason.SCAM -> it.reasonFraud
            ReportReason.HARASSMENT -> it.reasonHarassment
            ReportReason.INAPPROPRIATE -> it.reasonInappropriate
            ReportReason.OTHER -> it.reasonOther
        }
    }
