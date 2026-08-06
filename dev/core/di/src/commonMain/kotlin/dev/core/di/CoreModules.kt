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
import dev.core.data.seed.SeedPurge
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
import dev.core.domain.usecase.RequestRegistrationOtpUseCase
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
 * Faqat **endpoint'i hali yo'q** bo'limlar uchun qoldi: **ishlar** (`/v1/jobs`), **klublar**
 * (`/v1/clubs`) va eski `ad` jadvali. Ular local bazadan (seed) ishlaydi, shuning uchun
 * bayroq `false`.
 *
 * Qolgan hamma narsa backendda va bu bayroqqa **bog'liq emas**: auth, profil, geo, media,
 * "Siz uchun" feed'i ([DISCOUNTS_REMOTE_ENABLED]), talaba e'lonlari
 * ([STUDENT_LISTINGS_REMOTE_ENABLED]), talabalar ro'yxati (`GET /v1/students`),
 * bog'lanishlar, chat, qo'ng'iroq va story.
 */
const val REMOTE_SYNC_ENABLED = false

/**
 * "Siz uchun" bo'limi backend'dan keladimi — `POST /v1/catalog/groups` + `/v1/catalog/types`
 * (ElonUz katalogining 27 ta biznes turi) va `POST /v1/discounts/search`.
 *
 * ⚠️ Namuna ma'lumot (`listings.json`) olib tashlandi — `false` qilinsa ekranda faqat
 * oldingi so'rovlardan qolgan kesh ko'rinadi, birinchi ishga tushirishda esa bo'sh bo'ladi.
 * Tarmoq xatosida kesh saqlanadi: refresh DB'ni faqat muvaffaqiyatli javobda almashtiradi.
 */
const val DISCOUNTS_REMOTE_ENABLED = true

/**
 * Talaba e'lonlari backenddan keladimi — `/v1/student-listings*` (9 endpoint).
 *
 * `STUDENT_LISTINGS_RESPONSE.md` bo'yicha modul tayyor: yaratish, e'lon qilish, qidiruv,
 * xarita va muddati o'tishi. Shu sabab u umumiy [REMOTE_SYNC_ENABLED] bayrog'iga bog'liq
 * emas — o'sha bayroq hali endpoint'i yo'q bo'limlar (ishlar, klublar) uchun `false`.
 *
 * `false` qilinsa ilova butunlay local bazada ishlaydi (backendsiz rejim): e'lon darrov
 * faol bo'ladi, rasm `data:` URI sifatida saqlanadi. Namuna e'lonlar yo'q — ro'yxat
 * foydalanuvchi o'zi joylagan e'lonlardan iborat bo'ladi.
 */
const val STUDENT_LISTINGS_REMOTE_ENABLED = true

/**
 * Bildirishnomalar ro'yxati backenddan keladimi — `GET /v1/notifications` +
 * `POST /v1/notifications/read` (`01-NOTIFICATIONS_BACKEND.md`).
 *
 * 2026-08-05 dan boshlab ikkala endpoint ham serverda va spec'da bor, shuning uchun `true`.
 *
 * ⚠️ Ro'yxatga yozuvchi hodisalar `02-PUSH_CATALOG` bilan keladi: dastlab ro'yxat BO'SH
 * ko'rinishi mumkin, lekin `404` bermaydi.
 *
 * Push (`POST /v1/devices`) bunga BOG'LIQ EMAS: u allaqachon serverda va doim ishlaydi.
 */
const val NOTIFICATIONS_REMOTE_ENABLED = true

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

    // Eski o'rnatmalarda qolgan namuna ma'lumotni bir marta o'chiradi (backendga ulangan
    // bo'limlar uchun) — qarang: `SeedPurge`.
    single { SeedPurge(get(), get()) }
}

val domainModule = module {
    factory { LoginUseCase(get()) }
    factory { LoginWithGoogleUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { CompleteRegistrationUseCase(get()) }
    factory { CancelRegistrationUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { ObserveCurrentUserUseCase(get()) }
    factory { RequestRegistrationOtpUseCase(get()) }
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
