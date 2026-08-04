package dev.feature.listings.domain.model

/**
 * E'lon id'sining kelib chiqishi.
 *
 * Server e'longa o'z ULID'ini beradi, lekin forma e'lonni **yaratishdan oldin** ham to'liq
 * model sifatida quradi (qoralama saqlash, rasm qo'shish, tahrirlash). Shu sabab id ikki
 * xil bo'ladi va repository ikkalasini ajrata olishi shart: `POST` (yangi) va `PATCH`
 * (mavjud) — bir xil ko'rinadigan, lekin butunlay boshqa amallar.
 *
 * Ajratgich — **prefiks**: [LOCAL_PREFIX] bilan boshlangan id hech qachon serverda bo'lmagan.
 * Qolgan hammasi server bergan id deb qaraladi; xato bo'lsa (masalan eski, backendsiz
 * davrdan qolgan qator) `PATCH` `404` beradi va repository uni yangi e'lon sifatida
 * qaytadan yuboradi.
 */
object ListingIds {

    private const val LOCAL_PREFIX = "local-"

    /** Formada qurilgan yangi e'lon uchun vaqtinchalik id. */
    fun newLocalId(ownerId: String, now: Long): String = "$LOCAL_PREFIX$ownerId-$now"

    /** `true` — e'lon hali serverga bormagan. */
    fun isLocal(id: String): Boolean = id.startsWith(LOCAL_PREFIX)
}
