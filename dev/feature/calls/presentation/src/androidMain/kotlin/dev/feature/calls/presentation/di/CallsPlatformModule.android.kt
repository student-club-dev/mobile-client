package dev.feature.calls.presentation.di

import dev.feature.calls.data.engine.AndroidCallEngineFactory
import dev.feature.calls.data.engine.CallEngineFactory
import dev.feature.calls.data.session.AndroidCallPresence
import dev.feature.calls.data.session.CallPresence
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun callsPlatformModule(): Module = module {
    // `single` — `PeerConnectionFactory` va `EglBase` fabrikaning ichida bir marta
    // quriladi va butun ilova umri davomida qayta ishlatiladi (har qo'ng'iroqda qayta
    // qurish qimmat va ba'zi qurilmalarda ikkinchi marta umuman ishlamaydi).
    single<CallEngineFactory> { AndroidCallEngineFactory(androidContext()) }

    // Old plan xizmati — usiz Android 14+ fonda mikrofonni JIMGINA o'chirib qo'yadi.
    single<CallPresence> { AndroidCallPresence(androidContext()) }
}
