package dev.feature.listings.presentation.di

import dev.core.network.NetworkConfig
import dev.core.network.createPublicHttpClient
import dev.core.network.generated.api.ListingsApi
import dev.core.network.generated.api.MediaApi
import dev.feature.listings.data.remote.ApiListingRemoteDataSource
import dev.feature.listings.data.remote.ListingRemoteDataSource
import dev.feature.listings.data.remote.LocalListingRemoteDataSource
import dev.feature.listings.data.remote.NominatimGeoRepository
import dev.feature.listings.data.repository.ListingRepositoryImpl
import dev.feature.listings.domain.repository.GeoRepository
import dev.feature.listings.domain.repository.ListingRepository
import dev.feature.listings.domain.usecase.CreateBranchFromPointUseCase
import dev.feature.listings.domain.usecase.DeleteListingUseCase
import dev.feature.listings.domain.usecase.GetListingUseCase
import dev.feature.listings.domain.usecase.ObserveActiveListingsUseCase
import dev.feature.listings.domain.usecase.ObserveMyListingsUseCase
import dev.feature.listings.domain.usecase.PublishListingUseCase
import dev.feature.listings.domain.usecase.SaveDraftUseCase
import dev.feature.listings.domain.usecase.SearchPlacesUseCase
import dev.feature.listings.domain.usecase.ToggleListingPausedUseCase
import dev.feature.listings.domain.usecase.UploadListingImageUseCase
import dev.feature.listings.presentation.MyListingsViewModel
import dev.feature.listings.presentation.NearbyDiscountsViewModel
import dev.feature.listings.presentation.PostListingViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Chegirmalar feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 *
 * [useRemoteApi] — masofaviy manba tanlovi:
 * - `true`  → real backend: `POST /v1/business/{id}/listings` + `/submit`, rasm `/media/upload`
 *   (OpenAPI'dan generatsiya qilingan [ListingsApi] / [MediaApi]),
 * - `false` → backendsiz rejim: e'lon local bazada, rasm `data:` URI sifatida saqlanadi.
 *
 * Bayroq `CoreModules.REMOTE_SYNC_ENABLED` dan keladi — profil moduli bilan bir xil naqsh.
 */
fun listingsModule(useRemoteApi: Boolean) = module {

    single { ListingsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }
    single { MediaApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    single<ListingRemoteDataSource> {
        if (useRemoteApi) ApiListingRemoteDataSource(get(), get()) else LocalListingRemoteDataSource()
    }

    single<ListingRepository> { ListingRepositoryImpl(get(), get(), get()) }

    // Teskari geokodlash — OpenStreetMap Nominatim (tekin). Ilovaning umumiy klienti EMAS:
    // unda Firebase Bearer tokeni bor, uni begona serverga yuborib bo'lmaydi.
    single<GeoRepository> { NominatimGeoRepository(createPublicHttpClient()) }

    factory { CreateBranchFromPointUseCase(get()) }
    factory { SearchPlacesUseCase(get()) }
    factory { ObserveMyListingsUseCase(get()) }
    factory { ObserveActiveListingsUseCase(get()) }
    factory { SaveDraftUseCase(get()) }
    factory { PublishListingUseCase(get()) }
    factory { ToggleListingPausedUseCase(get()) }
    factory { DeleteListingUseCase(get()) }
    factory { UploadListingImageUseCase(get()) }
    factory { GetListingUseCase(get()) }

    viewModelOf(::PostListingViewModel)
    viewModelOf(::MyListingsViewModel)
    viewModelOf(::NearbyDiscountsViewModel)
}
