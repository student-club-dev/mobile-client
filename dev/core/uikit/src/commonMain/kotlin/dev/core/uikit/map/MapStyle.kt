package dev.core.uikit.map

/**
 * Ilovadagi BARCHA xaritalar uchun yagona manba.
 *
 * Ilgari ikki xil xarita bor edi: e'lonlarni ko'rsatish MapLibre vektor plitkalarida, joy
 * tanlash esa qo'lda yozilgan raster plitka dvigatelida ishlardi. Natijada bir ekranda
 * ko'rilgan ko'cha boshqa ekranda butunlay boshqacha chiqar, zoom bosqichlari va ishoralar
 * ham mos kelmasdi. Endi ikkalasi ham shu yerdagi uslub va bir xil MapLibre dvigatelidan
 * foydalanadi — foydalanuvchi qayerda bo'lmasin bitta xaritani ko'radi.
 *
 * Ikkala uslub ham bepul va API kalit talab qilmaydi (ma'lumot — OpenStreetMap).
 */
internal fun mapStyleUrl(dark: Boolean): String =
    if (dark) "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    else "https://tiles.openfreemap.org/styles/liberty" // sariq yo'llar, POI — Yandex uslubi

/** Plitkalar kelguncha ko'rinadigan fon — xarita "oq ekran" bo'lib turmasin. */
internal fun mapBackgroundColor(dark: Boolean): String = if (dark) "#12101F" else "#E8E6F2"

/**
 * WebView'ning baza manzili. Sahifa plitka serveri bilan bir origin'da bo'lsa WebView
 * so'rovlarni to'smaydi.
 */
internal const val MAP_BASE_URL = "https://tiles.openfreemap.org"

/** Xaritaning boshlang'ich yaqinlashuvi — ko'cha nomlari o'qiladigan daraja. */
internal const val MAP_DEFAULT_ZOOM = 13.5

/** Joy tanlashda yaqinroq turamiz — uy raqami darajasida aniqlik kerak. */
internal const val MAP_PICKER_ZOOM = 16.0
