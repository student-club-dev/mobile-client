# Geo kontrakti va metro bekatlari — ilova javobi

`DISCOUNTS_BUSINESS_API_RESPONSE.md` bo'yicha **talaba ilovasi** (StudentClub) tomonidagi javob.
Biznes ilovasi (ElonUz) alohida loyihada, u alohida javob beradi.

Qisqasi: yangi uchta geo endpoint spec'ga qo'shildi, klient qayta generatsiya qilindi va ilova
kontrakt yo'llariga o'tdi. Ochiq savolga (§6) javob — **1-variant**.

---

## 1. §6 ochiq savol — `GET /geo/metro-stations`

**Spec'ga qo'shildi (1-variant).** `dev/api-client-generator/student-club.json` ga
`swaggerstudent.json` dagi holicha ko'chirildi, klientda `GeoApi.getMetroStations()` paydo
bo'ldi.

Sabab: kontraktdan tashqarida qo'lda chaqirilgan yagona endpoint bo'lib qolardi, ya'ni yo'l
o'zgarsa ilova buni faqat ish vaqtida — `404` bilan — bilardi. Spec'da bo'lgani uchun endi uni
generatsiya bosqichi ushlaydi.

Biznes spec'ida (`elon-uz.json`) ham bor — tekshirildi, so'rov qolmadi.

## 2. Ilovada nima o'zgardi

| Endpoint | Ilovada |
|---|---|
| `GET /geo/regions` | Viloyat ro'yxati (feed geo filtri, sozlamalar). Ilgari `/regions` chaqirilardi. |
| `GET /geo/regions/{regionId}/districts` | Tumanlar — **birinchi marta serverdan**. Ilgari ilovadagi statik ro'yxat edi. |
| `GET /geo/metro-stations` | Mo'ljal uchun bekatlar ro'yxati; local keshda saqlanadi. |
| `POST /geo/reverse-geocode` → `nearestMetro` | Endi o'qiladi: e'lon manzilining **mo'ljali** ("Chilonzor metrosi yaqinida"), e'lon sahifasida manzil ostida ko'rinadi. |

Uchalasi ham local bazada keshlanadi (`AppSettingEntity`), shuning uchun manzil tanlash oqimi
oflaynda ham to'liq ishlaydi.

### 2.1 `/regions` va `/districts` ilova spec'idan olib tashlandi

§5.3 bo'yicha ular admin panelniki. Ikkalasini ham spec'da qoldirsak generator ikkita bir xil
`getRegions` ko'rgani uchun metod nomlarini `regionsGetRegions` / `geoRegionsGetRegions` qilib
buzardi. Ilova endi **faqat kontrakt yo'llarini** chaqiradi — sizning tomoningizda ikkalasi ham
ishlashda davom etaversin, biz eskisiga murojaat qilmaymiz.

### 2.2 `nearestMetro` — 3 km qoidasi ikki yo'lda ham bir xil

Geokoder `503` bergan holat uchun ilovada Nominatim zaxirasi bor (u `nearestMetro` bermaydi).
O'sha yo'lda bekat `/geo/metro-stations` keshidan **klientda** hisoblanadi va aynan sizdagi
**3 km** chegarasi qo'llanadi — aks holda bir xil nuqta ikki xil mo'ljal berardi.

Chegara sizda o'zgarsa ayting: u ilovada bitta konstanta
(`GeoCatalog.METRO_LANDMARK_RADIUS_METERS`).

---

## 3. Kelishmovchiliklar bo'yicha — hammasi qabul

| Bo'lim | Javob |
|---|---|
| §5.1 27 ta biznes tur, `attributes` bazadan | Qabul. Talaba feed'i allaqachon `catalog/groups` + `catalog/types` + `catalog/filter-schema` dan quriladi, ilovada birorta tur hardcode qilinmagan. |
| §5.2 `AttributesSchemaDto` (JSON Schema emas) | Qabul — ikkinchi parser kerak emas. Bu endpoint biznes ilovasiniki, talaba tomonida ishlatilmaydi. |
| §5.4 `GET /discounts` qurilmaydi | Qabul, ilova `POST /v1/discounts/search` ni chaqiradi. |
| §5.5 Firebase emas, o'z JWT'ingiz | To'g'ri, ilova ancha oldin o'tgan. Hujjatdagi "Firebase ID token" eskirgan. |
| §5.6 `403 FORBIDDEN` yagona kod, begona resurs → 403 | Qabul. Ilova 403'ni "ruxsat yo'q" deb ko'rsatadi va sizning o'zbekcha `message` ingizni chiqaradi. |
| §5.7 `geohash` hisoblanmaydi | **Kerak emas.** Ilova yaqinlik bo'yicha hech narsa hisoblamaydi, masofani server beradi. Qo'shmang. |

### §3 — `MODERATION_ENABLED`

Qabul: ilova **hech qaysi statusni oldindan taxmin qilmaydi**, javobdagi `status` ni o'qiydi
(`ApiListingRemoteDataSource`: `submit` faqat javob `ACTIVE`/`SCHEDULED` bo'lmaganda yuboriladi).
Bayroqni yoqsangiz ilova kodi o'zgarmaydi.

### §4 — limitlar

`429` javobning `error.message` i ekranda o'zbekcha ko'rinadi, `error.code`
(`RATE_LIMITED`, `LISTING_LIMIT_REACHED`) esa saqlanadi. Bu limitlar biznes egasiga tegishli,
talaba ilovasida ularga bormaydigan oqim yo'q.

---

## 4. Story arxivi — savol yopildi

Oraliq nusxada (`student.json`, 21:40) `GET /v1/stories/archive` va `StoryArchivePageDto`
tushib qolgan edi; keyingi nusxada (`swaggerstudent.json`, 22:44) ular joyida, ustiga
hujjati ham to'ldirilgan. Ilova spec'i o'shanga tenglashtirildi — savol yo'q.

Yangi maydon **`StoryDto.archivedMediaPurged`** ham ulandi:

- domenda `Story.mediaPurged`;
- `true` bo'lganda profil to'rida va lavha ko'rgichida serverga **umuman so'rov
  yuborilmaydi** — o'rniga bo'sh katak va "fayli saqlanmagan" izohi chiziladi. Aks holda
  `404` yuklovchida "buzilgan rasm" bo'lib ko'rinardi;
- telefonda local nusxa qolgan bo'lsa lavha baribir ochiladi.

`viewsCount` arxivda muzlatilgani va `expiresAt` o'tmishda bo'lishi ilovada allaqachon
to'g'ri ishlanadi — ular alohida holat sifatida qaralmaydi.
