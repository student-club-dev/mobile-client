# Stiker qidiruvi — Backend spetsifikatsiyasi

Bu hujjat chatga **stiker qidiruvini** qo'shish uchun backendda nima qilinishi kerakligini
tavsiflaydi. `CHAT_MEDIA_AND_CALLS_BACKEND.md` §4 ning davomi.

Yakuniy API kontrakti — `dev/api-client-generator/student-club.json` (OpenAPI v1), **yagona
manba**: Kotlin klienti o'sha yerdan generatsiya qilinadi.

Sana: 2026-07-29.

---

## 0. Nima o'zgardi klient tomonda — va nega bu so'rov qisqardi

`PENDING_ACTIONS.md` §6 da sizdan **2 paket × 24 ta WebP** stiker seed qilish so'ralgan edi.
**Bu endi kerak emas** — klient o'z zaxira katalogini qurib oldi:

| | Ilgari | Endi |
|---|---|---|
| Zaxira katalog | 96 ta tizim emojisi | **1625 ta** Fluent Emoji 3D stiker, 9 ta paket |
| Tasvirlar | yo'q | MIT litsenziyali, CDN'dan |
| Backendga bog'liqlik | — | **yo'q** |

Ya'ni stiker paneli **bugun to'liq ishlaydi**. `GET /v1/stickers/packs` kontrakti joyida
qoladi va server katalog bersa, u zaxiradan ustun turadi — o'zgartirish shart emas, seed
qilish ham shart emas.

**Qolgan bitta bo'shliq:** Fluent Emoji — bu emoji shaklidagi stikerlar, **personaj**
stikerlari emas (Telegramdagi mushuklar, Utya va h.k.). Foydalanuvchi kutadigan narsa esa
aynan shu. Uni faqat qidiruv bera oladi — pastdagi so'rov shu haqda.

---

## 1. `GET /v1/stickers/search` — KLIPY proksisi

KLIPY'da GIF'dan tashqari **alohida Sticker API** bor: millionlab shaffof fonli, animatsiyali
stiker. Bu — **allaqachon ulangan provayder**, o'sha kalit, o'sha `KLIPY_BASE_URL`. Yangi
shartnoma, yangi atribut, yangi hisob kerak emas.

Yuqori oqim: `GET {KLIPY_BASE_URL}/{KEY}/stickers/search` (GIF'dagidek, kalit **yo'lda**).
Trending uchun `…/stickers/trending`.

Endpoint `GET /v1/gifs/search` ning **aynan nusxasi** bo'lsin — parametrlari, sahifalash
uslubi, xato kodlari bir xil. Klient tomonda ikkala panel bitta koddan foydalanadi.

| Parametr | Izoh |
|---|---|
| `q` | Qidiruv so'zi. **Bo'sh bo'lsa** — trending |
| `limit` | 1–50, odatiy 30 |
| `pos` | Keyingi sahifa kursori — **shaffof** |
| `locale` | `uz_UZ` / `ru_RU` / `en_US`, odatiy `uz_UZ` |

```jsonc
{
  "result": {
    "items": [
      {
        "id": "8471021",
        "url": "https://static.klipy.com/…/xY3k.webp",
        "thumbUrl": "https://static.klipy.com/…/xY3k_s.webp",
        "width": 512, "height": 512,
        "isAnimated": true
      }
    ],
    "next": "2",
    "provider": "KLIPY"
  }
}
```

⚠️ **Format — GIF'dan farqli.** GIF'da siz MP4 qaytarasiz (to'g'ri qaror). Stikerda esa
**shaffof fon shart**, MP4 esa alfa kanalni tashlab yuboradi — stiker oq kvadrat ichida
chiqadi. Shuning uchun stikerda **WebP** (yoki shaffofligi saqlangan GIF) qaytarilsin,
MP4 ga o'girilmasin.

`POST /v1/stickers/{id}/share` ham bo'lsin — GIF'dagidek, provayder reytingi uchun.

---

## 2. Stiker xabari — kontrakt

Hozir `SendMessageDto.stickerId` **server katalogidagi** qatorga ishora qiladi. KLIPY stikeri
sizning bazangizda yo'q, ya'ni `stickerId` unga yaramaydi (`422 STICKER_NOT_FOUND`).

**So'rov:** `SendMessageDto` ga `sticker` **obyekti** qo'shilsin — `gif` bilan bir xil
shaklda va bir xil qoidalar bilan:

```jsonc
{
  "type": "STICKER",
  "sticker": {
    "provider": "KLIPY",
    "externalId": "8471021",
    "url": "https://static.klipy.com/…/xY3k.webp",
    "thumbUrl": "https://static.klipy.com/…/xY3k_s.webp",
    "width": 512, "height": 512
  },
  "clientMsgId": "…"
}
```

- `stickerId` **va** `sticker` — ikkalasi ham ixtiyoriy, lekin **bittasi** bo'lishi shart
  (ikkalasi birga → `422`). `stickerId` server katalogi uchun qoladi, ya'ni eski klientlar
  buzilmaydi.
- `MessageDto.sticker` javobda ikkala holatda ham **bir xil shaklda** qaytsin (`gif` da
  qilganingizdek) — klient manbani ajratishi shart emas.
- `body` — `STICKER` da avvalgidek **taqiqlangan**.

### Domen oq ro'yxati — bu yerda ham majburiy

`sticker.url` va `sticker.thumbUrl` GIF'dagi **o'sha** tekshiruvdan o'tsin
(`api-changes.md` §4c dagi ro'yxat): lookalike domenlar, authority'dagi credential,
`http://`, `javascript:`, `data:` — hammasi rad etilsin. Tekshiruv **ikki joyda**: qidiruv
natijasini qaytarishda ham, yuborishda ham.

Sabab o'zgarmagan: klient obyektni sizga qaytarib yuboradi, ya'ni tekshirilmasa bu maydon
ixtiyoriy havola joylash teshigiga aylanadi.

### Xato kodlari

| Kod | HTTP | Qachon |
|---|---|---|
| `STICKER_URL_NOT_ALLOWED` | 422 | `sticker.url` ruxsat etilgan domenlardan emas |
| `STICKER_SOURCE_AMBIGUOUS` | 422 | `stickerId` va `sticker` birga yuborilgan |
| `STICKER_PROVIDER_ERROR` | 502 | KLIPY javob bermadi yoki kalit yaroqsiz |
| `STICKER_PROVIDER_RATE_LIMITED` | 429 | Provayder chegarasi |
| — | 503 | `KLIPY_API_KEY` sozlanmagan |

Mavjud `STICKER_NOT_FOUND` (422) `stickerId` uchun o'z holicha qoladi.

---

## 3. Atribut va production kalit

**«Powered by KLIPY»** stiker panelida ham ko'rsatilishi shart — GIF panelidagi o'sha talab.
Klient buni javobdagi `provider` maydoniga qarab chizadi.

Production kaliti uchun so'raladigan **video** endi ikkala panelni ham qamrasin (GIF **va**
stiker qidiruvi) — bitta yozuv ikkalasiga yetadi, ikkinchi marta ariza berilmasin.

⚠️ Test kaliti soatiga 100 so'rov. Stiker qidiruvi qo'shilgach bu chegara **ikki barobar
tezroq** tugaydi — ya'ni production kaliti endi ilgarigidan ko'ra shoshilinchroq.

---

## 4. Nima qilinmasin

- **Telegram stikerlari.** Texnik jihatdan `getStickerSet` bilan olinadi, lekin ular
  mualliflarning mulki; Telegram shartlari boshqa ilovada tarqatishga ruxsat bermaydi va
  App Store shikoyat bo'yicha ilovani olib tashlaydi. Qaror o'zgarmadi
  (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §4.4).
- **KLIPY fayllarini o'z serveringizga ko'chirish.** Shartlarga zid, GIF'dagidek: havola
  qilinadi, `mediaId` berilmaydi.
- **Ads API.** Panelda taklif qilinadi — talabalar ilovasida o'rinsiz.
- **`.tgs` / Lottie.** Kerak emas: KLIPY stikerlari WebP/GIF, ya'ni mavjud rasm qatlami
  ularni o'zi chizadi.

---

## 5. Ustuvorlik

Bu **bloklovchi emas**. Stiker paneli 1625 ta stiker bilan bugun ishlayapti; qidiruv —
ustiga qo'shiladigan qatlam. Agar v1 ga ulgurmasa, hech narsa yo'qolmaydi.
