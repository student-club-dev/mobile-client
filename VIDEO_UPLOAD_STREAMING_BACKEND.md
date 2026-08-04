# Noma'lum hajmli (oqimli) yuklash · Backend spetsifikatsiyasi

Bitta kichik o'zgarish so'raymiz: `POST /v1/media/upload/init` **`totalBytes` siz** ham
sessiya ocha olsin, hajm esa `complete` da e'lon qilinsin.

Sabab qisqa: bugun video **avval to'liq siqiladi, keyin yuklanadi** — ikkisi ketma-ket.
Ular bir vaqtda ketishi mumkin emas, chunki `init` faylning **aniq** hajmini talab qiladi,
siqilgan faylning hajmi esa kodlash tugamaguncha ma'lum emas. Uch daqiqalik lavhada bu
foydalanuvchi uchun bir necha daqiqa "hech narsa bo'lmayotgan" vaqt.

Yakuniy API kontrakti — `dev/api-client-generator/student-club.json` (OpenAPI v1, **yagona
manba**).

---

## 0. Bir qarashda

| | Hozir | Kerak |
|---|---|---|
| `init` da `totalBytes` | **majburiy** | ixtiyoriy |
| Bo'laklarni yuborish | fayl **tayyor bo'lgach** | kodlash davomida, tayyor bo'lagi bilan |
| Yakuniy hajm tekshiruvi | `complete` dan oldin, `init` dagi son bilan | `complete` da kelgan son bilan |
| Kvota | `init` da band qilinadi | `init` da **taxminiy**, `complete` da aniqlanadi |

Klientda yutuq: siqish va yuklash ustma-ust tushadi — 3 daqiqalik videoda yuborish vaqti
taxminan **siqish vaqtiga** teng bo'lib qoladi (hozir: siqish + yuklash).

---

## 1. `POST /v1/media/upload/init` — `totalBytes` ixtiyoriy

```jsonc
{ "kind": "VIDEO", "conversationId": "…", "fileName": "video.mp4" }   // totalBytes YO'Q
```

- `totalBytes` **berilsa** — hammasi bugungidek qoladi, hech narsa o'zgarmaydi.
- **Berilmasa** — sessiya "oqimli" bo'ladi: bo'laklar soni oldindan noma'lum, oxirgi
  bo'lakni `complete` belgilaydi.

Javob o'zgarmaydi (`uploadId`, `chunkSize`, `received`).

**Kvota.** Oqimli sessiyada `init` da aniq hajm yo'q — taklif: kvotadan **shartli** ravishda
bitta videoning yuqori chegarasi (yoki `chunkSize × N`) band qilinsin va `complete` da
haqiqiy hajm bo'yicha to'g'rilansin. Kvota to'lgan bo'lsa `init` baribir darhol rad etsin —
bu `init` ning asosiy foydasi va u yo'qolmasligi kerak.

---

## 2. `POST /v1/media/upload/{uploadId}/complete` — hajmni e'lon qilish

```jsonc
{ "totalBytes": 11534336, "parts": 6 }
```

- Ikkala maydon ham faqat **oqimli** sessiyada majburiy; `totalBytes` bilan ochilgan
  sessiyada tana bo'sh qolaveradi (orqaga mos).
- Server tekshiradi: `0..parts-1` bo'laklarning **hammasi** kelganmi va yig'indi hajm
  e'lon qilinganiga tengmi. Mos kelmasa — `409 UPLOAD_INCOMPLETE` (yoki mavjud kod).
- Qolgan hammasi bugungidek: birlashtirish, EXIF, transkodlash, o'sha `AttachmentDto`.

---

## 3. Nima **o'zgarmaydi**

- `PUT …/part/{index}` — bir xil. Bo'laklar baribir istalgan tartibda va parallel keladi,
  takroriy indeks o'zini qayta yozadi.
- `chunkSize` ni server tanlaydi; oxirgi bo'lakdan boshqa hammasi shu hajmda.
- `GET /v1/media/upload/{id}` va `DELETE` — o'zgarishsiz.
- `totalBytes` bilan ochilgan sessiyalar bugungi yo'ldan ketaveradi.

---

## 4. Klient tomoni (kutmoqda)

Bugun: `MediaUploader.resumableUpload` faylni tayyor holida bo'laklarga bo'ladi
(`dev/core/network/media/MediaUploader.kt`), bo'laklar **parallel** ketadi va uzilishdan
keyin davom etadi. Yetishmayotgani — faylni **yozilayotgan paytda** yuborish.

Oqimli sessiya chiqishi bilan: `Transformer` chiqish fayliga yozayotganda to'lgan har bir
`chunkSize` darhol yuboriladi, kodlash tugagach oxirgi bo'lak va `complete` ketadi.

⚠️ Bitta nozik joy bizda: MP4 muxer fayl oxirida boshidagi `mdat` sarlavhasini
to'g'rilaydi, ya'ni **0-bo'lak oxirida qayta yuboriladi**. Sizning tarafingizda bu allaqachon
xavfsiz (bir xil indeks o'zini qayta yozadi) — shuning uchun qo'shimcha hech narsa
kerak emas, faqat `complete` gacha bo'lakni qayta qabul qilishda davom eting.

---

## 5. Qabul mezonlari

- [ ] `init` `totalBytes` siz ishlaydi va `uploadId` + `chunkSize` qaytaradi.
- [ ] Bo'laklar kelgani sayin qabul qilinadi; `GET /upload/{id}` ularni `received` da beradi.
- [ ] `complete { totalBytes, parts }` faylni yig'adi va o'sha `AttachmentDto` ni qaytaradi.
- [ ] Bo'lak yetishmasa yoki hajm mos kelmasa `complete` xato beradi va sessiya buzilmaydi
      (yetishmagan bo'lakni yuborib qayta urinish mumkin).
- [ ] 0-bo'lak `complete` dan oldin qayta yuborilsa — yangi baytlar qoladi.
- [ ] `totalBytes` bilan ochilgan eski oqim **hech qanday o'zgarishsiz** ishlaydi.
