package dev.core.uikit.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * Tarmoqdan keladigan rasm — **yuklanayotganda kulrang shimmer** ko'rsatadi.
 *
 * Uch holat:
 * 1. yuklanmoqda → skelet (shimmer);
 * 2. muvaffaqiyatli → rasmning o'zi;
 * 3. xato yoki havola yo'q → [fallback] (emoji, bosh harf, ikona...).
 *
 * Shimmer rasm KELGUNCHA turadi, ya'ni karta hech qachon bo'sh kulrang to'rtburchak yoki
 * to'satdan "sakrab" chiqadigan rasm bo'lmaydi.
 */
@Composable
fun ScNetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    /** Rasm bo'lmaganda yoki yuklab bo'lmaganda chiziladigan zaxira (emoji/ikona). */
    fallback: @Composable () -> Unit = {},
) {
    Box(modifier.clip(shape)) {
        if (url.isNullOrBlank()) {
            fallback()
            return@Box
        }
        // `remember(url)` — havola o'zgarsa holat noldan boshlanadi (ro'yxatda karta qayta
        // ishlatilganda eski rasmning holati qolib ketmasin).
        var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

        when (state) {
            is AsyncImagePainter.State.Error -> fallback()
            is AsyncImagePainter.State.Success -> Unit // rasm o'zi pastda chiziladi
            else -> ScShimmerBox(Modifier.fillMaxSize(), shape)
        }

        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onState = { state = it },
        )
    }
}
