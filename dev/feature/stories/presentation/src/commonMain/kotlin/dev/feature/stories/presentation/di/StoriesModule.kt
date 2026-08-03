package dev.feature.stories.presentation.di

import dev.core.network.NetworkConfig
import dev.core.network.generated.api.StoriesApi
import dev.core.network.media.apiOrigin
import dev.feature.stories.data.repository.StoryRepositoryImpl
import dev.feature.stories.domain.repository.StoryRepository
import dev.feature.stories.presentation.MyPostsViewModel
import dev.feature.stories.presentation.StoriesViewModel
import dev.feature.stories.presentation.StudentPostsViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Story feature'i (`handoff/07-STORIES.md`) — to'liq backendda, local kesh yo'q.
 *
 * `MediaUploader` core'dan keladi: yuklash `POST /v1/media/chat-upload` orqali,
 * `kind=STORY_IMAGE|STORY_VIDEO` bilan va **`conversationId` siz**.
 */
fun storiesModule() = module {

    single { StoriesApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    single<StoryRepository> {
        StoryRepositoryImpl(
            api = get(),
            media = get(),
            connectivity = get(),
            // Media havolalari serverdan NISBIY keladi — video pleyer uchun ular to'liq
            // bo'lishi shart (qarang `MediaUrl`).
            apiOrigin = get<NetworkConfig>().apiOrigin,
        )
    }

    viewModelOf(::StoriesViewModel)
    // Profildagi «Postlar» / «Arxivlangan postlar» bo'limi — lentadan alohida holat.
    viewModelOf(::MyPostsViewModel)
    // Boshqa talaba profilidagi «Postlar» bo'limi.
    viewModelOf(::StudentPostsViewModel)
}
