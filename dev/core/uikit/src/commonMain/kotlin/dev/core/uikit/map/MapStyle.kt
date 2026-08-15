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
/**
 * ⚠️ Xarita HAR DOIM yorug' — ilova mavzusi qorong'i bo'lganda ham.
 *
 * Ilgari qorong'i rejimda CARTO "dark-matter" uslubiga o'tilardi va natija yomon edi:
 * ko'chalar deyarli ko'rinmas, yashil/ko'k markerlar qora fonda bir-biriga qo'shilib
 * ketardi. Xarita — rasm emas, MA'LUMOT: unda mo'ljal (ko'cha, bino, suv) o'qilishi
 * kerak. Yandex Maps, 2GIS va Google Maps ham mobil ilovada sukut bo'yicha aynan shu
 * yo'ldan boradi — interfeys qorong'i bo'lsa ham xarita yorug' qoladi.
 */
internal fun mapStyleUrl(): String =
    "https://tiles.openfreemap.org/styles/liberty" // sariq yo'llar, POI — Yandex uslubi

/** Plitkalar kelguncha ko'rinadigan fon — xarita "oq ekran" bo'lib turmasin. */
internal fun mapBackgroundColor(): String = "#E8E6F2"

/**
 * WebView'ning baza manzili. Sahifa plitka serveri bilan bir origin'da bo'lsa WebView
 * so'rovlarni to'smaydi.
 */
internal const val MAP_BASE_URL = "https://tiles.openfreemap.org"

/** Xaritaning boshlang'ich yaqinlashuvi — ko'cha nomlari o'qiladigan daraja. */
internal const val MAP_DEFAULT_ZOOM = 13.5

/** Joy tanlashda yaqinroq turamiz — uy raqami darajasida aniqlik kerak. */
internal const val MAP_PICKER_ZOOM = 16.0

/**
 * Shu masshtabdan PASTDA marker yorliqlari (narx / do'kon nomi) yashiriladi va faqat pin
 * ikonasi qoladi.
 *
 * Ataylab past qiymat: butun shahar ko'rinib turgan masshtabda ham yorliqlar o'qilsin
 * (ustma-ust tushmasligi yorliqlarni yashirish bilan emas, yaqin e'lonlarni siljitilgan
 * ustunga terish bilan hal qilinadi). Faqat viloyat/mamlakat ko'rinishida ular olib
 * tashlanadi — u yerda o'nlab yorliq baribir o'qilmasdi.
 */
internal const val MAP_LABEL_ZOOM = 9.0

/**
 * Xaritani FAQAT surish va masshtablashga cheklaydi — burish va egish o'chiriladi.
 *
 * MapLibre'da ikki barmoq bir vaqtning o'zida uchta ishorani boshqaradi: zoom, burish
 * (rotate) va egish (pitch). Foydalanuvchi shunchaki kattalashtirmoqchi bo'lganda barmoqlar
 * bir necha gradusga qiyshaysa ham xarita aylanib yoki egilib ketardi — belgilar go'yo o'z
 * joyidan chiqib boshqa yerga o'tayotgandek ko'rinardi. Kompas tugmasi yashirilgani uchun
 * xarita aylanib qolgani bilinmasdi ham va uni tiklashning iloji yo'q edi.
 *
 * Shimoliy yo'nalish doim tepada qoladi — belgilar faqat masshtabga qarab yaqinlashadi
 * yoki uzoqlashadi, boshqa hech qanday harakat yo'q.
 */
internal fun mapLockRotationJs(): String = """
    map.dragRotate.disable();
    map.touchZoomRotate.disableRotation();
    if (map.touchPitch) map.touchPitch.disable();
    map.keyboard.disableRotation();
""".trimIndent()
