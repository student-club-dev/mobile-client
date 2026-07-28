package dev.core.uikit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.core.uikit.theme.Sc

/**
 * Foydalanuvchi rasmi — ilova bo'ylab **yagona** avatar komponenti.
 *
 * Uch holat, shu tartibda:
 * 1. [localPreview] — endigina galereyadan tanlangan rasm (hali yuklanmagan), darrov ko'rinadi;
 * 2. [avatarUrl] — serverdagi rasm (`PUT /v1/profile/me` dagi `avatarUrl`), Coil yuklaydi;
 * 3. ikkalasi ham yo'q — [name] ning bosh harfi.
 *
 * Rasm yuklanayotgan yoki yuklab bo'lmagan paytda ham fon va bosh harf **o'z joyida qoladi**,
 * shuning uchun ro'yxat sakramaydi va bo'sh oq doira ko'rinmaydi.
 */
@Composable
fun ScAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    localPreview: ImageBitmap? = null,
    /** Bosh harf o'lchami. Berilmasa avatar kattaligidan hisoblanadi. */
    fontSize: Float = size.value * INITIAL_RATIO,
    background: Color = Sc.TintBlue,
    initialColor: Color = Sc.Brand,
    shape: Shape = RoundedCornerShape(percent = 50),
) {
    Box(
        modifier.size(size).clip(shape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        // Bosh harf DOIM chiziladi va rasm uning ustiga tushadi — shu bilan yuklanish
        // paytida ham, havola buzuq bo'lganda ham avatar bo'sh qolmaydi.
        ScText(name.initialOf(), fontSize, FontWeight.ExtraBold, initialColor, maxLines = 1)

        when {
            localPreview != null -> Image(
                bitmap = localPreview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            !avatarUrl.isNullOrBlank() -> AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Ismning bosh harfi. Bo'sh ism (backend `fullName` ni bermasligi mumkin) — `?`, aks holda
 * ro'yxatdagi doira umuman bo'sh ko'rinardi.
 */
private fun String.initialOf(): String =
    trim().firstOrNull()?.uppercase() ?: "?"

/** Bosh harf avatar diametrining ~40% i — barcha o'lchamlarda muvozanatli ko'rinadi. */
private const val INITIAL_RATIO = 0.4f

/** Ro'yxat qatorlaridagi odatiy avatar o'lchami. */
val ScAvatarSize: Dp = 46.dp
