# Talaba e'lonlari — klient integratsiya qilindi (backendga javob)

`STUDENT_LISTINGS_RESPONSE.md` bo'yicha ilova to'liq ulandi: e'lon yaratish → e'lon
qilish → qidiruv → sahifalash → status → o'chirish. Yo'l `/v1/student-listings` ga
o'zgartirildi, `generateAllApi` qayta ishga tushirildi, DTO/enum nomlari o'zgarmadi.

Quyida **sizdan** kerak bo'lgan uchta narsa (§1–§3) va so'raganlaringizga javob (§4–§6).

---

## 1. ⚠️ Spec'da 4 ta sxema YO'Q — generatsiya yiqiladi

`CreateStudentListingDto.details`, `UpdateStudentListingDto.details` va
`StudentListingDto.details` quyidagilarga `$ref` qiladi:

```
#/components/schemas/TaskDetailsDto
#/components/schemas/RentalDetailsDto
#/components/schemas/ServiceDetailsDto
#/components/schemas/JobDetailsDto
```

`components.schemas` da bu to'rttasining **birortasi ham yo'q** — havolalar bo'shliqqa
qaraydi. Har qanday kod generatori shu yerda to'xtaydi.

Hozircha bizda ular `cleanSwagger` ning 12-qadamida erkin JSON obyektiga
(`JsonObject`) aylantiriladi va `details` **qo'lda** — `StudentListingApiMappers.kt` da,
spec §4 bo'yicha — o'qiladi/yoziladi. Ya'ni ishlayapti, lekin kontrakt hujjatlashtirilmagan
va sxemani biz taxmin qilib yozdik.

**Iltimos:** to'rttasini `components.schemas` ga qo'shing. Ajratgich — `kind`
(§4 dagidek), shakl esa aynan §4.1–§4.4. Shundan keyin biz 12-qadamni olib tashlab,
tiplangan variantga o'tamiz.

---

## 2. ⚠️ `POST /v1/student-listings/search` tanasi noto'g'ri sxemaga qarab turibdi

`SearchListingsDto` ikkita begona sxemani ishlatadi:

| Maydon | Spec'dagi `$ref` | Nima uchun yaramaydi |
|---|---|---|
| `filter` | `SearchFilterDto` | Bu **biznes chegirmalarining** filtri: `groupKeys`, `businessIds`, `discount`, `redemption`, `attributes`… Sizning §7.2.1 dagi `gender` / `propertyType` / `minRooms` / `serviceType` / `shift` / `taskCategory` maydonlarining **birortasi ham yo'q** |
| `page` | `SearchPageDto` | `{ number, size }` — **`cursor` yo'q**, ya'ni §7.2.2 dagi asosiy (kursorli) rejimni ifodalab bo'lmaydi |

`GET /v1/student-listings` da esa hammasi joyida: 15 ta turga xos filtr, `sort`, `size`,
`cursor`, `page`. Shu sabab ilova **hozircha `GET` bilan ishlaydi** — §5.1 ga ko'ra
ikkalasi bir xil kod yo'lidan o'tadi, ya'ni natija farq qilmaydi.

**Iltimos:** `SearchListingsDto` uchun o'z sxemalaringizni bering
(`StudentListingFilterDto` + `cursor` li `ListingPageRequestDto`). Biz `POST` ga o'tamiz —
bu bir necha qatorlik ish, lekin `bbox` (xarita ekrani) faqat `POST` da bor va u bizga
kerak bo'ladi.

---

## 3. ⚠️ Yuborilgan spec'da `/v1/stories/archive` yo'q

`StudentListing add json.json` da `/v1/stories/archive` yo'li va `StoryArchivePageDto`
sxemasi yo'q — ular avvalgi spec'da bor edi va ilovada ishlatiladi (profildagi story
arxivi, `STORY_ARCHIVE_BACKEND.md`).

Biz spec'ni **birlashtirib** oldik (eski + yangi 6 ta yo'l), shuning uchun ilova
buzilmadi. Lekin keyingi safar to'liq spec yuborilsa yaxshi bo'lardi — aks holda uni
har safar qo'lda solishtirishga to'g'ri keladi.

---

## 3b. Kichik nomuvofiqlik: narx `int64` mi, `int32` mi?

`STUDENT_LISTINGS_BACKEND.md` §2.2 da `price` / `priceMax` — **`int64`**, spec'da esa
formatsiz `integer`, ya'ni generator uchun `int32`. Klientda narx `Long`, simga
chiqarishda `0..2_147_483_647` oralig'iga kesiladi (aks holda `toInt()` manfiy songa
aylanib, e'lon "−2 mlrd so'm" bo'lib ketardi).

Talaba e'lonlarida 2.1 mlrd so'mlik narx amalda uchramaydi, shuning uchun shoshilinch
emas. Lekin `"format": "int64"` qo'yib qo'ysangiz kesish umuman kerak bo'lmaydi.

---

## 4. `GeoCatalog` — bizdagi 14 viloyat / 193 tuman (so'raganingiz §6.2)

Bizniki **kichikroq to'plam** bo'lishi kutilyapti (sizda 210). Slug qoidasi:
lotin harflari + raqamlar `UPPERCASE`, probel/tire → `_`, apostrof (`'`, `ʻ`)
**tashlanadi**: `Mirzo Ulug'bek` → `MIRZO_ULUGBEK`, `Qo'qon` → `QOQON`.

To'liq ro'yxat — pastda. Farq topilsa, **sizdagi** id yutadi (biz katalogni yangilaymiz),
faqat qaysilari o'zgarganini ayting: saqlangan e'lonlardagi `branches[].districtId`
migratsiya qilinishi kerak, aks holda ular hudud filtridan tushib qoladi.

### TOSHKENT_SHAHRI — Toshkent shahri (12)
BEKTEMIR, CHILONZOR, MIROBOD, MIRZO_ULUGBEK, OLMAZOR, SERGELI, SHAYXONTOHUR, UCHTEPA, YAKKASAROY, YANGIHAYOT, YASHNOBOD, YUNUSOBOD
### TOSHKENT_VILOYATI — Toshkent viloyati (18)
ANGREN, BEKOBOD, BOKA, BOSTONLIQ, CHINOZ, CHIRCHIQ, NURAFSHON, OHANGARON, OLMALIQ, OQQORGON, PARKENT, PISKENT, QIBRAY, QUYICHIRCHIQ, ORTACHIRCHIQ, YANGIYOL, YUQORICHIRCHIQ, ZANGIOTA
### ANDIJON_VILOYATI — Andijon viloyati (16)
ANDIJON_SHAHRI, ANDIJON_TUMANI, ASAKA, BALIQCHI, BOZ, BULOQBOSHI, IZBOSKAN, JALAQUDUQ, MARHAMAT, OLTINKOL, PAXTAOBOD, QORGONTEPA, SHAHRIXON, ULUGNOR, XOJAOBOD, XONOBOD
### BUXORO_VILOYATI — Buxoro viloyati (12)
BUXORO_SHAHRI, BUXORO_TUMANI, GIJDUVON, JONDOR, KOGON, OLOT, PESHKU, QORAKOL, QOROVULBOZOR, ROMITAN, SHOFIRKON, VOBKENT
### FARGONA_VILOYATI — Farg'ona viloyati (18)
FARGONA_SHAHRI, FARGONA_TUMANI, MARGILON, QOQON, QUVA, QUVASOY, BESHARIQ, BOGDOD, BUVAYDA, DANGARA, FURQAT, OLTIARIQ, RISHTON, SOX, TOSHLOQ, UCHKOPRIK, OZBEKISTON, YOZYOVON
### JIZZAX_VILOYATI — Jizzax viloyati (12)
JIZZAX_SHAHRI, ARNASOY, BAXMAL, DOSTLIK, FORISH, GALLAOROL, MIRZACHOL, PAXTAKOR, SHAROF_RASHIDOV, YANGIOBOD, ZAFAROBOD, ZOMIN
### NAMANGAN_VILOYATI — Namangan viloyati (12)
NAMANGAN_SHAHRI, NAMANGAN_TUMANI, CHORTOQ, CHUST, KOSONSOY, MINGBULOQ, NORIN, POP, TORAQORGON, UCHQORGON, UYCHI, YANGIQORGON
### NAVOIY_VILOYATI — Navoiy viloyati (10)
NAVOIY_SHAHRI, ZARAFSHON, KARMANA, KONIMEX, NAVBAHOR, NUROTA, QIZILTEPA, TOMDI, UCHQUDUQ, XATIRCHI
### QASHQADARYO_VILOYATI — Qashqadaryo viloyati (13)
QARSHI_SHAHRI, QARSHI_TUMANI, SHAHRISABZ, CHIROQCHI, DEHQONOBOD, GUZOR, KASBI, KITOB, KOSON, MIRISHKOR, MUBORAK, NISHON, YAKKABOG
### QORAQALPOGISTON_RESPUBLIKASI — Qoraqalpog'iston Respublikasi (16)
NUKUS_SHAHRI, NUKUS_TUMANI, AMUDARYO, BERUNIY, CHIMBOY, ELLIKQALA, KEGEYLI, MOYNOQ, QANLIKOL, QONGIROT, QORAOZAK, SHUMANAY, TAXIATOSH, TAXTAKOPIR, TORTKOL, XOJAYLI
### SAMARQAND_VILOYATI — Samarqand viloyati (16)
SAMARQAND_SHAHRI, SAMARQAND_TUMANI, KATTAQORGON_SHAHRI, KATTAQORGON_TUMANI, BULUNGUR, ISHTIXON, JOMBOY, NARPAY, NUROBOD, OQDARYO, PASTDARGOM, PAXTACHI, PAYARIQ, QOSHRABOT, TOYLOQ, URGUT
### SIRDARYO_VILOYATI — Sirdaryo viloyati (11)
GULISTON_SHAHRI, GULISTON_TUMANI, SHIRIN, YANGIYER, BOYOVUT, MIRZAOBOD, OQOLTIN, SARDOBA, SAYXUNOBOD, SIRDARYO_TUMANI, XOVOS
### SURXONDARYO_VILOYATI — Surxondaryo viloyati (15)
TERMIZ_SHAHRI, TERMIZ_TUMANI, ANGOR, BANDIXON, BOYSUN, DENOV, JARQORGON, MUZRABOT, OLTINSOY, QIZIRIQ, QUMQORGON, SARIOSIYO, SHEROBOD, SHORCHI, UZUN
### XORAZM_VILOYATI — Xorazm viloyati (12)
URGANCH_SHAHRI, URGANCH_TUMANI, XIVA, BOGOT, GURLAN, HAZORASP, QOSHKOPIR, SHOVOT, TUPROQQALA, XONQA, YANGIARIQ, YANGIBOZOR
---

## 5. `ServiceCatalog` (so'raganingiz §6.1)

`dev/feature/listings/domain/.../ServiceCatalog.kt` — 12 soha, ~90 yo'nalish, ~60 maydon.
Fayl uzun, shuning uchun uni alohida yuboramiz yoki repodan olasiz; shakli §4.3 dagidek
(`AttributeSpec`: `key`, `label`, `kind`, `options`, `required`, `hint`, `suffix`).

Hozircha `fields.subject` **tekshirilmasligi** biz uchun to'sqinlik emas: klient
validatsiyasi o'sha katalog bo'yicha ishlaydi va yaroqsiz e'lon serverga bormaydi.
Ya'ni bu Faza 2 ga qolsa ham bo'ladi.

---

## 6. Klient nima qilyapti — kelishuvlarni tasdiqlash

| Nima | Holat |
|---|---|
| `audience` | **Yuborilmaydi** (§7 dagi ogohlantirish bo'yicha). Faza 2 da yoqamiz |
| `Idempotency-Key` | Yuboriladi. Kalit **tasodifiy emas** — `"{listingId}:{updatedAt}"`, ya'ni "Yuborish" ikki marta bosilsa kalit ham bir xil bo'ladi |
| `PATCH` da `kind` | Yuborilmaydi (`409 LISTING_KIND_IMMUTABLE` dan qochish uchun) |
| Qoralamani e'lon qilish | `PATCH` → so'ng `POST /{id}/submit` (ikki qadam, chunki `submit` faqat `POST /` da bor). `ACTIVE` e'lon tahrirlanganda ikkinchi qadam yo'q — §3 dagidek `PATCH` ning o'zi qayta validatsiya qiladi |
| `TASK` da `validTo` | Formada **`deadline` bilan cheklanadi** (§6 dagi `VALIDITY` qoidasi). Foydalanuvchi "30 kun" tanlab, muddatni ertaga qo'ysa e'lon muddati ham ertaga bo'ladi |
| Amal muddati | Formada 7 / 14 / 30 / 60 / 90 kun — 90 kunlik chegaradan oshmaydi |
| `NEAREST` | `lat`/`lng` **faqat shu tartib tanlanganda** yuboriladi; joylashuv noma'lum bo'lsa tartib ro'yxatda umuman ko'rinmaydi |
| `radiusMeters` | **Yuborilmaydi** (foydalanuvchi aniq so'ramaguncha). `lat`/`lng` ning o'zi berilganda radius filtri qo'llanmasligini tasdiqlang — aks holda uzoqdagi e'lonlar jimgina tushib qoladi |
| Sahifalash | Kursorli (`size=20`). Sahifa raqamli rejim faqat filtr oynasidagi "Qo'llash · N" soni uchun (`page=1&size=1`, javobdan `total` olinadi) |
| `422 LISTING_VALIDATION_FAILED` | `error.fields` kalitlari `ListingField` nomlariga (`GENDER`, `TASK_DEADLINE`, `VALIDITY`…) bog'lanadi va matn aynan o'sha maydon ostida chiqadi |
| `DISCOUNT` turi | Bu API'ga **hech qachon** yuborilmaydi — u biznes tomonining shartnomasi |

---

## 7. Faza 2 — holat

### ✅ Bajarildi: `audience` (§7.2.4 ning klient qismi)

`CreateStudentListingDto` / `UpdateStudentListingDto` / `StudentListingDto` da maydon
paydo bo'lgani uchun ulandi:

- e'lon formasida "Kim ko'radi" tanlovi — `Hammaga` · `Yaqin universitetlar` ·
  `Faqat universitetim`; odatiy `ALL` (spec'dagidek);
- profilda universitet ko'rsatilmagan bo'lsa tanlov **umuman ko'rsatilmaydi** va so'rovga
  doim `ALL` ketadi. Sabab: `MY_UNIVERSITY` doirasi `universityId` dan hisoblanadi, usiz
  e'lon hech kimga ko'rinmay qolardi. Tanlov keyin universitet olib tashlansa ham `ALL` ga
  qaytadi;
- qiymat local bazada ham saqlanadi (`ListingEntity.audience`, migratsiya 29 → eski
  qatorlar `ALL`), ya'ni oflaynda yaratilgan qoralama ham doirani yo'qotmaydi.

### ⛔️ Kutilmoqda — spec'da hali yo'q

Tekshirdik (`swaggerstudent.json`, 2026-08-03):

| Kerak | Spec'dagi holat |
|---|---|
| `universityRelation` (`SAME`/`NEAREST`/`OTHER`), `universityName` | `StudentListingDto` da **yo'q** |
| `universityIds[]`, `onlyMyUniversity`, `includeNearbyUniversities` filtrlari | `GET /v1/student-listings` query'sida **yo'q** (`sort=RELEVANCE` bor, lekin uni hisoblaydigan signal yo'q) |
| `GET /listings/catalog?kind=…` (§7.3) | **yo'q** — kataloglar hali ilovada hardcode |
| `POST /v1/conversations` da `listingId` (§7.5) | `OpenDirectDto` da faqat `studentId` |
| Sevimlilar (`/favorite/toggle`), `POST /search/map`, `POST /suggest` | faqat **chegirmalar** tomonida bor (`/v1/discounts/*`), talaba e'lonlarida yo'q |

Bular chiqqanda ilova tomonidagi ish kam: `universityRelation` ni kartada ko'rsatish va
filtr oynasiga "Faqat universitetim" tugmasini qo'shish.

`apply` / `applicationsCount` (§10 Q4) — hozircha kerak emas, chat va telefon yetarli.
