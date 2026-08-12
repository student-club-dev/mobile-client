package dev.feature.listings.domain.usecase

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingBranch
import dev.feature.listings.domain.model.ListingError
import dev.feature.listings.domain.model.ListingField
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.ListingValidator
import dev.feature.listings.domain.repository.GeoRepository
import dev.feature.listings.domain.repository.PlaceSuggestion
import dev.feature.listings.domain.repository.ListingRepository
import kotlinx.coroutines.flow.Flow
import dev.core.common.locale.AppLocale

/** Biznes egasining e'lonlari (barcha statuslar) — "Mening e'lonlarim" ekrani. */
class ObserveMyListingsUseCase(private val repository: ListingRepository) {
    operator fun invoke(ownerId: String): Flow<List<Listing>> = repository.observeMyListings(ownerId)
}

/** Talabaga ko'rinadigan barcha turdagi faol e'lonlar (masalan xaritada hammasi birga). */
class ObserveActiveListingsUseCase(private val repository: ListingRepository) {
    operator fun invoke(): Flow<List<Listing>> = repository.observeActive()
}

/**
 * Bitta bo'limning faol e'lonlari: Chegirmalar, Ijara, Xizmatlar yoki Ish e'lonlari.
 *
 * Ro'yxatlar aralashmasligi kerak — talaba "Chegirmalar" bo'limida ijara e'lonini
 * ko'rmasligi lozim.
 */
class ObserveListingsByKindUseCase(private val repository: ListingRepository) {
    operator fun invoke(kind: ListingKind): Flow<List<Listing>> = repository.observeActiveByKind(kind)
}

/**
 * Serverdagi qidiruv — ro'yxat ekranining asosiy manbasi.
 *
 * Filtrlash va saralash klientda emas, chunki telefonda e'lonlarning faqat bir qismi bor:
 * local filtr "topilmadi" deganda ham serverda o'nlab mos e'lon turgan bo'lishi mumkin.
 */
class SearchListingsUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(query: ListingQuery): Resource<ListingPage> = repository.search(query)
}

/**
 * Bo'limning birinchi sahifasini serverdan **keshga** tortadi.
 *
 * Home va "Universitetim" ekranlari e'lonlarni [ObserveListingsByKindUseCase] bilan
 * keshdan kuzatadi: ular uchun e'lon — ekranning bir bo'lagi, alohida ro'yxat emas, ya'ni
 * sahifalash ham, filtr ham kerak emas. Lekin kesh o'zi to'lmaydi — uni kimdir to'ldirishi
 * kerak, aks holda bu bo'limlar faqat foydalanuvchi "E'lonlar" ekranini ochgandan keyin
 * jonlanardi.
 */
class RefreshListingsUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(kind: ListingKind): Resource<Unit> =
        when (val res = repository.search(ListingQuery(kind = kind, size = ListingQuery.MAX_PAGE_SIZE))) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> res
            Resource.Loading -> Resource.Loading
        }
}

/** "Mening e'lonlarim" ni server bilan sinxronlaydi (barcha status va turlar). */
class RefreshMyListingsUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(ownerId: String): Resource<List<Listing>> =
        repository.refreshMine(ownerId)
}

/**
 * E'lonni to'liq ko'rish uchun yuklaydi. Keshdagi nusxadan farqi: `viewsCount` shu
 * so'rovda oshadi va `contactPhone` **faqat shu javobda** keladi — ro'yxatda u yo'q.
 */
class FetchListingUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(id: String): Resource<Listing> = repository.fetchById(id)
}

/** Qoralama sifatida saqlaydi — validatsiyasiz (yarim to'ldirilgan forma ham saqlanadi). */
class SaveDraftUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(listing: Listing): Resource<Listing> =
        repository.save(listing.copy(status = ListingStatus.DRAFT))
}

/**
 * E'lonni publish qiladi. **Avval klient validatsiyasi** — xato bo'lsa serverga bormaydi.
 *
 * Server baribir o'zi tekshiradi (§5) va uning xatolari ham aynan shu shaklda qaytadi:
 * `error.fields` kalitlari [ListingField] nomlari bilan bir xil, matnlari esa klientdagi
 * matnlar bilan **so'zma-so'z** mos. Shu sabab UI uchun ikkalasi farq qilmaydi — xato
 * qayerdan kelganidan qat'i nazar, o'sha maydon ostida chiqadi.
 */
class PublishListingUseCase(private val repository: ListingRepository) {

    sealed interface Result {
        data class Success(val listing: Listing) : Result

        /**
         * [message] — serverning umumiy xabari. U faqat **birorta ham** maydon xatosi
         * tanib olinmaganda to'ldiriladi (masalan spec kengayib, yangi kalit qo'shilgan):
         * shunda foydalanuvchi hech bo'lmasa nima bo'lganini ko'radi.
         */
        data class Invalid(val errors: List<ListingError>, val message: String? = null) : Result
        data class Failed(val message: String) : Result
    }

    suspend operator fun invoke(listing: Listing): Result {
        val errors = ListingValidator.validate(listing)
        if (errors.isNotEmpty()) return Result.Invalid(errors)

        return when (val res = repository.submit(listing)) {
            is Resource.Success -> Result.Success(res.data)
            is Resource.Error -> res.toResult()
            Resource.Loading -> Result.Failed(AppLocale.pick(en = "Couldn't submit the listing", ru = "Не удалось отправить объявление", uz = "E'lonni yuborib bo'lmadi"))
        }
    }

    private fun Resource.Error.toResult(): Result {
        val validation = error as? AppException.Validation ?: return Result.Failed(message)
        val fields = validation.fields.mapNotNull { (key, text) ->
            ListingField.entries.firstOrNull { it.name == key }?.let { ListingError(it, text) }
        }
        return if (fields.isEmpty()) {
            Result.Failed(message)
        } else {
            Result.Invalid(fields)
        }
    }
}

/** E'lonni to'xtatib turish / qayta yoqish (ACTIVE ⇄ PAUSED). */
class ToggleListingPausedUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(listing: Listing): Resource<Unit> {
        val next = when (listing.status) {
            ListingStatus.ACTIVE -> ListingStatus.PAUSED
            ListingStatus.PAUSED -> ListingStatus.ACTIVE
            else -> return Resource.Error(AppLocale.pick(en = "Can't pause from this state: ${listing.status.label}", ru = "Нельзя приостановить из этого состояния: ${listing.status.label}", uz = "Bu holatda to'xtatib bo'lmaydi: ${listing.status.label}"))
        }
        return repository.updateStatus(listing.id, next)
    }
}

class DeleteListingUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(id: String): Resource<Unit> = repository.delete(id)
}

/**
 * E'lon rasmini yuklaydi. Hajm [MAX_IMAGE_BYTES] dan oshsa masofaviy manbaga bormaydi —
 * spec ham 5 MB chegara qo'yadi.
 */
class UploadListingImageUseCase(private val repository: ListingRepository) {

    suspend operator fun invoke(bytes: ByteArray, fileName: String): Resource<String> {
        if (bytes.isEmpty()) return Resource.Error(AppLocale.pick(en = "The image is empty", ru = "Изображение пустое", uz = "Rasm bo'sh"))
        if (bytes.size > MAX_IMAGE_BYTES) return Resource.Error(AppLocale.pick(en = "The image is too large (max 5 MB)", ru = "Изображение слишком большое (макс. 5 МБ)", uz = "Rasm juda katta (maks. 5 MB)"))
        return repository.uploadImage(bytes, fileName)
    }

    companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    }
}

/** Tahrirlash uchun mavjud e'lonni yuklaydi. */
class GetListingUseCase(private val repository: ListingRepository) {
    suspend operator fun invoke(id: String): Listing? = repository.byId(id)
}

/**
 * Joy qidirish (xaritadagi qidiruv maydoni). Juda qisqa so'rov yuborilmaydi — Nominatim
 * uchun ham, foydalanuvchi uchun ham ma'nosiz natijalar chiqadi.
 */
class SearchPlacesUseCase(private val geoRepository: GeoRepository) {

    suspend operator fun invoke(query: String): List<PlaceSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        return (geoRepository.search(trimmed) as? Resource.Success)?.data.orEmpty()
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}

/**
 * Xaritada tanlangan nuqtadan filial yaratadi: manzil teskari geokodlash bilan avtomatik
 * to'ladi. Geokodlash ishlamasa (internet yo'q) — filial baribir yaratiladi, manzil o'rniga
 * koordinata yoziladi va foydalanuvchi uni tahrirlay oladi. Nuqta yo'qolib qolmasligi kerak.
 */
class CreateBranchFromPointUseCase(private val geoRepository: GeoRepository) {

    suspend operator fun invoke(id: String, lat: Double, lng: Double): ListingBranch {
        val resolved = (geoRepository.reverseGeocode(lat, lng) as? Resource.Success)?.data
        return ListingBranch(
            id = id,
            lat = lat,
            lng = lng,
            address = resolved?.address?.takeIf { it.isNotBlank() }
                ?: "${lat.round5()}, ${lng.round5()}",
            // Mo'ljal — eng yaqin metro bekati (Toshkent, 3 km ichida). Manzil satriga
            // qo'shilmaydi: manzilni foydalanuvchi tahrirlaydi, mo'ljal esa serverdan
            // kelgan fakt va uni tasodifiy o'chirib qo'yish kerak emas.
            landmark = resolved?.nearestMetro?.let { "$it metrosi yaqinida" },
            regionId = resolved?.regionId,
            districtId = resolved?.districtId,
        )
    }

    /** Koordinatani o'qish uchun qisqartiradi (5 xona ≈ 1 metr aniqlik). */
    private fun Double.round5(): Double = kotlin.math.round(this * 100_000) / 100_000
}
