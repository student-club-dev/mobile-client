package dev.feature.calls.presentation.di

import org.koin.core.module.Module

/**
 * Media qatlamining platformaga xos fabrikasi.
 *
 * Alohida modul, chunki `AndroidCallEngineFactory` `Context` talab qiladi va uni
 * `commonMain` dagi [callsModule] da qurib bo'lmaydi.
 */
expect fun callsPlatformModule(): Module
