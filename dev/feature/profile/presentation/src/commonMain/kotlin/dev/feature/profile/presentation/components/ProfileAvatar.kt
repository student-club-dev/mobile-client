package dev.feature.profile.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.core.uikit.components.ScAvatar

/**
 * Profil rasmi — ilovaning umumiy [ScAvatar] komponenti ustidagi yupqa qatlam.
 *
 * O'zi hech narsa chizmaydi: avatar mantiqi (local nusxa → serverdagi rasm → bosh harf)
 * bitta joyda turishi kerak, aks holda ikkita komponent vaqt o'tib bir-biridan ajralib
 * ketardi. Bu funksiya faqat mavjud chaqiruvlarni buzmaslik uchun saqlanib qolgan.
 */
@Composable
fun ProfileAvatar(
    name: String,
    size: Dp,
    fontSize: TextUnit,
    avatarUrl: String? = null,
    localPreview: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    ScAvatar(
        name = name,
        size = size,
        modifier = modifier,
        avatarUrl = avatarUrl,
        localPreview = localPreview,
        fontSize = fontSize.value,
    )
}
