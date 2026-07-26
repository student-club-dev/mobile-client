package dev.feature.listings.presentation.di

import dev.core.network.createPublicHttpClient
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
import dev.feature.listings.domain.usecase.ObserveListingsByKindUseCase
import dev.feature.listings.presentation.browse.ListingsBrowseViewModel
import dev.feature.listings.presentation.detail.ListingDetailViewModel
import dev.feature.listings.domain.usecase.ObserveMyListingsUseCase
import dev.feature.listings.domain.usecase.PublishListingUseCase
import dev.feature.listings.domain.usecase.SaveDraftUseCase
import dev.feature.listings.domain.usecase.SearchPlacesUseCase
import dev.feature.listings.domain.usecase.ToggleListingPausedUseCase
import dev.feature.listings.domain.usecase.UploadListingImageUseCase
import dev.feature.listings.presentation.MyListingsViewModel
import dev.feature.listings.presentation.NearbyDiscountsViewModel
import dev.feature.listings.presentation.PostListingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * E'lonlar feature'ining barcha qatlamlarini bog'laydi (domain / data / presentation).
 *
 * [useRemoteApi] — masofaviy manba tanlovi. **Hozircha har ikkala holatda ham local**:
 * talaba API'sida (`student-club.json`) e'lon yozish/o'qish endpoint'i yo'q — e'lon qo'yish
 * biznes tomonining (ElonUz) shartnomasiga tegishli, talaba feed'i esa `STUDENT_FEED.md`
 * bo'yicha alohida quriladi (`POST /v1/discounts/search`). Shu endpoint'lar chiqqanda
 * bu yerga API manbasi qo'shiladi.
 */
fun listingsModule(useRemoteApi: Boolean) = module {

    single<ListingRemoteDataSource> { LocalListingRemoteDataSource() }

    single<ListingRepository> { ListingRepositoryImpl(get(), get(), get()) }

    // Teskari geokodlash — OpenStreetMap Nominatim (tekin). Ilovaning umumiy klienti EMAS:
    // unda Firebase Bearer tokeni bor, uni begona serverga yuborib bo'lmaydi.
    single<GeoRepository> { NominatimGeoRepository(createPublicHttpClient()) }

    factory { CreateBranchFromPointUseCase(get()) }
    factory { SearchPlacesUseCase(get()) }
    factory { ObserveMyListingsUseCase(get()) }
    factory { ObserveActiveListingsUseCase(get()) }
    factory { ObserveListingsByKindUseCase(get()) }
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
