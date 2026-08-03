package dev.core.uikit.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS'da dumaloq video xabar hali tayyorlanmaydi.
 *
 * `AVAssetExportSession` **kesa olmaydi** — u faqat presetlar bilan o'lchamni
 * o'zgartiradi. Kvadratga kesish uchun `AVMutableVideoComposition` +
 * `AVMutableVideoCompositionLayerInstruction` (transform matritsasi) kerak, bu esa
 * alohida ish. Shungacha `null` qaytaramiz: yuborish **boshlanmaydi** va foydalanuvchi
 * aniq xato ko'radi — kvadrat bo'lmagan fayl serverga ketib, `422 MEDIA_NOT_SQUARE`
 * bilan qaytishidan yaxshiroq.
 */
@Composable
actual fun rememberVideoNotePreparer(): VideoPreparer = remember { VideoPreparer { _, _ -> null } }
