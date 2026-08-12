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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.map.Mapper
import io.github.aakira.napier.Napier
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import dev.core.common.locale.AppLanguage
import dev.core.common.locale.AppLocale
import dev.core.data.seed.LocalDataSeeder
import dev.core.data.seed.SeedPurge
import dev.core.di.IMAGE_CLIENT
import dev.core.network.NetworkConfig
import dev.core.network.media.MediaUrl
import dev.core.network.media.apiOrigin
import dev.core.uikit.components.ScToastHost
import dev.core.uikit.locale.LocalAppLanguage
import dev.core.uikit.media.purgeLegacyGalleryMedia
import dev.core.uikit.theme.AppTheme
import dev.feature.settings.domain.model.ThemeMode
import dev.feature.settings.domain.repository.SettingsRepository
import dev.feature.university.domain.repository.UniversityRepository
import dev.feature.auth.presentation.flow.AuthNavHost
import dev.feature.calls.presentation.CallHost
import dev.core.uikit.theme.appPalette
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/** Ilovaning ildiz Composable'i — Android MainActivity ham, iOS ham shuni ishlatadi. */
@Composable
fun App() {
    AppScaffold { AuthNavHost() }
}

/** Umumiy ildiz sozlamasi (rasm yuklovchi, seed, mavzu, inset) — hamma kirish nuqtalari ishlatadi. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
private fun AppScaffold(content: @Composable () -> Unit) {
    // Rasmlar uchun ALOHIDA klient: umumiy klientning 15 soniyalik so'rov chegarasi
    // navbatda turgan rasmlarni o'ldirardi va Chucker har bir rasmni bazasiga nusxalardi
    // (qarang: `createImageHttpClient`).
    val httpClient = koinInject<HttpClient>(named(IMAGE_CLIENT))
    // API manzilining origin qismi (`/v1/` siz) — buzuq havolalarni tuzatish uchun.
    val apiOrigin = koinInject<NetworkConfig>().apiOrigin
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            // Diskdagi kesh — **o'zimizning** papkada (`StudentClub/images`) va chegara
            // bilan. Coil'ning sukutdagi joyi tizimning vaqtinchalik papkasi: OS uni
            // xotira tugaganda tozalaydi va bir marta ko'rilgan rasm (ayniqsa story)
            // qaytadan yuklanardi.
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectory(context))
                    .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                    .build()
            }
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

    // Endpoint'i hali yo'q bo'limlarni (ishlar, klublar, bildirishnomalar) namuna ma'lumot
    // bilan to'ldiramiz. Backendga ulanganlari seed'dan olib tashlangan.
    val seeder = koinInject<LocalDataSeeder>()
    // Eski o'rnatmalarda qolgan namuna qatorlarni bir marta o'chiradi.
    val seedPurge = koinInject<SeedPurge>()
    // Universitetlar ro'yxati prof-emis'dan (barcha tanlash joylari shu manbani ishlatadi).
    val universityRepository = koinInject<UniversityRepository>()
    LaunchedEffect(Unit) {
        // Tozalash seed'DAN OLDIN: aks holda yangi seed qatorlari ham o'chib ketardi.
        runCatching { seedPurge.purgeOnce() }
        seeder.seedIfEmpty()
        runCatching { universityRepository.ensureRemoteUniversities() }
        // Eski versiyalar galereyaga yozib qo'ygan `story_…` / `chat_…` nusxalari — bir
        // marta o'chiriladi. Endi media ilovaning shaxsiy papkasida turadi va galereyada
        // umuman ko'rinmaydi (`StudentClubFolder`).
        runCatching { purgeLegacyGalleryMedia() }
    }

    // Foydalanuvchi tanlagan mavzu (Sozlamalar). SYSTEM bo'lsa qurilma rejimiga ergashadi.
    val settings = koinInject<SettingsRepository>()
    val themeMode by settings.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)

    // Interfeys tili. Boshlang'ich qiymat — EN: DB'dan javob kelgunicha ham ilova ingliz
    // tilida chiziladi, ya'ni birinchi kadr hech qachon boshqa tilda "chaqnab" ketmaydi.
    val language by settings.observeLanguage().collectAsState(initial = AppLanguage.Default)
    // Compose'dan tashqaridagi kod (validator, use-case, WebSocket mapper) shu global
    // holatdan o'qiydi — CompositionLocal u yerga yetmaydi. Namuna qatorlar bazada
    // yotadi, shuning uchun ular ham yangi tilda qayta yoziladi.
    LaunchedEffect(language) {
        AppLocale.set(language)
        runCatching { seeder.resyncLanguage() }
    }

    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Til BUTUN daraxtga shu yerdan tarqaladi — `AppTheme` ham ichida qoladi, chunki
    // mavzu ichidagi komponentlar (toast, qo'ng'iroq oynasi) ham tarjimani o'qiydi.
    CompositionLocalProvider(LocalAppLanguage provides language) {
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
                // Qo'ng'iroq ekrani BUTUN ilova ustida turadi va inset o'ramidan tashqarida:
                // u to'liq ekranni egallaydi (video kadr status bar ostiga ham chiqadi) va
                // o'z insetlarini o'zi qo'yadi. Kiruvchi qo'ng'iroq foydalanuvchi qaysi
                // ekranda turganidan qat'i nazar ko'rinishi kerak.
                CallHost()
                // Xato/xabar toastlari — HAMMA narsadan ustida va insetdan tashqarida: ular
                // status bar ostidan tushadi va foydalanuvchi qaysi ekranda bo'lishidan
                // qat'i nazar ko'rinishi kerak (`AppMessageBus`).
                ScToastHost()
            }
        }
    }
}

/**
 * Coil har bir havolani shu yerdan o'tkazadi — buzuq media havolalari (`localhost`,
 * `http://`, nisbiy yo'l) ko'rsatishdan oldin tuzatiladi. Qarang [MediaUrl].
 */
private class MediaUrlMapper(private val apiOrigin: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? {
        val fixed = MediaUrl.normalize(data, apiOrigin)
        // Rasm ko'rinmasa birinchi savol — "server qanday havola bergan?". Logsiz buni
        // faqat trafikni ushlab ko'rish bilan bilib bo'lardi.
        if (fixed != data) Napier.d("Media URL: $data -> $fixed", tag = "Media")
        return fixed
    }
}
