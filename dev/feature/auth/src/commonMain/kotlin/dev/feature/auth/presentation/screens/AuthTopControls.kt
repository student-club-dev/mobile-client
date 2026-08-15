package dev.feature.auth.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.common.locale.AppLanguage
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette
import dev.feature.settings.domain.model.ThemeMode

/**
 * Kirish/tanishtiruv ekranlarining yuqori boshqaruvlari — **til** va **mavzu**.
 *
 * Nega bu yerda: Sozlamalar ekrani faqat hisobga kirgandan keyin ochiladi, ya'ni ilovani
 * ilk marta ochgan odam interfeys tilini o'zgartira olmasdi va ro'yxatdan o'tishning
 * butun oqimini o'zi tushunmaydigan tilda bosib o'tardi.
 *
 * Ikkalasi ham `SettingsRepository` ga yoziladi — kirgandan keyin ham o'sha tanlov qoladi.
 */
@Composable
internal fun AuthTopControls(
    language: AppLanguage,
    themeMode: ThemeMode,
    onLanguage: (AppLanguage) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    palette: AppPalette = appPalette,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageSwitcher(language, onLanguage, palette)
        ThemeToggle(themeMode, onTheme, palette)
    }
}

/** Uch tilli segment — `EN · RU · UZ`. Ro'yxat qisqa, shuning uchun ochiluvchi menyu shart emas. */
@Composable
private fun LanguageSwitcher(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    palette: AppPalette,
) {
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(999.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppLanguage.entries.forEach { entry ->
            LanguageChip(entry, entry == current, { onSelect(entry) }, palette)
        }
    }
}

@Composable
private fun RowScope.LanguageChip(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    palette: AppPalette,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            language.code.uppercase(),
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 11.5f.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) palette.onPrimary else palette.inkMuted,
            ),
        )
    }
}

/**
 * Mavzu tugmasi — yorug'/qorong'i orasida almashadi.
 *
 * `SYSTEM` holatida qurilma rejimining TESKARISIga o'tadi: foydalanuvchi tugmani bosganda
 * ko'rinishning o'zgarishini kutadi, "tizimga ergash" holatida esa hech nima o'zgarmasdi.
 */
@Composable
private fun ThemeToggle(mode: ThemeMode, onSelect: (ThemeMode) -> Unit, palette: AppPalette) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> palette.dark
    }
    // Almashish paytidagi kichik burilish — holat o'zgargani sezilib tursin.
    val spin by animateFloatAsState(if (dark) 0f else 180f, label = "themeSpin")
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.glass)
            .border(1.dp, palette.border, RoundedCornerShape(999.dp))
            .clickable { onSelect(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (dark) AppIcons.Moon else AppIcons.Sun,
            null,
            tint = palette.inkMuted,
            modifier = Modifier.size(17.dp).rotate(spin),
        )
    }
}
