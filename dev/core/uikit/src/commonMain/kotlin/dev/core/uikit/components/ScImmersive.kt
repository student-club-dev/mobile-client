package dev.core.uikit.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * "To'liq ekran" rejimi — ochiq bo'lganda karkasning pastki navigatsiya paneli chizilmaydi.
 *
 * Nega kerak: xarita ekranning O'ZI kontent — undagi har bir piksel mo'ljal. Pastki panel
 * esa xaritaning pastki qismini kesib, MapLibre'ning zoom va "mening joylashuvim"
 * tugmalariga yopishib turardi (bug hisoboti #24). Xaritada pastki panelning ma'nosi ham
 * yo'q: undan chiqish yo'li — o'zining orqaga tugmasi.
 *
 * Sanoq (counter) bayroq emas: bir vaqtda bir nechta to'liq ekranli qatlam ochilishi
 * mumkin (xarita ustidan rasm ko'ruvchi). Oxirgisi yopilgandagina panel qaytadi.
 */
object ScImmersive {
    private var depth by mutableIntStateOf(0)

    /** Hozir to'liq ekranli qatlam ochiqmi — karkas shu qiymatni o'qiydi. */
    val active: Boolean get() = depth > 0

    internal fun enter() { depth += 1 }
    internal fun exit() { depth = (depth - 1).coerceAtLeast(0) }
}

/**
 * Shu Composable kompozitsiyada turgan vaqtda pastki navigatsiya paneli yashiriladi.
 *
 * Chaqiruvchi hech narsa qilishi shart emas — qatlam yopilishi bilan panel o'zi qaytadi.
 */
@Composable
fun ScHideBottomBar() {
    DisposableEffect(Unit) {
        ScImmersive.enter()
        onDispose { ScImmersive.exit() }
    }
}
