package dev.core.uikit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.core.common.error.AppMessage
import dev.core.common.error.AppMessageBus
import dev.core.common.error.AppMessageKind
import dev.core.uikit.theme.Sc
import kotlinx.coroutines.delay

/**
 * Butun ilova ustidagi **toast qatlami** — [AppMessageBus] dagi har bir xabarni ko'rsatadi.
 *
 * Ildizda (`App.kt`) bir marta o'rnatiladi, ekranlar buning uchun hech nima qilmaydi: har
 * qanday API javobidagi xato `safeCall` orqali shinaga tushadi va shu yerda ko'rinadi.
 * Demak endi ekran xatoni "yutib yuborishi" mumkin emas.
 *
 * Xatti-harakati:
 * - yuqoridan sirg'alib chiqadi (status bar ostida), [TOAST_DURATION_MS] dan keyin o'zi ketadi;
 * - bosilsa darhol yopiladi;
 * - bir vaqtda ko'pi bilan [MAX_VISIBLE] ta ko'rinadi — eng eskisi siqib chiqariladi;
 * - **takrorlanmaydi**: ekrandagi xabar bilan bir xil matn qayta kelsa (masalan bir nechta
 *   so'rov birdaniga "Internet aloqasi yo'q" bilan qaytsa) yangi qator qo'shilmaydi, faqat
 *   taymer qaytadan boshlanadi.
 */
@Composable
fun BoxScope.ScToastHost() {
    val toasts = remember { mutableStateListOf<ToastItem>() }
    // ID uchun oddiy o'suvchi hisoblagich — vaqt/tasodifiy son kerak emas.
    var nextId by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        AppMessageBus.messages.collect { message ->
            val existing = toasts.indexOfFirst { it.message.text == message.text }
            nextId += 1
            if (existing >= 0) {
                // Bir xil matn — kartochka joyida qoladi, faqat "yana keldi" deb belgilanadi.
                toasts[existing] = toasts[existing].copy(id = nextId)
            } else {
                if (toasts.size >= MAX_VISIBLE) toasts.removeAt(0)
                toasts.add(ToastItem(nextId, message))
            }
        }
    }

    Column(
        Modifier.align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toasts.forEach { item ->
            // Kalit — MATN bo'yicha: takror kelgan xabar kartochkasi qayta yaratilmasin
            // (kirish animatsiyasi qaytadan o'ynamaydi), faqat taymeri yangilansin.
            key(item.message.text) {
                // Olib tashlash MATN bo'yicha, obyekt bo'yicha emas: chiqish animatsiyasi
                // paytida ayni xabar qayta kelsa qator yangi `id` bilan almashadi va eski
                // nusxa ro'yxatda topilmay, toast osilib qolardi.
                ToastCard(item, onRemove = { toasts.removeAll { it.message.text == item.message.text } })
            }
        }
    }
}

private data class ToastItem(val id: Long, val message: AppMessage)

@Composable
private fun ToastCard(item: ToastItem, onRemove: () -> Unit) {
    val accent = when (item.message.kind) {
        AppMessageKind.ERROR -> Sc.Danger
        AppMessageKind.SUCCESS -> Sc.Success
        AppMessageKind.INFO -> Sc.Brand
    }
    val shape = RoundedCornerShape(16.dp)

    // `AnimatedVisibility` faqat `visible` O'ZGARGANDA animatsiya qiladi, shuning uchun
    // boshlang'ich qiymat `false` — birinchi kadrdan keyin `true` bo'lib kirish o'ynaydi.
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    // Yashash davri. Kalit — `item.id`: bir xil xabar qayta kelsa taymer noldan boshlanadi.
    LaunchedEffect(item.id) {
        closing = false
        visible = true
        delay(TOAST_DURATION_MS)
        closing = true
    }
    // Chiqish: avval animatsiya o'ynaydi, ro'yxatdan KEYIN olib tashlanadi — aks holda
    // kartochka kompozitsiyani darhol tark etib, chiqish animatsiyasi umuman ko'rinmasdi.
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        visible = false
        delay(EXIT_DURATION_MS)
        onRemove()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.94f),
        exit = slideOutVertically { -it } + fadeOut(tween(EXIT_DURATION_MS.toInt())) +
            shrinkVertically(),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .widthIn(max = 560.dp)
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .background(Sc.Card)
                .border(1.dp, accent.copy(alpha = 0.35f), shape)
                .noRipple { closing = true },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ToastGlyph(item.message.kind, accent)
                }
                Spacer(Modifier.width(11.dp))
                ScText(
                    item.message.text,
                    size = 13f,
                    weight = FontWeight.SemiBold,
                    color = Sc.Ink,
                    lineHeight = 18f,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    AppIcons.Close, null, tint = Sc.MutedLight,
                    modifier = Modifier.size(14.dp).noRipple { closing = true },
                )
            }
            // Qolgan vaqtni ko'rsatuvchi ingichka chiziq — toast qachon ketishi ko'rinib turadi.
            TimerBar(item.id, accent)
        }
    }
}

/** Toast turi bo'yicha belgi: xato/ma'lumot — «!», muvaffaqiyat — ✓. */
@Composable
private fun ToastGlyph(kind: AppMessageKind, accent: Color) {
    if (kind == AppMessageKind.SUCCESS) {
        Icon(AppIcons.Check, null, tint = accent, modifier = Modifier.size(16.dp))
    } else {
        ScText("!", size = 15f, weight = FontWeight.ExtraBold, color = accent)
    }
}

/** [id] o'zgarganda (xabar qayta kelganda) chiziq to'lgan holatdan qaytadan boshlanadi. */
@Composable
private fun TimerBar(id: Long, accent: Color) {
    var started by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 0f else 1f,
        animationSpec = tween(TOAST_DURATION_MS.toInt(), easing = LinearEasing),
        label = "toast-timer",
    )
    Box(Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha = 0.10f))) {
        Box(Modifier.fillMaxWidth(progress).height(2.dp).background(accent.copy(alpha = 0.55f)))
    }
}

/** Toastda ripple ortiqcha — u tugma emas, faqat "bosib yopish" imkoni. */
@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Ekranda bir vaqtda ko'rinadigan toastlar soni. */
private const val MAX_VISIBLE = 3

private const val TOAST_DURATION_MS = 4_000L

/** Chiqish animatsiyasi — shu vaqtdan keyingina ro'yxatdan olib tashlanadi. */
private const val EXIT_DURATION_MS = 220L
