package dev.feature.calls.presentation.di

import dev.feature.calls.data.engine.CallEngineFactory
import dev.feature.calls.data.engine.IosCallEngineFactory
import dev.feature.calls.data.session.CallPresence
import dev.feature.calls.data.session.IosCallPresence
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun callsPlatformModule(): Module = module {
    // `WebRTC.framework` qo'shilgunicha bu fabrika media qatlamini ko'tarmaydi va
    // qo'ng'iroq aniq xato bilan to'xtaydi (`CallEngineFactory.ios.kt`).
    single<CallEngineFactory> { IosCallEngineFactory() }

    // CallKit 2-bosqichda — hozircha ilmoq bo'sh.
    single<CallPresence> { IosCallPresence() }
}
