package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.Region
import kotlinx.coroutines.flow.Flow

/**
 * Foydalanuvchi viloyati — e'lonlar feed'ining geo filtri.
 *
 * Tanlov ilova sozlamalarida (`AppSettingEntity`) saqlanadi, ya'ni sessiyadan mustaqil:
 * chiqib qayta kirilса ham o'sha viloyat qoladi. Tanlanmagan bo'lsa feed butun mamlakat
 * bo'yicha keladi — shuning uchun [syncWithUniversity] uni foydalanuvchi universitetining
 * manzilidan o'zi topib qo'yadi.
 */
interface RegionRepository {

    /** Backend'dagi viloyatlar ro'yxati (`GET /v1/geo/regions`). */
    suspend fun regions(): Resource<List<Region>>

    /** Tanlangan viloyat; `null` — filtr yo'q (butun O'zbekiston). */
    fun observeSelected(): Flow<Region?>

    /**
     * Tanlangan viloyat id'si — **sinxron**, chunki uni qidiruv so'rovini yig'ayotgan
     * data-source o'qiydi (manba local baza, chaqiruv arzon).
     */
    fun selectedId(): String?

    /** Viloyatni saqlaydi; `null` — filtrni olib tashlaydi. */
    suspend fun select(region: Region?)

    /**
     * Viloyatni foydalanuvchi universitetiga moslaydi.
     *
     * Qayta hisoblash FAQAT [universityId] o'zgarganda bo'ladi (oxirgi ishlatilgani local
     * saqlanadi). Ya'ni:
     * - profilda universitet almashtirilsa — viloyat ham darrov o'zgaradi;
     * - foydalanuvchi Filter/Sozlamalardan qo'lda boshqa viloyat tanlagan bo'lsa — u
     *   universitet o'zgarmagunча saqlanadi (ilova har ochilganda ustidan yozilmaydi).
     *
     * [address] — universitetning to'liq manzili (prof-emis `address`); viloyat shundan topiladi.
     */
    suspend fun syncWithUniversity(universityId: String?, address: String?)
}
