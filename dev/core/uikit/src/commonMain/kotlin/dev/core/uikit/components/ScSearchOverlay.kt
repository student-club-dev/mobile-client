package dev.core.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.core.uikit.theme.Sc

/**
 * Klaviatura USTIDA suzuvchi qidiruv qatlami — ilovadagi barcha ekranlar uchun yagona
 * qidiruv ko'rinishi (E'lonlar, "Siz uchun"...).
 *
 * Ochilganda tizim klaviaturasi ko'tariladi va uning ustida yumaloq (pill) maydon turadi.
 * Dizayn talabi bo'yicha orqa fon **qoraytirilmaydi** — tepasiga bosilsa qidiruv yopiladi.
 *
 * @param suggestions maydon ostidagi tayyor so'rovlar tasmasi (bo'sh — tasma chizilmaydi).
 * @param onSuggestionPick taklif bosilganda; sukut bo'yicha matnni qatorga qo'yadi.
 */
@Composable
fun ScSearchOverlay(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Qidirish…",
    suggestions: List<String> = emptyList(),
    onSuggestionPick: (String) -> Unit = onQuery,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.fillMaxSize().imePadding()) {
        // Tepadagi bo'sh joy — bosilsa yopiladi (fon qoraytirilmaydi).
        Box(Modifier.fillMaxWidth().weight(1f).clickable { keyboard?.hide(); onClose() })

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pill = RoundedCornerShape(50.dp)
            Row(
                Modifier.weight(1f)
                    .shadow(16.dp, pill, ambientColor = Color(0xFF0B1622), spotColor = Color(0xFF0B1622))
                    .clip(pill)
                    .background(Sc.Card)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(ScIcons.Search, null, tint = Sc.InkSoft, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        ScText(placeholder, 17f, FontWeight.Normal, Sc.NavIdle, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = scStyle(17f, FontWeight.Medium, Sc.Ink),
                        cursorBrush = SolidColor(Sc.Brand),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onClose() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(22.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Sc.Handle)
                            .clickable { onQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Kulrang doira ustidagi "×": yorug'da oq, to'qda esa oqish matn rangi
                        // (to'q rejimda doira ham to'qlashadi va oq belgi ko'zni qamashtirardi).
                        Icon(
                            ScIcons.Close, "Tozalash",
                            tint = if (Sc.IsDark) Sc.Ink else Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                } else {
                    Icon(ScIcons.Mic, null, tint = Sc.Muted, modifier = Modifier.size(19.dp))
                }
            }
            ScCircleButton(
                ScIcons.Close, { keyboard?.hide(); onClose() },
                size = 48.dp, background = Sc.Card.copy(alpha = 0.92f),
                tint = Sc.InkSoft, contentDescription = "Yopish",
            )
        }

        // Takliflar tasmasi — klaviatura ustida (iOS uslubidagi kulrang tasma).
        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Sc.Handle).height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Uch-to'rttadan ortig'i tasmaga sig'maydi — qolgani kesiladi.
                suggestions.take(MAX_SUGGESTIONS).forEachIndexed { index, suggestion ->
                    if (index > 0) {
                        Box(Modifier.width(1.dp).height(22.dp).background(Sc.MutedLight.copy(alpha = 0.5f)))
                    }
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .clickable { onSuggestionPick(suggestion); keyboard?.hide(); onClose() },
                        contentAlignment = Alignment.Center,
                    ) { ScText(suggestion, 15.5f, FontWeight.Medium, Sc.Ink, maxLines = 1) }
                }
            }
        }
        // Klaviatura yopiq bo'lsa (masalan planshetda) — tizim panel joyini qoldiramiz.
        Box(Modifier.fillMaxWidth().navigationBarsPadding())
    }
}

/** Tasmaga sig'adigan takliflar soni. */
private const val MAX_SUGGESTIONS = 3
