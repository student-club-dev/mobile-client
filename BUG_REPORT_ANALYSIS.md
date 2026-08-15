# StudentClub — Bug Report tahlili (2026-08-15)

Manba: `~/Desktop/StudentClub Bug.docx` (43 ta skrinshot + izohlar, Sherzod Jo'ra).
Har bir band skrinshot tartibida raqamlangan; `#N` — hujjatdagi `imageN.png`.

**Holat: 43 ta bandning hammasi ko'rib chiqildi.** ✅ — tuzatildi, ☑️ — tekshirildi va
tuzatish talab qilmadi (sabab ustunda).

⚠️ **Qurilmada sinalmagan.** Kod kompilyatsiya bo'ladi, testlar o'tadi va APK yig'iladi,
lekin sessiya davomida telefon/emulyator ulanmagan edi. Quyidagi bandlar xatti-harakatga
bog'liq va HAQIQIY qurilmada tekshirilishi kerak: **C3** (bitta xabarni belgilash),
**C5** (klaviatura ostidagi xabarlar), **C8** (Android 14 ruxsat oynasi), **C4**
(«internet yo'q» — sabab yo'q qilindi, lekin xatoning o'zi qayta ishlab chiqarilmadi).

---

## A. Autentifikatsiya (Auth)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| A1 (#1, #21) | Login ekranida til va mavzu almashtirgichi yo'q | Sign-in ekranining yuqorisiga language switcher + theme switcher; default til — **English** | ✅ Kirish/tanishtiruv ekranida `AuthTopControls` (EN·RU·UZ + quyosh/oy) |
| A2 (#2) | Google tugmasi faqat ikonka | «Continue with Google» matni qo'shilsin (UX) | ✅ `SocialRow(googleLabel = …)` — «Continue with Google» |
| A3 (#3) | Universitet tanlashda ortiqcha «Select» tugmasi | Ro'yxatdan bosilgan element darhol tanlanadi va sheet yopiladi | ✅ Qator bosilishi tanlaydi va varaqni yopadi; tugma olib tashlandi |
| A4 (#4) | Placeholder rangi to'ldirilgan input kabi ko'rinadi | Placeholder `onSurfaceVariant`ga yaqin, past kontrastli rang; matn saqlanadi | ✅ `AppPalette.placeholder` tokeni + namuna matni («e.g. aziz@tuit.uz») |
| A5 (#5) | Reset password: raqamli klaviatura tugmani berkitadi; input tashqarisiga bosilganda klaviatura yopilmaydi | `imePadding` + scroll; tashqariga bosilganda focus tozalanadi | ✅ Tugmalar `bottomBar` ga ko'chdi; `AppScreenScaffold` tashqi bosishda fokusni tozalaydi |
| A6 (#6) | Parol tiklangach login sahifasiga qaytariladi | Reset javobidagi token bilan **avto-login** | ✅ `resetPassword` muvaffaqiyatdan keyin yangi parol bilan avtomatik kiradi |

## B. Bosh ekran (Home)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| B1 (#7) | «Hello 👋» ortiqcha | Olib tashlansin | ✅ Olib tashlandi |
| B2 (#7) | Xabar/bildirishnoma ikonkalari ism+avatar bilan bir qatorda | Ikonkalar yuqoriroq qatorga chiqarilsin, ui buzilmasin | ✅ Amal tugmalari alohida yuqori qatorda |
| B3 (#7) | Bottom menu kontentga yopishgan | Menyu ortiga yumshoq soya (shadow/scrim) | ✅ Panel ostiga fon rangiga eriydigan gradient + kuchliroq soya |
| B4 (#8) | Stories bloki tepa/past marjini katta | Marjin biroz kamaytirilsin (siqilib qolmasin) | ✅ 22dp → 12dp |
| B5 (#9) | Bir joyda «All», boshqasida «More» | Hamma joyda bitta so'z — **All** | ✅ Hamma joyda `uiStrings().all` |
| B6 (#19) | Yuklanish paytida bo'sh joy / aylanma indikator | Skeleton (shimmer) ko'rsatilsin | ✅ Talabalar bo'limlari uchun `StudentsSkeleton`; E'lonlar ekranining BIRINCHI yuklanishi ham spinnerdan skeletga o'tdi (aylanma faqat keyingi sahifada) |
| B7 (#20) | Scrollda header cho'zilib qotib qaytadi | Collapse animatsiyasi smooth bo'lsin | ✅ Siqilish endi scroll masofasidan uzluksiz hisoblanadi |
| B8 (#22) | Food ichidan bitta kartani bosganda umumiy ro'yxat ochiladi | Karta bosilsa **listing detail** ochilsin (barcha kategoriyalar uchun) | ✅ `onOpenOffer` → `discounts?group=…&offer=…`, tafsilot darhol ochiladi |

## C. Chat / Xabarlar / Bildirishnomalar

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| C1 (#10) | Yangi xabar bildirishnomada faqat refreshdan keyin chiqadi; bir kishidan kelgan xabarlar guruhlanmagan | WS orqali realtime qo'shilsin; bitta suhbat = bitta qator | ✅ `RealtimeSignals.incomingMessages` + `groupChatNotifications` (testlangan) |
| C2 (#11) | Chat bildirishnomasini bosganda chat ochilmaydi | Bosilganda o'sha suhbatga navigatsiya | ✅ `NotificationTarget.Conversations` — id bo'lmasa suhbatlar ro'yxati (testlangan) |
| C3 (#12) | Bitta xabarni tanlab bo'lmaydi (darhol bekor bo'ladi); 2+ ishlaydi | Uzun bosish 1 ta xabarni ham tanlagan holda qoldirsin | ✅ Uzun bosishdan keyingi bosish yutiladi (`suppressTap`) |
| C4 (#13, #14, #27) | Ilovaga qayta kirganda «No internet connection» xatosi | Xato manbasi (calls socket / ice-servers) topilib, foydalanuvchiga ko'rsatilmasin | ✅ WS token yangilash `ice-servers` ni chaqirmaydi; toastlar takrorlanmaydi; offline bir marta qayta tekshiriladi |
| C5 (#15) | Klaviatura ochilganda pastdagi xabarlar ko'rinmaydi | Ro'yxat IME balandligiga surilsin va oxirgi xabarga scroll | ✅ Ro'yxat IME balandligining HAR o'zgarishida pastda ushlanadi |
| C6 (#16) | Bubble kengligi noto'g'ri — matn erta ko'chadi | Oxirgi qator + vaqt bir tekislikda joylashadigan flow layout | ✅ `TextWithTrailingMeta` — vaqt oxirgi qator yonida |
| C7 (#17) | Stiker paneli va klaviatura bir vaqtda ochiq; emoji yo'q | Panel ochilganda IME yopilsin; **Emoji** tab qo'shilsin | ✅ Panel ochilganda IME yopiladi; **Emoji** bo'limi qo'shildi |
| C8 (#43) | Galereya ruxsati Telegramdagidek so'ralmaydi | Tizim ruxsat dialogi (to'liq/qisman/rad) chaqirilsin | ✅ `READ_MEDIA_VISUAL_USER_SELECTED` + `GalleryAccess.LIMITED`; varaq ochilishi bilan tizim oynasi |

## D. Tarmoq (Network)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| D1 (#18, #21) | `GET /v1/calls/ice-servers` ilova ochilganda va davriy chaqiriladi | Faqat qo'ng'iroq boshlanganda; natija keshlansin | ✅ `HttpClient.refreshSession(...)` — soket endi `ice-servers` ni chaqirmaydi |
| D2 (#18) | `POST /v1/student-listings/search` ikki marta ketadi | Takroriy so'rov olib tashlansin (debounce / bitta manba) | ☑️ Takror emas: ikkalasi ham kerak (`TASK` va `RENTAL` bo'limlari uchun alohida so'rov) |
| D3 (#18) | Ilova ochilishida ortiqcha POSTlar (`catalog/groups`, `devices`) | `catalog/groups` keshlansin; `devices` token o'zgarganda | ☑️ Kontrakt bo'yicha: qidiruv/katalog `POST`, `devices` — push tokeni. Har biri ochilishda bir marta |

## E. Xarita (Map)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| E1 (#23) | Dark themeda xarita ham qorayadi | Xarita doim light stilda | ✅ `mapStyleUrl()` doim yorug' uslub qaytaradi |
| E2 (#24) | Zoom/locate tugmalari menyuga yopishgan; xaritada bottom nav ortiqcha | Xarita ekranida bottom navigation ko'rinmasin | ✅ `ScHideBottomBar()` — xarita, filtr va tafsilot qatlamlarida panel chizilmaydi |
| E3 (#25) | Tepa tugmalar/chiplar kattaroq | Biroz kichraytirilsin | ✅ Chiplar 11.5sp, maydon/tugmalar 38dp |
| E4 (#30) | Tepa chiplar «Section» ro'yxati | **Business type** ro'yxati va shu bo'yicha filtr | ✅ `MapTypeChips` — biznes turlari, `selectType` bilan |
| E5 (#29) | Xarita sekin ochiladi | Kechiktirilgan init / kesh | ✅ MapLibre fayllari ilova ochilishida oldindan yuklanadi (`prepareMapAssets`) |

## F. Filtr (Filter sheet)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| F1 (#26) | Region ro'yxati inline ochilib pastdagilarni surib tashlaydi | Select (bottom sheet) ko'rinishida | ✅ `RegionPickerSheet` — pastdan chiqadigan varaq |
| F2 (#26) | «All of Uzbekistan» noto'g'ri matn | «All regions» (+ ru/uz tarjimalari) | ✅ `allRegions` («Barcha viloyatlar» / «Все регионы») |
| F3 (#27) | Apply tugmasi FAB va bottom nav bilan ustma-ust | Pastki padding qo'shilsin | ✅ Panel yashirinadi + `navigationBarsPadding` |
| F4 (#28, #35) | Back tugmasi juda katta | Standart (kichikroq) o'lcham — barcha ekranlarda bir xil | ✅ `ScBackButton` (38dp) — barcha ekranlarda |
| F5 (#29) | Chiplar single-select | **Multi-select**; ko'rsatiladigan tag maksimum 1 ta, qolgani `{count}+` | ✅ `FilterSelectField` — 1 yorliq + «+N»; biznes turi ko'p tanlovli |
| F6 (#31) | «Section» filtri ortiqcha | Vaqtincha olib tashlansin (business type yetadi) | ✅ Filtrdan olib tashlandi (model/so'rov qatlamida saqlanib turibdi) |

## G. Mening universitetim / Talabalar

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| G1 (#32) | «My university» ichida Food bo'limi mantiqsiz | Kataloglar olib tashlansin | ✅ «Ovqatlanish» bo'limi olib tashlandi (nusxa ko'chirish joylari qoldi) |
| G2 (#33) | Search placeholder «Search universities» | «Search students» | ✅ `searchStudents` |
| G3 (#33) | «Year 1 / Year 2» tarjimasi yo'q; filtr single-select | To'g'ri tarjima + multi-select | ✅ Tarjima `courseText` da bor edi; filtr ko'p tanlovga o'tdi |
| G4 (#34) | «1 students» | «1 student» / «N students», raqam bilan so'z orasida bo'shliq | ✅ `studentsCount` — birlik/ko'plik (ru uchun 1/2-4/5+ qoidasi) |

## H. E'lon tafsiloti (Listing detail)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| H1 (#35) | Back tugmasi katta | Standart o'lcham (F4 bilan bir xil) | ✅ `ScBackButton` |
| H2 (#35) | O'ng tepadagi ikonka hech narsa qilmaydi va tushunarsiz | Favorit (heart) sifatida ishlasin | ✅ `ScFavoriteButton` — yurak + prujinali to'lish |
| H3 (#35) | Narx va «/ dona» bir tekislikda emas | Baseline bo'yicha tekislansin | ✅ `alignByBaseline()` |
| H4 (#35) | «Branches (1)» | 1 ta → «Branch», ko'p → «Branches · N»; max 3 ta + «Show more»; bosilsa xaritada ko'rsatsin | ✅ `branchesLabel`, max 3 + «Yana ko'rsatish», bosilsa xaritada |
| H5 (#35) | Sana `2026-08-07 — 2026-09-06` | `07.08.2026 – 06.09.2026` | ✅ `formatIsoDate` (testlangan) |
| H6 (#35) | Telefon raqami formatlanmagan | `+998 94 122 90 05` | ✅ `formatUzPhoneFull` |
| H7 (#42) | Rasm ko'rinmaydi (backend rasm yuboryapti) | Detalda rasm/karusel chizilsin | ✅ `OfferDetail.images` mapper'da o'qiladi + `DetailImages` karuseli |

## I. E'lonlar ro'yxati (Listings / Offers)

| # | Muammo | Kutilgan natija | Holat |
|---|--------|-----------------|-------|
| I1 (#36) | Favorit ikonkasi arxiv qutisi kabi | Heart ikonka + chiroyli to'lish animatsiyasi | ✅ `ScFavoriteButton` |
| I2 (#37) | Search yopish ikonkasi yopishib qolgan | To'g'ri padding, ko'k border/shadow bilan ajratilsin | ✅ Yopish tugmasi alohida — brend chegara + soya |
| I3 (#38) | Mikrofon bosilganda orqadagi karta bosiladi; voice search ishlamaydi | Voice search olib tashlansin | ✅ Mikrofon olib tashlandi; maydon bosishni yutadi |
| I4 (#39) | Kategoriya kartalari balandligi har xil | Bir xil balandlik; 0 ta bo'lsa qizil «No listings» | ✅ Son qatori doim chiziladi (0 → qizil «E'lon yo'q»), nom `minLines = 2` |
| I5 (#40) | Kartalar har xil (chegirmalisi katta, boshqasi kichik) | Barchasi bir xil — katta karta ko'rinishida | ✅ Yagona `OfferCard` |
| I6 (#41) | Chegirmali kartada e'lon nomi yo'q | Nom ko'rsatilsin | ✅ Karta rasm ostida e'lon nomini ko'rsatadi |

---

## Qo'shimcha eslatmalar

**Backendga hech narsa talab qilinmadi.** Ikkita band shubha uyg'otgan edi, ikkalasi ham
klientda hal qilindi:

- **A6** — `POST /v1/auth/student/password/reset` javobida token YO'Q (spec: faqat
  `{ reset: true }`). Backendni o'zgartirish o'rniga klient parol yangilangach o'sha
  parol bilan `login` chaqiradi: foydalanuvchi uchun natija bir xil (darhol ichkarida),
  server kontrakti esa o'zgarmaydi.
- **H7** — rasmlar backenddan ALLAQACHON kelayotgan edi (`ListingDetailDto.images`),
  ularni klient mapper'i o'qimasdi.

**Mavzu bo'yicha ataylab qilingan chekinish:** xarita endi qorong'i rejimda ham yorug'
uslubda qoladi (E1) — bu bug hisobotining aniq talabi va Yandex/2GIS/Google Maps ham
shunday ishlaydi.

**«Bo'lim» (subcategory) filtri** faqat FILTR EKRANIDAN olib tashlandi (F6). Model,
so'rov va xarita chiplarida u saqlanib turibdi — qaytarish uchun `FilterScreen` ichidagi
izohli blokni tiklash kifoya.

## Yangi umumiy komponentlar

Tuzatishlar davomida takrorlanadigan naqshlar bitta joyga chiqarildi:

| Komponent | Vazifa |
|-----------|--------|
| `ScBackButton` (+ `ScBackButtonSize`) | Orqaga tugmasining yagona o'lchami |
| `ScFavoriteButton` | Sevimlilar — yurak + to'lish animatsiyasi |
| `ScHideBottomBar` / `ScImmersive` | To'liq ekranli qatlamda pastki panelni yashirish |
| `RealtimeSignals` | Modullararo "yangi xabar keldi" signali |
| `HttpClient.refreshSession(...)` | Soketlar uchun to'g'ridan-to'g'ri token yangilash |
| `formatIsoDate(...)` | ISO sana → `kun.oy.yil` |
| `AppPalette.placeholder` | Bo'sh maydon namunasining past kontrastli rangi |
