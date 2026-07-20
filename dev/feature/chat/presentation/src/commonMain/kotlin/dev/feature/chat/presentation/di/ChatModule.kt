package dev.feature.chat.presentation.di

import dev.feature.chat.data.remote.ChatRemoteDataSource
import dev.feature.chat.data.remote.KtorChatRemoteDataSource
import dev.feature.chat.data.repository.ChatRepositoryImpl
import dev.feature.chat.domain.repository.ChatRepository
import dev.feature.chat.presentation.ChatViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Chat feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 * [useRemoteApi] — masofaviy sinxronlash bayrog'i (`CoreModules.REMOTE_SYNC_ENABLED`).
 *
 * `ChatRealtimeSource` (Firestore) auth feature'da bog'lanadi (B7) — Koin uni runtime'da ulaydi.
 */
fun chatModule(useRemoteApi: Boolean) = module {
    single<ChatRemoteDataSource> { KtorChatRemoteDataSource(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), useRemoteApi, get()) }
    viewModelOf(::ChatViewModel)
}
