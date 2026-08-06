package dev.core.uikit.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue

/**
 * Ekran ICHIDA e'lon qilinadigan, lekin KARKAS ustida — pastki navigatsiya panelidan ham
 * yuqorida — chiziladigan qatlam.
 *
 * Nega kerak: ekranlar `NavHost` ichida yashaydi, pastki panel esa undan KEYIN chiziladi.
 * Ya'ni ekran ichidagi modal qatlam (yon panel, varaq) qanday tartibda qo'yilmasin, pastki
 * panel uning ustida qolaveradi — qorayish qatlami panelni qoraytirmaydi va uning tugmalari
 * bosilaveradi. `zIndex` yordam bermaydi: u faqat BITTA ota-layout ichida ishlaydi, bu yerda
 * esa qatlamlar boshqa-boshqa daraxtlarda.
 *
 * Yechim — chizishni yuqoriga ko'chirish: ekran nimani ko'rsatishni ayting ([ScOverlay]),
 * karkas esa uni o'zining eng oxirgi bolasi sifatida chizadi ([ScOverlayHost]).
 *
 * Holat ekranda qoladi: bu yerdan faqat CHIZUVCHI o'tadi, ya'ni qatlam o'z ekranining
 * ma'lumotini (ViewModel, `remember`) o'zgarishsiz o'qiyveradi.
 */
@Stable
class ScOverlayHostState {

    /**
     * Kalit → chizuvchi juftliklari, QO'SHILISH tartibida: keyin qo'shilgani ustida chiziladi.
     * Ro'yxat (map emas) — tartib kafolatlangan bo'lishi shart, aks holda ikki qatlam bir-birini
     * har rekompozitsiyada almashtirib turardi.
     */
    internal val slots = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()
}

/**
 * Joriy karkasning ustki qatlami. `null` — karkas yo'q (masalan alohida ekranda ko'rilgan
 * kompozitsiya): bunday holda [ScOverlay] kontentni o'z joyida chizadi.
 */
val LocalScOverlayHost = staticCompositionLocalOf<ScOverlayHostState?> { null }

/**
 * Qatlamlarni chizadi — karkasning ENG OXIRGI bolasi bo'lishi kerak (pastki paneldan keyin).
 */
@Composable
fun ScOverlayHost(state: ScOverlayHostState) {
    // Nusxa: ro'yxat chizish paytida o'zgarsa (qatlam o'zini olib tashlasa) aylanish uzilmasin.
    state.slots.toList().forEach { (slotKey, content) ->
        key(slotKey) { content() }
    }
}

/**
 * Kontentni karkasning ustki qatlamiga beradi — chaqirilgan JOYIDA hech narsa chizmaydi.
 *
 * [key] qatlamni ajratib turadi: bitta karkasda bir nechta qatlam bo'lishi mumkin va ular
 * kalit bo'yicha o'z holatini saqlaydi.
 *
 * Ekran kompozitsiyadan chiqishi bilan qatlam ham olib tashlanadi, ya'ni "osilib qolgan"
 * qatlam bo'lishi mumkin emas.
 */
@Composable
fun ScOverlay(key: Any, content: @Composable () -> Unit) {
    val host = LocalScOverlayHost.current
    if (host == null) {
        // Karkassiz ishlatilgan — eski xatti-harakat: o'z joyida chiziladi.
        content()
        return
    }
    // Chizuvchi bir marta ro'yxatga qo'yiladi, ichidagi `latest` esa har rekompozitsiyada
    // yangilanadi: aks holda qatlam ro'yxatga tushgan ondagi ESKI kontentni chizib qolardi.
    val latest by rememberUpdatedState(content)
    DisposableEffect(host, key) {
        val slot: Pair<Any, @Composable () -> Unit> = key to { latest() }
        host.slots += slot
        onDispose { host.slots -= slot }
    }
}
