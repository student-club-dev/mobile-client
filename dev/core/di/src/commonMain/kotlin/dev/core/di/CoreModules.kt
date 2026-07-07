package dev.core.di

import dev.core.common.AppDispatchers
import dev.core.common.DefaultAppDispatchers
import dev.core.data.repository.AdRepositoryImpl
import dev.core.data.repository.ChatRepositoryImpl
import dev.core.data.repository.ClubRepositoryImpl
import dev.core.data.repository.DiscountRepositoryImpl
import dev.core.data.repository.JobRepositoryImpl
import dev.core.data.repository.StudentRepositoryImpl
import dev.core.data.repository.UniversityRepositoryImpl
import dev.core.data.seed.LocalDataSeeder
import dev.core.database.DatabaseFactory
import dev.core.database.DriverFactory
import dev.core.database.sql.StudentClubsDatabase
import dev.core.domain.repository.AdRepository
import dev.core.domain.repository.ChatRepository
import dev.core.domain.repository.ClubRepository
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.JobRepository
import dev.core.domain.repository.StudentRepository
import dev.core.domain.repository.UniversityRepository
import dev.core.domain.usecase.ConfirmEmailSignupUseCase
import dev.core.domain.usecase.HasProfileUseCase
import dev.core.domain.usecase.LoginUseCase
import dev.core.domain.usecase.LogoutUseCase
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.core.domain.usecase.ObserveProfileUseCase
import dev.core.domain.usecase.RegisterUseCase
import dev.core.domain.usecase.RequestEmailSignupUseCase
import dev.core.domain.usecase.SaveProfileUseCase
import dev.core.domain.usecase.SendPasswordResetUseCase
import dev.core.domain.usecase.SyncExternalUserUseCase
import dev.core.network.NetworkConfig
import dev.core.network.createHttpClient
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** Bazaviy URL — haqiqiy backend kelganda shu yerda almashtiriladi. */
const val DEFAULT_BASE_URL = "https://api.studentclubs.dev"

val networkModule = module {
    single { NetworkConfig(baseUrl = DEFAULT_BASE_URL) }
    single<HttpClient> { createHttpClient(get()) }
}

val databaseModule = module {
    single<StudentClubsDatabase> { DatabaseFactory.create(get<DriverFactory>()) }
}

val dispatchersModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
}

val repositoryModule = module {
    // AuthRepository (Firebase) auth feature modulida bog'lanadi (authFeatureModule).
    single<ClubRepository> { ClubRepositoryImpl(get(), get(), get()) }

    // Barcha domenlar — local DB (SQLDelight) ustidagi repository'lar.
    single<UniversityRepository> { UniversityRepositoryImpl(get(), get()) }
    single<DiscountRepository> { DiscountRepositoryImpl(get(), get()) }
    single<JobRepository> { JobRepositoryImpl(get(), get()) }
    single<StudentRepository> { StudentRepositoryImpl(get(), get()) }
    single<AdRepository> { AdRepositoryImpl(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get()) }

    // Dizayndagi namuna ma'lumot bilan bazani to'ldiruvchi (bo'sh bo'lsa).
    single { LocalDataSeeder(get(), get()) }
}

val domainModule = module {
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { SendPasswordResetUseCase(get()) }
    factory { RequestEmailSignupUseCase(get()) }
    factory { ConfirmEmailSignupUseCase(get()) }
    factory { SaveProfileUseCase(get()) }
    factory { SyncExternalUserUseCase(get()) }
    factory { HasProfileUseCase(get()) }
    factory { ObserveCurrentUserUseCase(get()) }
    factory { ObserveProfileUseCase(get()) }
    factory { LogoutUseCase(get()) }
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
