# Qo'ng'iroq — mobil tomondan javob (`PREREQUISITES.md` uchtasi)

`calls/` handoff'iga javob. Uchala shart ham **bajarildi**; pastda har biri qanday
qilingani va sizga kerak bo'lgan **tasdiq** bor.

Sana: 2026-08-03. Klient: Android (KMP — kod iOS'da ham bir xil, faqat media qatlami
yetishmaydi, §4).

---

## 1. `MessageType.CALL` ga chidamlilik — ⚠️ deploy shartli

**Yangi klientda hal qilindi.** Enum'lar endi Kotlin `enum` sifatida generatsiya
qilinmaydi: OpenAPI normalizatsiyamiz `MessageTypeDto`, `MediaKindDto`, `MediaStatusDto`,
`ConversationTypeDto` va to'rtala qo'ng'iroq enum'ini (`CallStatusDto`, `CallEndReasonDto`,
`CallMediaDto`, `CallDirectionDto`, `CallPartyDto`) **oddiy `string`** ga o'giradi. Domenga
o'girish `parseEnum(raw, default)` orqali ketadi, ya'ni noma'lum qiymat faqat **o'sha bitta
xabarni** «noma'lum tur» qiladi, javobning qolgan qismi normal pars bo'ladi.

Nima uchun sizning taklifingizdagi `coerceInputValues` emas: u maydonda **odatiy qiymat**
bo'lishini talab qiladi, generator esa majburiy maydonga odatiy qiymat qo'ymaydi. `String`
+ `parseEnum` bundan qat'iyroq — enum kelajakda **xohlagancha** kengaysa ham klient
buzilmaydi va bizga har safar yangi versiya chiqarish kerak bo'lmaydi.

Tekshirildi: `type: "CALL"` va `type: "VIDEO_NOTE"` bo'lgan xabarli javob to'liq pars
bo'ladi, `MessageDto.call` o'qiladi va chat lentasida pufakcha chiziladi.

### ⚠️ Sizga kerak bo'lgan tasdiq — **shartli**

**Allaqachon tarqatilgan versiyada `MessageTypeDto` qat'iy Kotlin enum'i.** Ya'ni bugungi
production klient `CALL` qatorini olsa, kotlinx `SerializationException` tashlaydi va
suhbat tarixi, suhbatlar ro'yxati hamda `message:new` handler'i yiqiladi.

**Shuning uchun: `CALL` xabar yozadigan deploy yangi mobil versiya tarqalgandan keyin
chiqarilishi kerak.** Versiya chiqqach alohida xabar qilamiz.

---

## 2. Qayta ulanishda `call:connected` — ✅

`/calls` socket'i jonli qo'ng'iroq davomida qayta ulangan zahoti, boshqa hech narsadan
oldin `call:connected { callId }` yuboriladi (`CallSessionManager.watchReconnect`).

`CALL_NOT_FOUND` ack'i **xato sifatida ko'rsatilmaydi** — u «qo'ng'iroq tugadi» degani va
UI shunda jimgina yopiladi.

## 3. `call:auth { token }` — ✅

Access token yangilanishini socket ko'rmagani uchun (Ktor `Auth` plagini uni fonda
almashtiradi va hech kimga aytmaydi) klient tokenni **kuzatadi**: 30 soniyada bir marta
saqlangan qiymat solishtiriladi va o'zgargan bo'lsa ochiq socket'ga `call:auth` ketadi.

Boshqa talabaning tokeni hech qachon yuborilmaydi — qiymat doim joriy sessiyaniki.

**`CALLS_ENFORCE_TOKEN_EXPIRY` ni `true` ga o'girsangiz bo'ladi** (bizning tomondan
to'siq yo'q). Faqat 1-bandagi tartibga rioya qiling: avval yangi versiya tarqalsin.

---

## 4. Qolgan qismi bo'yicha holat

| Sizning bandingiz | Bizda |
|---|---|
| 17 hodisa, ack shakli, xato kodlari | ✅ to'liq |
| `call:invite` da `conversationId` **yo'q** | ✅ |
| `candidate` da aynan uchta kalit (`usernameFragment` olib tashlangan) | ✅ |
| `sdpMid: null` markeri yuborilmaydi | ✅ |
| `relayOnly` → `iceTransportPolicy = relay`, host/srflx yig'ilmaydi | ✅ |
| `call:ringing` ni chaqirilgan yuboradi | ✅ |
| `call:ended` / `call:declined` / `call:canceled` — uchalasi UI'ni yopadi | ✅ |
| `RATE_LIMITED` da eksponensial pauza | ✅ (250 → 500 → 1000 ms) |
| `GET /v1/calls/ice-servers` 503 alohida qayta ishlanadi | ✅ «Qo'ng'iroq hozircha mavjud emas» |
| `POST /v1/calls/{id}/stats` — **tanlangan juftlikdan** | ✅ `getStats()` → `nominated && succeeded` |
| Javobsiz qo'ng'iroqda stats yuborilmaydi | ✅ (409 ga umuman bormaydi) |
| `iceServers` ro'yxati qotirilmagan (host/URL soni/TTL) | ✅ qanday kelsa shundayligicha uzatiladi |
| Media qatlami (WebRTC) | ✅ **Android**; ❌ iOS — `WebRTC.framework` qo'shilishi kerak |

**iOS**: signalizatsiya, holat mashinasi, taymerlar, REST va UI umumiy kodda va iOS'da ham
ishlaydi; yetishmayotgani faqat media. iOS'da `call:invite` gacha **umuman yetib
bormaydi** (media qatlami ko'tarilmasa offer qurilmaydi), ya'ni chegaralaringiz
sarflanmaydi va soxta qo'ng'iroq yaratilmaydi.

---

## 5. Bizdan bitta so'rov

`CALLS_ENABLED` ni yoqishdan oldin ayting — biz o'sha kuni Android'da uchidan-uchiga
sinaymiz (coturn ustida, ikkita real qurilma bilan). Hozir `503` kutilgan javob bo'lgani
uchun qo'ng'iroq yo'lining media qismini haqiqiy tekshirib bo'lmayapti.
