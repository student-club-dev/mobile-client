package dev.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import dev.core.data.seed.LocalDataSeeder
import dev.core.network.NetworkConfig
import dev.core.network.media.MediaUrl
import dev.core.uikit.generated.resources.Res
import dev.core.uikit.theme.AppTheme
import dev.feature.settings.domain.model.ThemeMode
import dev.feature.settings.domain.repository.SettingsRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.feature.auth.presentation.flow.AuthNavHost
import dev.core.uikit.theme.appPalette
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

/** Ilovaning ildiz Composable'i — Android MainActivity ham, iOS ham shuni ishlatadi. */
@Composable
fun App() {
    AppScaffold { AuthNavHost() }
}

/** Umumiy ildiz sozlamasi (rasm yuklovchi, seed, mavzu, inset) — hamma kirish nuqtalari ishlatadi. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun AppScaffold(content: @Composable () -> Unit) {
    // Tarmoqdan rasm yuklash — Coil ilovaning o'z Ktor klientidan foydalanadi, shunda
    // so'rovlarga sessiya tokeni ham qo'shiladi (himoyalangan rasm URL'lari uchun).
    val httpClient = koinInject<HttpClient>()
    // API manzilining origin qismi (`/v1/` siz) — buzuq havolalarni tuzatish uchun.
    val apiOrigin = koinInject<NetworkConfig>().baseUrl.substringBefore("/v1")
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                // ⚠️ Mapper fetcher'DAN OLDIN: backend rasm havolasini o'z ichki manzili
                // bilan qaytarishi mumkin (`http://localhost:3000/uploads/…`) yoki nisbiy
                // yo'l berishi mumkin. Tuzatilmasa Android'da BITTA HAM rasm ko'rinmaydi:
                // `localhost` — telefonning o'zi, `http://` esa cleartext sifatida bloklanadi.
                add(MediaUrlMapper(apiOrigin))
                add(KtorNetworkFetcherFactory(httpClient))
            }
            .build()
    }

    // Local bazani dizayndagi namuna ma'lumot bilan to'ldiramiz (bo'sh bo'lsagina).
    // "Siz uchun" e'lonlari bundlangan JSON'dan (composeResources/files/listings.json) o'qiladi.
    val seeder = koinInject<LocalDataSeeder>()
    // Universitetlar ro'yxati prof-emis'dan (barcha tanlash joylari shu manbani ishlatadi).
    val universityRepository = koinInject<UniversityRepository>()
    LaunchedEffect(Unit) {
        val listingsJson = runCatching { Res.readBytes("files/listings.json").decodeToString() }.getOrNull()
        seeder.seedIfEmpty(listingsJson)
        runCatching { universityRepository.ensureRemoteUniversities() }
    }

    // Foydalanuvchi tanlagan mavzu (Sozlamalar). SYSTEM bo'lsa qurilma rejimiga ergashadi.
    val settings = koinInject<SettingsRepository>()
    val themeMode by settings.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    AppTheme(darkTheme = isDark) {
        // Butun ilova pastki tizim navigatsiya paneli (3 tugma) / iOS home indikatori
        // ortida qolmasligi uchun global inset. Fon gradienti panel ostida ham to'liq chiziladi.
        //
        // `union(ime)` — klaviatura ochilganda kontent uning USTIGA ko'tariladi, ya'ni matn
        // maydonlari (qidiruv, forma, izoh...) klaviatura ostida qolib ketmaydi. `union` —
        // ikkalasining KATTAsi olinadi, aks holda klaviatura ustiga yana navigatsiya paneli
        // balandligi qo'shilib, ortiqcha bo'shliq paydo bo'lardi.
        //
        // Bu global: ichkarida `imePadding()` chaqirgan ekranlar (chat, e'lonlar) ikki marta
        // surilib ketmaydi — Compose qo'llanilgan insetni "iste'mol qilingan" deb belgilaydi.
        Box(Modifier.fillMaxSize().background(appPalette.bgBrush)) {
            Box(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                content()
            }
        }
    }
}

/**
 * Coil har bir havolani shu yerdan o'tkazadi — buzuq media havolalari (`localhost`,
 * `http://`, nisbiy yo'l) ko'rsatishdan oldin tuzatiladi. Qarang [MediaUrl].
 */
private class MediaUrlMapper(private val apiOrigin: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? = MediaUrl.normalize(data, apiOrigin)
}
