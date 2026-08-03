package dev.feature.listings.data.remote

import dev.core.common.Resource
import dev.core.common.error.AppException
import dev.core.common.map
import dev.core.common.network.NetworkConnectivity
import dev.core.network.generated.api.StudentListingsApi
import dev.core.network.media.MediaPurpose
import dev.core.network.media.MediaUploader
import dev.core.network.media.MediaUrl
import dev.core.network.response.safeCall
import dev.feature.listings.data.mapper.toCreateDto
import dev.feature.listings.data.mapper.toDomain
import dev.feature.listings.data.mapper.toStatusDto
import dev.feature.listings.data.mapper.toUpdateDto
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingGeoFilter
import dev.feature.listings.domain.model.ListingIds
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.domain.model.ListingPage
import dev.feature.listings.domain.model.ListingQuery
import dev.feature.listings.domain.model.ListingSort
import dev.feature.listings.domain.model.ListingStatus

/**
 * Talaba e'lonlarining real backend manbasi — generatsiya qilingan [StudentListingsApi]
 * ustida (`STUDENT_LISTINGS_BACKEND.md` + `STUDENT_LISTINGS_RESPONSE.md`).
 *
 * ⚠️ **Qidiruv `GET /v1/student-listings` bilan ketadi, `POST /search` bilan emas.**
 * Backend javobiga ko'ra ikkalasi bir xil kod yo'lidan o'tadi (§5.1), lekin spec'dagi
 * `POST /search` tanasi noto'g'ri: `filter` maydoni **biznes chegirmalarining**
 * `SearchFilterDto` siga (`groupKeys`, `businessIds`, `discount`…) qarab turibdi va unda
 * `gender`/`propertyType`/`shift` kabi turga xos filtrlarning birortasi ham yo'q; `page`
 * esa `{number, size}` — kursor umuman yo'q. `GET` variantida esa hammasi bor: turga xos
 * 15 ta filtr, `sort`, `size` va `cursor`. Spec tuzatilganda bu yerni `POST` ga o'tkazish
 * bir necha qatorlik ish.
 */
class ApiListingRemoteDataSource(
    private val api: StudentListingsApi,
    private val media: MediaUploader,
    private val connectivity: NetworkConnectivity,
    /** Rasm havolalarini to'liq holga keltirish uchun (`https://api.studentclub.uz`). */
    private val apiOrigin: String,
) : ListingRemoteDataSource {

    override suspend fun search(query: ListingQuery): Resource<ListingPage> {
        val kind = query.kind.toSearchKind()
            // Chegirma bu API'da yo'q — repository local keshdan izlaydi.
            ?: return Resource.Success(ListingPage())

        val filters = query.filters
        val geo = query.geo ?: ListingGeoFilter()
        return safeCall(connectivity) {
            api.studentListingSearchList(
                kind = kind,
                query = query.text.trim().takeIf { it.isNotBlank() },
                lat = geo.lat,
                lng = geo.lng,
                // Radius **faqat aniq so'ralganda** yuboriladi. `lat`/`lng` ning o'zi
                // masofa bo'yicha saralash uchun kerak; radiusni ham qo'shib yuborsak
                // undan uzoqdagi e'lonlar ro'yxatdan butunlay tushib qolardi va
                // foydalanuvchi buni hech qayerdan bilmasdi.
                radiusMeters = geo.takeIf { it.hasPoint }
                    ?.radiusMeters
                    ?.coerceAtMost(ListingGeoFilter.MAX_RADIUS_METERS),
                // Server vergul bilan ajratilgan ro'yxat kutadi ("TOSHKENT_SHAHRI,ANDIJON").
                regionIds = geo.regionIds.joinToString(",").takeIf { it.isNotBlank() },
                districtIds = geo.districtIds.joinToString(",").takeIf { it.isNotBlank() },
                maxPrice = filters.maxPrice?.toInt(),

                // --- Ijara ---
                gender = filters.gender?.let { g ->
                    StudentListingsApi.GenderStudentListingSearchList.entries
                        .firstOrNull { it.value == g.name }
                },
                propertyType = filters.propertyType?.let { p ->
                    StudentListingsApi.PropertyTypeStudentListingSearchList.entries
                        .firstOrNull { it.value == p.name }
                },
                minRooms = filters.minRooms,
                onlyAvailable = filters.onlyAvailable.takeIf { it },

                // --- Xizmat ---
                serviceType = filters.serviceType?.let { s ->
                    StudentListingsApi.ServiceTypeStudentListingSearchList.entries
                        .firstOrNull { it.value == s.name }
                },
                serviceFormat = filters.serviceFormat?.let { f ->
                    StudentListingsApi.ServiceFormatStudentListingSearchList.entries
                        .firstOrNull { it.value == f.name }
                },
                onlyFreeTrial = filters.onlyFreeTrial.takeIf { it },

                // --- Ish ---
                employment = filters.employment?.let { e ->
                    StudentListingsApi.EmploymentStudentListingSearchList.entries
                        .firstOrNull { it.value == e.name }
                },
                jobCategoryKey = filters.jobCategoryKey,
                shift = filters.shift?.let { s ->
                    StudentListingsApi.ShiftStudentListingSearchList.entries
                        .firstOrNull { it.value == s.name }
                },
                noExperienceOnly = filters.noExperienceOnly.takeIf { it },

                // --- Fanlardan yordam ---
                taskCategory = filters.taskCategory?.let { c ->
                    StudentListingsApi.TaskCategoryStudentListingSearchList.entries
                        .firstOrNull { it.value == c.name }
                },
                taskTypeKey = filters.taskTypeKey,
                taskFormat = filters.taskFormat?.let { f ->
                    StudentListingsApi.TaskFormatStudentListingSearchList.entries
                        .firstOrNull { it.value == f.name }
                },
                onlyOpenDeadline = filters.onlyOpenDeadline.takeIf { it },

                sort = query.sort.toApiSort(query.geo),
                size = query.size.coerceIn(1, ListingQuery.MAX_PAGE_SIZE),
                cursor = query.cursor,
                page = query.page?.coerceAtLeast(1),
            ).body()
        }.map { it.toDomain(apiOrigin) }
    }

    override suspend fun mine(page: Int, size: Int): Resource<ListingPage> =
        safeCall(connectivity) {
            api.studentListingsMine(
                size = size.coerceIn(1, ListingQuery.MAX_PAGE_SIZE),
                page = page.coerceAtLeast(1),
            ).body()
        }.map { it.toDomain(apiOrigin) }

    override suspend fun byId(id: String): Resource<Listing> =
        safeCall(connectivity) { api.findOne(id).body() }.map { it.toDomain(apiOrigin) }

    /**
     * Yangi e'lon — `POST`, mavjudi — `PATCH`.
     *
     * `PATCH` `404` bilan qaytsa e'lon serverda yo'q (o'chirilgan yoki id backendsiz
     * davrdan qolgan) — o'shanda so'rov yangi e'lon sifatida qaytadan yuboriladi, aks holda
     * to'ldirilgan forma foydalanuvchining qo'lida yo'qolib ketardi.
     */
    override suspend fun save(listing: Listing, submit: Boolean): Resource<Listing> {
        if (!ListingIds.isLocal(listing.id)) {
            val patched = patch(listing, submit)
            if (patched !is Resource.Error || patched.error !is AppException.NotFound) return patched
        }
        return create(listing, submit)
    }

    private suspend fun create(listing: Listing, submit: Boolean): Resource<Listing> {
        val body = listing.toCreateDto(submit)
            ?: return Resource.Error("Bu tur serverga yuborilmaydi: ${listing.kind.label}")
        return safeCall(connectivity) {
            api.studentListingsCreate(
                idempotencyKey = listing.idempotencyKey(),
                createStudentListingDto = body,
            ).body()
        }.map { it.toDomain(apiOrigin) }
    }

    /**
     * `PATCH` o'zi e'lon qilmaydi — `submit` faqat `POST` da bor. Shuning uchun tahrirlangan
     * qoralamani e'lon qilish ikki qadam: avval yangi mazmun yoziladi, keyin `/submit`.
     * Faol e'lonni tahrirlashda ikkinchi qadam kerak emas: server `PATCH` ning o'zida qayta
     * validatsiya qiladi va e'lon `ACTIVE` bo'lib qoladi (§3).
     */
    private suspend fun patch(listing: Listing, submit: Boolean): Resource<Listing> {
        val body = listing.toUpdateDto()
            ?: return Resource.Error("Bu tur serverga yuborilmaydi: ${listing.kind.label}")
        val updated = safeCall(connectivity) {
            api.patch(id = listing.id, updateStudentListingDto = body).body()
        }.map { it.toDomain(apiOrigin) }

        if (!submit || updated !is Resource.Success) return updated
        if (updated.data.status == ListingStatus.ACTIVE || updated.data.status == ListingStatus.SCHEDULED) {
            return updated
        }
        return safeCall(connectivity) { api.submit(listing.id).body() }.map { it.toDomain(apiOrigin) }
    }

    override suspend fun setStatus(id: String, status: ListingStatus): Resource<Listing> {
        val body = status.toStatusDto()
            ?: return Resource.Error("Bu holatga o'tkazib bo'lmaydi: ${status.label}")
        return safeCall(connectivity) { api.setStatus(id, body).body() }.map { it.toDomain(apiOrigin) }
    }

    override suspend fun delete(id: String): Resource<Unit> =
        safeCall(connectivity) { api.studentListingsRemove(id).body() }

    override suspend fun uploadImage(bytes: ByteArray, fileName: String): Resource<String> =
        safeCall(connectivity) {
            val uploaded = media.upload(bytes, fileName, MediaPurpose.LISTING)
            MediaUrl.normalize(uploaded.url, apiOrigin) ?: uploaded.url
        }

    /**
     * `Idempotency-Key` — takroriy so'rov dublikat yaratmaydi (§9).
     *
     * Kalit tasodifiy emas, **e'lonning o'zidan** olinadi: tarmoq uzilib, foydalanuvchi
     * "Yuborish" ni ikkinchi marta bosganda kalit ham aynan bir xil bo'ladi va server
     * o'sha e'lonni qaytaradi. Tasodifiy kalit bunda ikkita e'lon yaratardi.
     */
    private fun Listing.idempotencyKey(): String = "$id:$updatedAt"

    private fun ListingKind.toSearchKind(): StudentListingsApi.KindStudentListingSearchList? =
        StudentListingsApi.KindStudentListingSearchList.entries.firstOrNull { it.value == name }

    /**
     * Koordinatasiz `NEAREST` — serverda xato emas, u jimgina `NEWEST` ga tushadi. Buni
     * klientda ham aniq qilamiz: aks holda foydalanuvchi "eng yaqin" tanlab, tasodifiy
     * tartibdagi ro'yxatni ko'rardi va sababi hech qayerda ko'rinmasdi.
     */
    private fun ListingSort.toApiSort(geo: ListingGeoFilter?): StudentListingsApi.SortStudentListingSearchList {
        val effective = if (this == ListingSort.NEAREST && geo?.hasPoint != true) ListingSort.NEWEST else this
        return StudentListingsApi.SortStudentListingSearchList.entries
            .first { it.value == effective.name }
    }
}
