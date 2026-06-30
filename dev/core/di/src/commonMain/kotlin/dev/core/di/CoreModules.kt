package dev.core.di

import dev.core.common.AppDispatchers
import dev.core.common.DefaultAppDispatchers
import dev.core.data.repository.AuthRepositoryImpl
import dev.core.data.repository.ClubRepositoryImpl
import dev.core.database.DatabaseFactory
import dev.core.database.DriverFactory
import dev.core.database.sql.StudentClubsDatabase
import dev.core.domain.repository.AuthRepository
import dev.core.domain.repository.ClubRepository
import dev.core.domain.usecase.LoginUseCase
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
    single<AuthRepository> { AuthRepositoryImpl(get()) }
    single<ClubRepository> { ClubRepositoryImpl(get(), get(), get()) }
}

val domainModule = module {
    factory { LoginUseCase(get()) }
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
