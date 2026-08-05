package dev.core.uikit.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.core.uikit.theme.Sc

/**
 * "Tepadan pastga tortib yangilash" — ilovadagi **yagona** komponent.
 *
 * Har bir ro'yxatli ekran shu bilan o'raladi: foydalanuvchi qayerda bo'lmasin, ekranni
 * pastga tortish o'sha ekrandagi ma'lumotni serverdan qayta o'qiydi. Ilgari yangilanish
 * faqat ekranga qayta kirish orqali bo'lardi va, masalan, bog'lanish so'rovi qabul
 * qilingandan keyin boshqa ekrandagi ro'yxat eski holida qolib ketardi.
 *
 * ⚠️ [content] ICHIDA scroll qiladigan element bo'lishi shart (`LazyColumn`,
 * `verticalScroll`): imo-ishora nested-scroll orqali keladi. Scroll qilinmaydigan
 * kontentda tortish umuman ishlamaydi — bu Compose'ning cheklovi, xato emas.
 *
 * Indikator brend rangida: sukutdagi Material rangi ilovaning ko'k palitrasi bilan
 * mos tushmaydi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Indikator shuncha pastda chiziladi — sarlavhasi (topbar) bor ekranlarda u
     * gradient panelning ostidan chiqib kelsin.
     */
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        contentAlignment = contentAlignment,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Sc.Card,
                color = Sc.Brand,
            )
        },
        content = content,
    )
}
