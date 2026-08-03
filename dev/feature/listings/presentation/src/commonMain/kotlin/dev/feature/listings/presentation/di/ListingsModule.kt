package dev.feature.listings.presentation.di

import dev.core.network.NetworkConfig
import dev.core.network.createPublicHttpClient
import dev.core.network.generated.api.StudentListingsApi
import dev.core.network.media.apiOrigin
import dev.feature.listings.data.remote.ApiGeoRepository
import dev.feature.listings.data.remote.ApiListingRemoteDataSource
import dev.feature.listings.data.remote.ListingRemoteDataSource
import dev.feature.listings.data.remote.LocalListingRemoteDataSource
import dev.feature.listings.data.remote.NominatimGeoRepository
import dev.feature.listings.data.repository.ListingRepositoryImpl
import dev.feature.listings.domain.repository.GeoRepository
import dev.feature.listings.domain.repository.ListingRepository
import dev.feature.listings.domain.usecase.CreateBranchFromPointUseCase
import dev.feature.listings.domain.usecase.DeleteListingUseCase
import dev.feature.listings.domain.usecase.FetchListingUseCase
import dev.feature.listings.domain.usecase.GetListingUseCase
import dev.feature.listings.domain.usecase.ObserveActiveListingsUseCase
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import dev.feature.listings.domain.usecase.ObserveMyListingsUseCase
import dev.feature.listings.domain.usecase.PublishListingUseCase
import dev.feature.listings.domain.usecase.RefreshListingsUseCase
import dev.feature.listings.domain.usecase.RefreshMyListingsUseCase
import dev.feature.listings.domain.usecase.SaveDraftUseCase
import dev.feature.listings.domain.usecase.SearchListingsUseCase
import dev.feature.listings.domain.usecase.SearchPlacesUseCase
import dev.feature.listings.domain.usecase.ToggleListingPausedUseCase
import dev.feature.listings.domain.usecase.UploadListingImageUseCase
import dev.feature.listings.presentation.MyListingsViewModel
import dev.feature.listings.presentation.NearbyDiscountsViewModel
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.browse.ListingsBrowseViewModel
import dev.feature.listings.presentation.detail.ListingDetailViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * E'lonlar feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 *
 * [useRemoteApi] — masofaviy manba tanlovi (`STUDENT_LISTINGS_REMOTE_ENABLED`):
 *
 * - `true` — `/v1/student-listings*`: qidiruv, e'lon qilish, status va o'chirish serverda,
 *   local baza esa kesh bo'lib qoladi.
 * - `false` — backendsiz rejim: hamma narsa local bazada, rasm `data:` URI sifatida.
 *   Ilova internetsiz ham to'liq ishlaydi.
 */
fun listingsModule(useRemoteApi: Boolean) = module {

    // Generatsiya qilingan klientga ilovaning umumiy Ktor klienti uzatiladi — shunda
    // `Authorization: Bearer` har so'rovga avtomatik qo'shiladi va muddati tugasa yangilanadi.
    single { StudentListingsApi(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get<HttpClient>()) }

    single<ListingRemoteDataSource> {
        if (useRemoteApi) {
            ApiListingRemoteDataSource(
                api = get(),
                media = get(),
                connectivity = get(),
                apiOrigin = get<NetworkConfig>().apiOrigin,
            )
        } else {
            LocalListingRemoteDataSource()
        }
    }

    single<ListingRepository> { ListingRepositoryImpl(get(), get(), get(), useRemoteApi) }

    /**
     * Geokodlash — **o'z backendimiz** (`/v1/geo/geocode`, `/v1/geo/reverse-geocode`).
     *
     * Nominatim zaxira sifatida qoladi: ikkala endpoint ham `503` qaytarishi mumkin
     * (provayder kaliti sozlanmagan deployment), o'shanda manzil tanlash butunlay
     * ishlamay qolmasin. Batafsil — `ApiGeoRepository` izohida.
     *
     * Nominatim uchun ALOHIDA klient: umumiy klientda sessiya tokeni bor va uni begona
     * serverga yuborib bo'lmaydi.
     */
    single<GeoRepository> {
        ApiGeoRepository(
            geo = get(),
            connectivity = get(),
            fallback = NominatimGeoRepository(createPublicHttpClient()),
        )
    }

    factory { CreateBranchFromPointUseCase(get()) }
    factory { SearchPlacesUseCase(get()) }
    factory { ObserveMyListingsUseCase(get()) }
    factory { ObserveActiveListingsUseCase(get()) }
    factory { ObserveListingsByKindUseCase(get()) }
    factory { SearchListingsUseCase(get()) }
    factory { RefreshListingsUseCase(get()) }
    factory { RefreshMyListingsUseCase(get()) }
    factory { FetchListingUseCase(get()) }
    factory { SaveDraftUseCase(get()) }
    factory { PublishListingUseCase(get()) }
    factory { ToggleListingPausedUseCase(get()) }
    factory { DeleteListingUseCase(get()) }
    factory { UploadListingImageUseCase(get()) }
    factory { GetListingUseCase(get()) }

    viewModelOf(::ListingsBrowseViewModel)
    viewModelOf(::ListingDetailViewModel)
    viewModelOf(::PostListingViewModel)
    viewModelOf(::MyListingsViewModel)
    viewModelOf(::NearbyDiscountsViewModel)
}
