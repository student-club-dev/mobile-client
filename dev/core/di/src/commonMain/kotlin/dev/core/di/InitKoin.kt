package dev.core.di

import dev.feature.auth.di.authFeatureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun appModules() = coreModules() + authFeatureModule

/** Umumiy Koin start (androidApp shu yerga androidContext qo'shadi). */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModules())
    }
