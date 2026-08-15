# StudentClub — Bug Report tahlili (2026-08-15)

Manba: `~/Desktop/StudentClub Bug.docx` (43 ta skrinshot + izohlar, Sherzod Jo'ra).
Har bir band skrinshot tartibida raqamlangan; `#N` — hujjatdagi `imageN.png`.

Belgilar: **P0** — buzilgan funksional, **P1** — ko'rinadigan UX/UI nuqson, **P2** — jilo.

---

## A. Autentifikatsiya (Auth)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| A1 (#1, #21) | Login ekranida til va mavzu almashtirgichi yo'q | Sign-in ekranining yuqorisiga language switcher + theme switcher; default til — **English** | P1 |
| A2 (#2) | Google tugmasi faqat ikonka | «Continue with Google» matni qo'shilsin (UX) | P1 |
| A3 (#3) | Universitet tanlashda ortiqcha «Select» tugmasi | Ro'yxatdan bosilgan element darhol tanlanadi va sheet yopiladi | P1 |
| A4 (#4) | Placeholder rangi to'ldirilgan input kabi ko'rinadi | Placeholder `onSurfaceVariant`ga yaqin, past kontrastli rang; matn saqlanadi | P1 |
| A5 (#5) | Reset password: raqamli klaviatura tugmani berkitadi; input tashqarisiga bosilganda klaviatura yopilmaydi | `imePadding` + scroll; tashqariga bosilganda focus tozalanadi | P0 |
| A6 (#6) | Parol tiklangach login sahifasiga qaytariladi | Reset javobidagi token bilan **avto-login** | P0 |

## B. Bosh ekran (Home)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| B1 (#7) | «Hello 👋» ortiqcha | Olib tashlansin | P2 |
| B2 (#7) | Xabar/bildirishnoma ikonkalari ism+avatar bilan bir qatorda | Ikonkalar yuqoriroq qatorga chiqarilsin, ui buzilmasin | P1 |
| B3 (#7) | Bottom menu kontentga yopishgan | Menyu ortiga yumshoq soya (shadow/scrim) | P1 |
| B4 (#8) | Stories bloki tepa/past marjini katta | Marjin biroz kamaytirilsin (siqilib qolmasin) | P2 |
| B5 (#9) | Bir joyda «All», boshqasida «More» | Hamma joyda bitta so'z — **All** | P1 |
| B6 (#19) | Yuklanish paytida bo'sh joy | Skeleton (shimmer) ko'rsatilsin | P1 |
| B7 (#20) | Scrollda header cho'zilib qotib qaytadi | Collapse animatsiyasi smooth bo'lsin | P1 |
| B8 (#22) | Food ichidan bitta kartani bosganda umumiy ro'yxat ochiladi | Karta bosilsa **listing detail** ochilsin (barcha kategoriyalar uchun) | P0 |

## C. Chat / Xabarlar / Bildirishnomalar

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| C1 (#10) | Yangi xabar bildirishnomada faqat refreshdan keyin chiqadi; bir kishidan kelgan xabarlar guruhlanmagan | WS orqali realtime qo'shilsin; bitta suhbat = bitta qator | P0 |
| C2 (#11) | Chat bildirishnomasini bosganda chat ochilmaydi | Bosilganda o'sha suhbatga navigatsiya | P0 |
| C3 (#12) | Bitta xabarni tanlab bo'lmaydi (darhol bekor bo'ladi); 2+ ishlaydi | Uzun bosish 1 ta xabarni ham tanlagan holda qoldirsin | P0 |
| C4 (#13, #14, #27) | Ilovaga qayta kirganda «No internet connection» xatosi | Xato manbasi (calls socket / ice-servers) topilib, foydalanuvchiga ko'rsatilmasin | P0 |
| C5 (#15) | Klaviatura ochilganda pastdagi xabarlar ko'rinmaydi | Ro'yxat IME balandligiga surilsin va oxirgi xabarga scroll | P0 |
| C6 (#16) | Bubble kengligi noto'g'ri — matn erta ko'chadi | Oxirgi qator + vaqt bir tekislikda joylashadigan flow layout | P1 |
| C7 (#17) | Stiker paneli va klaviatura bir vaqtda ochiq; emoji yo'q | Panel ochilganda IME yopilsin; **Emoji** tab qo'shilsin | P1 |
| C8 (#43) | Galereya ruxsati Telegramdagidek so'ralmaydi | Tizim ruxsat dialogi (to'liq/qisman/rad) chaqirilsin | P1 |

## D. Tarmoq (Network)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| D1 (#18, #21) | `GET /v1/calls/ice-servers` ilova ochilganda va davriy chaqiriladi | Faqat qo'ng'iroq boshlanganda; natija keshlansin | P0 |
| D2 (#18) | `POST /v1/student-listings/search` ikki marta ketadi | Takroriy so'rov olib tashlansin (debounce / bitta manba) | P1 |
| D3 (#18) | Ilova ochilishida ortiqcha POSTlar (`catalog/groups`, `devices`) | `catalog/groups` keshlansin; `devices` token o'zgarganda | P1 |

## E. Xarita (Map)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| E1 (#23) | Dark themeda xarita ham qorayadi | Xarita doim light stilda | P1 |
| E2 (#24) | Zoom/locate tugmalari menyuga yopishgan; xaritada bottom nav ortiqcha | Xarita ekranida bottom navigation ko'rinmasin | P1 |
| E3 (#25) | Tepa tugmalar/chiplar kattaroq | Biroz kichraytirilsin | P2 |
| E4 (#30) | Tepa chiplar «Section» ro'yxati | **Business type** ro'yxati va shu bo'yicha filtr | P1 |
| E5 (#29) | Xarita sekin ochiladi | Kechiktirilgan init / kesh | P1 |

## F. Filtr (Filter sheet)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| F1 (#26) | Region ro'yxati inline ochilib pastdagilarni surib tashlaydi | Select (bottom sheet) ko'rinishida | P1 |
| F2 (#26) | «All of Uzbekistan» noto'g'ri matn | «All regions» (+ ru/uz tarjimalari) | P1 |
| F3 (#27) | Apply tugmasi FAB va bottom nav bilan ustma-ust | Pastki padding qo'shilsin | P0 |
| F4 (#28, #35) | Back tugmasi juda katta | Standart (kichikroq) o'lcham — barcha ekranlarda bir xil | P1 |
| F5 (#29) | Chiplar single-select | **Multi-select**; ko'rsatiladigan tag maksimum 1 ta, qolgani `{count}+` | P1 |
| F6 (#31) | «Section» filtri ortiqcha | Vaqtincha olib tashlansin (business type yetadi) | P1 |

## G. Mening universitetim / Talabalar

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| G1 (#32) | «My university» ichida Food bo'limi mantiqsiz | Kataloglar olib tashlansin | P1 |
| G2 (#33) | Search placeholder «Search universities» | «Search students» | P1 |
| G3 (#33) | «Year 1 / Year 2» tarjimasi yo'q; filtr single-select | To'g'ri tarjima + multi-select | P1 |
| G4 (#34) | «1 students» | «1 student» / «N students», raqam bilan so'z orasida bo'shliq | P1 |

## H. E'lon tafsiloti (Listing detail)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| H1 (#35) | Back tugmasi katta | Standart o'lcham (F4 bilan bir xil) | P1 |
| H2 (#35) | O'ng tepadagi ikonka hech narsa qilmaydi va tushunarsiz | Favorit (heart) sifatida ishlasin | P1 |
| H3 (#35) | Narx va «/ dona» bir tekislikda emas | Baseline bo'yicha tekislansin | P2 |
| H4 (#35) | «Branches (1)» | 1 ta → «Branch», ko'p → «Branches · N»; max 3 ta + «Show more»; bosilsa xaritada ko'rsatsin | P1 |
| H5 (#35) | Sana `2026-08-07 — 2026-09-06` | `07.08.2026 – 06.09.2026` | P1 |
| H6 (#35) | Telefon raqami formatlanmagan | `+998 94 122 90 05` | P1 |
| H7 (#42) | Rasm ko'rinmaydi (backend rasm yuboryapti) | Detalda rasm/karusel chizilsin | P0 |

## I. E'lonlar ro'yxati (Listings / Offers)

| # | Muammo | Kutilgan natija | Prioritet |
|---|--------|-----------------|-----------|
| I1 (#36) | Favorit ikonkasi arxiv qutisi kabi | Heart ikonka + chiroyli to'lish animatsiyasi | P1 |
| I2 (#37) | Search yopish ikonkasi yopishib qolgan | To'g'ri padding, ko'k border/shadow bilan ajratilsin | P1 |
| I3 (#38) | Mikrofon bosilganda orqadagi karta bosiladi; voice search ishlamaydi | Voice search olib tashlansin | P1 |
| I4 (#39) | Kategoriya kartalari balandligi har xil | Bir xil balandlik; 0 ta bo'lsa qizil «No listings» | P1 |
| I5 (#40) | Kartalar har xil (chegirmalisi katta, boshqasi kichik) | Barchasi bir xil — katta karta ko'rinishida | P1 |
| I6 (#41) | Chegirmali kartada e'lon nomi yo'q | Nom ko'rsatilsin | P1 |

---

## Bajarish tartibi

1. **P0 to'plami** — A5, A6, B8, C1–C5, D1, F3, H7
2. **P1 UI/UX** — Auth, Home, Filter, Map, University, Listing detail, Listings
3. **P2 jilo** — B1, B4, E3, H3

Har bir tuzatish shu faylda `[x]` bilan belgilanadi.
