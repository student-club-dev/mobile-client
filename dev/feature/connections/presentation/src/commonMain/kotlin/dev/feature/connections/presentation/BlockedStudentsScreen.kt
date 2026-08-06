package dev.feature.connections.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScHeader
import dev.core.uikit.components.ScHeaderSubtitle
import dev.core.uikit.components.ScHeaderTitle
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.ScShimmerList
import dev.core.uikit.components.ScShimmerFooter
import dev.core.uikit.components.ScPullRefresh
import dev.core.uikit.theme.Sc
import dev.feature.connections.domain.model.BlockedStudent
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * **Bloklanganlar** — `GET /v1/blocks` ro'yxati (Sozlamalar → Maxfiylik → "Bloklangan talabalar").
 *
 * Ro'yxatda faqat foydalanuvchining O'ZI bloklaganlari bo'ladi. Buni sarlavha ostida ochiq
 * yozamiz: aks holda ekran "meni kim bloklagan" degan savolga javob berayotgandek tuyuladi,
 * server esa bunday ma'lumotni ataylab bermaydi.
 *
 * Presence (onlayn / oxirgi faollik) qatorda **ko'rsatilmaydi** — server bloklangan talabalar
 * uchun uni doim maskalaydi (`false`/`null`), ya'ni chizsak "hammasi oflayn" degan yolg'on
 * ma'lumot chiqardi.
 */
@Composable
fun BlockedStudentsScreen(
    onBack: () -> Unit = {},
    vm: BlockedStudentsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var unblockFor by remember { mutableStateOf<BlockedStudent?>(null) }

    val listState = rememberLazyListState()
    // Ro'yxat oxiriga yaqinlashganda keyingi sahifa. Takroriy chaqiruvlarni ViewModel
    // filtrlaydi (`hasNext` + yuklash bayrog'i), shuning uchun bu yerda shart sodda.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.hasNext) { if (nearEnd) vm.loadMore() }

    Box(Modifier.fillMaxSize().background(Sc.Bg)) {
        Column(Modifier.fillMaxSize()) {
            ScHeader {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    ScCircleButton(ScIcons.ChevronLeft, onBack, contentDescription = "Orqaga")
                    ScHeaderTitle("Bloklanganlar", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                ScHeaderSubtitle(
                    "Bu yerda faqat siz bloklagan talabalar. Sizni kim bloklagani ko'rsatilmaydi.",
                )
            }

            Spacer(Modifier.height(16.dp))

            val error = state.error
            when {
                // Birinchi yuklanish — qatorlarning skeleti (matnli "Yuklanmoqda…" emas).
                state.loading && state.items.isEmpty() -> ScShimmerList(
                    rows = 6,
                    modifier = Modifier.padding(horizontal = Sc.ScreenPadding),
                )

                error != null && state.items.isEmpty() -> ErrorBlock(error, vm::refresh)

                // Bloklangan yo'q — ro'yxat o'rniga hech nima chizilmaydi (sarlavha ostidagi
                // izoh bo'limning nimaligini allaqachon aytadi).
                state.isEmpty -> Unit

                // Tepadan tortish — ro'yxat birinchi sahifadan qayta o'qiladi.
                // `refreshing` — ro'yxat allaqachon to'la bo'lgandagi yuklanish: bo'sh
                // ro'yxatda skelet chiziladi va indikator ortiqcha bo'lardi.
                else -> ScPullRefresh(refreshing = state.loading, onRefresh = vm::refresh) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = Sc.ScreenPadding, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.total > 0) {
                            item(key = "total") {
                                ScText(
                                    "${state.total} ta talaba", 12.5f, FontWeight.Bold, Sc.Muted,
                                    Modifier.padding(start = 4.dp, bottom = 2.dp), maxLines = 1,
                                )
                            }
                        }
                        items(state.items, key = { it.student.id }) { blocked ->
                            BlockedRow(
                                blocked = blocked,
                                busy = blocked.student.id in state.busyIds,
                                onUnblock = { unblockFor = blocked },
                            )
                        }
                        if (state.loadingMore) {
                            item(key = "loading_more") {
                                ScShimmerFooter()
                            }
                        }
                        item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }

        // Bir martalik xabar — "Do'stlar" ekranidagi bilan bir xil naqsh (2.5 s dan keyin ketadi).
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

    val target = unblockFor
    if (target != null) {
        AlertDialog(
            onDismissRequest = { unblockFor = null },
            title = { Text("Blokdan chiqarish", style = scStyle(17f, FontWeight.ExtraBold)) },
            text = {
                Text(
                    "${target.student.displayName} blokdan chiqariladi va sizni qidiruvda " +
                        "yana ko'radi. Avvalgi bog'lanish tiklanmaydi — kerak bo'lsa qaytadan " +
                        "so'rov yuborasiz.",
                    style = scStyle(14f, FontWeight.Medium, Sc.InkSoft, lineHeight = 20f),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.unblock(target); unblockFor = null }) {
                    Text("Chiqarish", style = scStyle(14f, FontWeight.ExtraBold, Sc.Brand))
                }
            },
            dismissButton = {
                TextButton(onClick = { unblockFor = null }) {
                    Text("Bekor", style = scStyle(14f, FontWeight.Bold, Sc.InkSoft))
                }
            },
        )
    }
}

@Composable
private fun BlockedRow(blocked: BlockedStudent, busy: Boolean, onUnblock: () -> Unit) {
    val student = blocked.student
    Row(
        Modifier.fillMaxWidth().scCard(radius = 18.dp, elevation = 5.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ScAvatar(name = student.displayName, size = 46.dp, avatarUrl = student.avatarUrl)
        Column(Modifier.weight(1f)) {
            ScText(student.displayName, 14.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            // Presence YO'Q (server maskalaydi) — o'rniga blok sanasi va username.
            ScText(
                listOfNotNull(
                    student.username?.let { "@$it" },
                    "${formatDate(blocked.blockedAt)} dan beri bloklangan",
                ).joinToString(" · "),
                12f, FontWeight.Medium, Sc.MutedLight, maxLines = 1,
            )
        }
        UnblockButton(enabled = !busy, onClick = onUnblock)
    }
}

@Composable
private fun UnblockButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(if (enabled) Sc.Brand.copy(alpha = 0.12f) else Sc.Chip)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScText(
            "Blokdan chiqarish", 11.5f, FontWeight.ExtraBold,
            if (enabled) Sc.Brand else Sc.MutedLight, maxLines = 1,
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(ScIcons.Close, null, tint = Sc.Danger, modifier = Modifier.size(26.dp))
        ScText(message, 13.5f, FontWeight.Medium, Sc.Muted, lineHeight = 20f)
        Box(
            Modifier.clip(RoundedCornerShape(12.dp))
                .background(Sc.Brand)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 11.dp),
        ) { ScText("Qayta urinish", 12.5f, FontWeight.ExtraBold, Color.White, maxLines = 1) }
    }
}

/**
 * `12.07.2026`. `kotlinx-datetime` da KMP formatlagichi yo'q, shuning uchun qo'lda —
 * chat'dagi yorliqlar bilan bir xil uslub (kun.oy.yil, qurilma vaqt mintaqasida).
 */
private fun formatDate(instant: Instant): String {
    val d = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.dayOfMonth.pad()}.${d.monthNumber.pad()}.${d.year}"
}

private fun Int.pad(): String = if (this < 10) "0$this" else "$this"

/** Ro'yxat oxirigacha shuncha element qolganda keyingi sahifa so'raladi. */
private const val LOAD_MORE_THRESHOLD = 3
