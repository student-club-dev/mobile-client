package dev.core.di

import dev.core.common.AppDispatchers
import dev.core.common.DefaultAppDispatchers
import dev.core.common.auth.TokenStore
import dev.core.data.auth.SecureTokenStore
import dev.core.data.auth.SqlDelightTokenStore
import dev.core.data.repository.DiscountRepositoryImpl
import dev.core.data.repository.RegionRepositoryImpl
import dev.core.data.remote.ApiDiscountRemoteDataSource
import dev.core.data.remote.DiscountRemoteDataSource
import dev.core.data.seed.LocalDataSeeder
import dev.core.database.DatabaseFactory
import dev.core.database.DriverFactory
import dev.core.database.sql.StudentClubDatabase
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.RegionRepository
import dev.core.domain.usecase.CancelRegistrationUseCase
import dev.core.domain.usecase.CompleteRegistrationUseCase
import dev.core.domain.usecase.ForgotPasswordUseCase
import dev.core.domain.usecase.GetDeviceSessionsUseCase
import dev.core.domain.usecase.LoginUseCase
import dev.core.domain.usecase.LoginWithGoogleUseCase
import dev.core.domain.usecase.LogoutAllDevicesUseCase
import dev.core.domain.usecase.LogoutUseCase
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.core.domain.usecase.RegisterUseCase
import dev.core.domain.usecase.RequestPhoneOtpUseCase
import dev.core.domain.usecase.ResetPasswordUseCase
import dev.core.domain.usecase.RevokeDeviceSessionUseCase
import dev.core.domain.usecase.SetPasswordUseCase
import dev.core.domain.usecase.VerifyPhoneOtpUseCase
import dev.core.network.NetworkConfig
import dev.core.network.createHttpClient
import dev.core.network.createImageHttpClient
import dev.core.network.media.MediaUploader
import dev.core.network.generated.api.CatalogApi
import dev.core.network.generated.api.GeoApi
import dev.core.network.generated.api.DiscountsApi
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Backend manzillari — API o'zgarganda faqat shu yerni almashtiring.
 * `USE_PROD_API = true` qilib prod'ga o'tasiz (yoki build-flag'ga ulaysiz).
 *
 * ⚠️ Oxiridagi `/v1/` (slash bilan) MUHIM:
 * - Ktor `defaultRequest` nisbiy yo'llarni shunga nisbatan hal qiladi (`get("jobs")` → `/v1/jobs`),
 * - OpenAPI'dan generatsiya qilingan klient ham shu bazaga yo'lni qo'shadi
 *   (`/profile/me` → `/v1/profile/me`); spec'dagi `/v1` prefiksi generatsiya paytida olib
 *   tashlanadi (`:dev:api-client-generator` dagi `cleanSwagger`).
 */
const val DEV_BASE_URL = "https://api.studentclub.uz/v1/"
const val PROD_BASE_URL = "https://api.studentclub.uz/v1/"
private const val USE_PROD_API = false

/** Joriy bazaviy URL (bitta manba). */
const val DEFAULT_BASE_URL = DEV_BASE_URL

/**
 * Offline-first sinxronlash yoqilganmi (B4).
 *
 * `student-club.json` (talaba API'si) **auth**, **profil**, **geo**, **media**, "Siz uchun"
 * feed'ini (**catalog** + **discounts**), shuningdek **connections** va **chat** ni beradi.
 * Ishlar/talabalar ro'yxati/universitetlar/klublar uchun endpoint hali yo'q — ular local
 * bazadan (seed) ishlaydi, shuning uchun bu umumiy bayroq `false` bo'lib qoladi.
 *
 * Bayroqqa **bog'liq bo'lmaganlari**: feed ([DISCOUNTS_REMOTE_ENABLED]), profil,
 * bog'lanishlar va chat — ular doim backenddan ishlaydi.
 */
const val REMOTE_SYNC_ENABLED = false

/**
 * "Siz uchun" bo'limi backend'dan keladimi — `POST /v1/catalog/groups` + `/v1/catalog/types`
 * (ElonUz katalogining 27 ta biznes turi) va `POST /v1/discounts/search`.
 *
 * `false` qilinsa ekran faqat local seed (`listings.json`) bilan ishlaydi. Tarmoq xatosi
 * bo'lganda ham kesh saqlanadi — refresh DB'ni faqat muvaffaqiyatli javobda almashtiradi.
 */
const val DISCOUNTS_REMOTE_ENABLED = true

/** Coil ishlatadigan rasm klientining Koin nomi. */
const val IMAGE_CLIENT = "imageHttpClient"

val networkModule = module {
    single { NetworkConfig(baseUrl = if (USE_PROD_API) PROD_BASE_URL else DEV_BASE_URL) }

    // Sessiya tokenlari platformaning shifrlangan omborida (Android: EncryptedSharedPreferences,
    // iOS: Keychain) — tarmoq qatlami ularni shu orqali o'qiydi/yangilaydi. `SqlDelightTokenStore`
    // faqat eski o'rnatmalardan bir martalik ko'chirish uchun uzatiladi.
    single<TokenStore> { SecureTokenStore(secure = get(), legacy = SqlDelightTokenStore(get())) }

    // Har so'rovga `Authorization: Bearer <accessToken>`; 401 da refresh avtomatik.
    single<HttpClient> { createHttpClient(get(), get()) }

    // Rasmlar (Coil) uchun ALOHIDA klient — Chucker'siz va kengroq chegaralar bilan
    // (qarang: `createImageHttpClient` izohi). Umumiy klient ishlatilganda ko'p rasmli
    // ekranlarda qismi yuklanmay qolardi.
    single(named(IMAGE_CLIENT)) { createImageHttpClient(get(), get()) }

    // Rasm yuklash (`POST /v1/media/upload`) — generatsiya qilingan `MediaApi` multipart
    // qismiga `filename` qo'ymagani uchun qo'lda yozilgan (qarang: `MediaUploader` izohi).
    //
    // Bu yerda — chunki uni bir nechta feature ishlatadi (profil avatari, chat rasmlari).
    // Har feature o'zi ro'yxatdan o'tkazsa Koin `DefinitionOverrideException` bilan yiqilardi.
    single { MediaUploader(client = get(), config = get()) }
}

val databaseModule = module {
    single<StudentClubDatabase> { DatabaseFactory.create(get<DriverFactory>()) }
}

val dispatchersModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
}

val repositoryModule = module {
    // AuthRepository (backend) auth feature modulida bog'lanadi (authFeatureModule).

    // Barcha domenlar — local DB (SQLDelight) ustidagi repository'lar.
    // --- B4 offline-first: masofaviy manbalar ---
    // Generatsiya qilingan klientlarga ilovaning umumiy Ktor klienti uzatiladi — shunda sessiya
    // tokeni (Bearer) har so'rovga avtomatik qo'shiladi va muddati tugasa yangilanadi.
    single { CatalogApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    single { DiscountsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    single { GeoApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    // Feed geo filtri: tanlangan viloyat (`filter.geo.regionIds`).
    single<RegionRepository> { RegionRepositoryImpl(get(), get(), get()) }
    single<DiscountRemoteDataSource> { ApiDiscountRemoteDataSource(get(), get(), get()) }

    // --- Repository'lar (offline-first: DB + refresh) ---
    single<DiscountRepository> { DiscountRepositoryImpl(get(), get(), get(), DISCOUNTS_REMOTE_ENABLED) }

    // Dizayndagi namuna ma'lumot bilan bazani to'ldiruvchi (bo'sh bo'lsa).
    single { LocalDataSeeder(get(), get()) }
}

val domainModule = module {
    factory { LoginUseCase(get()) }
    factory { LoginWithGoogleUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { CompleteRegistrationUseCase(get()) }
    factory { CancelRegistrationUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { ObserveCurrentUserUseCase(get()) }
    factory { RequestPhoneOtpUseCase(get()) }
    factory { VerifyPhoneOtpUseCase(get()) }
    factory { ForgotPasswordUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { SetPasswordUseCase(get()) }
    factory { GetDeviceSessionsUseCase(get()) }
    factory { RevokeDeviceSessionUseCase(get()) }
    factory { LogoutAllDevicesUseCase(get()) }
}

/** DriverFactory platformaga bog'liq (Android: Context kerak). */
expect val platformModule: Module

fun coreModules(): List<Module> = listOf(
    platformModule,
    dispatchersModule,
    networkModule,
    databaseModule,
    repositoryModule,
    domainModule,
)
