# Ovozli va video qo'ng'iroq — Backend spetsifikatsiyasi

Bu hujjat **Student Club** ilovasiga 1:1 **ovozli va video qo'ng'iroq** qo'shish uchun backendda
nima qilinishi kerakligini yakuniy ko'rinishda tavsiflaydi.

`CHAT_MEDIA_AND_CALLS_BACKEND.md` ning **B qismi** (§10–§16) — bu ishning eskizi edi: u qo'ng'iroq
umuman qanday qurilishini (P2P WebRTC, SFU emas) va coturn'ning taxminiy konfiguratsiyasini
belgilagan. **U qismni qayta o'qish shart emas va bu hujjat uni takrorlamaydi** — bu yerda faqat
o'shandan keyin aniqlashgan, o'zgargan yoki umuman aytilmagan narsalar bor.

Aloqador hujjatlar:

| Hujjat | Nima uchun kerak |
|---|---|
| `CHAT_MEDIA_AND_CALLS_BACKEND.md` §10–§16 | Arxitektura qarori (P2P + TURN), sifat sozlamalari (Opus/H.264), coturn konfiguratsiyasining asosi |
| `handoff/03-WEBSOCKET.md` | Mavjud `/chat` protokoli — hodisa nomlanishi, ack shakli, xato konverti. **Yangi hodisalar aynan shu uslubda** |
| `handoff/05-PUSH-SETUP.md` | FCM holati, `POST /v1/devices` kontrakti, push qachon yuborilishi |
| `handoff/PENDING_ACTIONS.md` §7, §8 | FCM tayyor / coturn hali yo'q |

Yakuniy API kontrakti — `dev/api-client-generator/student-club.json` (OpenAPI v1), **yagona manba**:
Kotlin klienti o'sha yerdan generatsiya qilinadi. WebSocket hodisalari Swagger'ga sig'maydi —
ular `handoff/03-WEBSOCKET.md` ga yoziladi.

Sana: 2026-07-31.

---

## 0. Nega aynan hozir

Qo'ng'iroq B qismida "keyinroq" deb qoldirilgan edi, chunki uni **texnik jihatdan qilib
bo'lmasdi**. Ikkita to'siq bor edi, ikkalasi ham 2026-07-31 da olib tashlandi:

| To'siq | Holat |
|---|---|
| nginx WebSocket upgrade **400** qaytarardi → signalizatsiya long-polling ustida ishlardi | ✅ tuzatildi, `ws: 101` (`PENDING_ACTIONS.md` §3) |
| Push provayderi stub edi | ✅ FCM production'da (`PENDING_ACTIONS.md` §7) |

Long-polling ustida qo'ng'iroq **umuman ishlamaydi**: ICE nomzodlari va SDP almashinuvi
sekundlik kechikishga chidamaydi, natijada qo'ng'iroq har safar "ulanmoqda"da qotib qolardi.
Endi bu to'siq yo'q.

**Qolgan yagona infratuzilma bo'shlig'i — coturn** (`PENDING_ACTIONS.md` §8, hozircha ⚪).
U bu ishning eng uzun yetkazib berish muddatiga ega qismi (§6), shuning uchun **birinchi**
boshlanishi kerak.

---

## 1. Hozirgi holat — nima bor, nima yo'q

### Backend

| Imkoniyat | Holat |
|---|---|
| `/chat` Socket.IO namespace, haqiqiy WebSocket ustida | ✅ |
| Redis adapteri (ko'p nusxali deploy) | ✅ |
| `Connection` (bog'lanish) va blok tekshiruvi — chatda | ✅ |
| FCM push (Android + iOS oddiy bildirishnoma) | ✅ |
| coturn (TURN/STUN) | ❌ |
| Qo'ng'iroq signalizatsiyasi | ❌ |
| `Call` jadvali va tarixi | ❌ |
| VoIP push (APNs to'g'ridan-to'g'ri) | ❌ |
| `MessageTypeDto` da `CALL` | ❌ **spec'da yo'q** (tekshirildi: `TEXT, IMAGE, GIF, VIDEO, FILE, VOICE, STICKER, SYSTEM`) |
| `MessageDto.call` | ❌ |
| `RegisterDeviceDto.tokenType` | ❌ |

### Klient (tekshirilgan)

| Narsa | Holat | Fayl |
|---|---|---|
| `MessageType.CALL` domen enum'ida | ✅ bor | `dev/feature/chat/domain/.../model/Chat.kt:19` |
| Klient `CALL` yubora olmasligi | ✅ ataylab bloklangan (`"Bu turdagi xabarni yuborib bo'lmaydi."`) | `.../data/mapper/SendPayload.kt:103` |
| «Qo'ng'iroq» / «Video qo'ng'iroq» tugmalari | ⚠️ bor, lekin `onSoon("… tez orada")` | `.../presentation/PeerProfileSheet.kt:249,252` |
| Socket.IO klienti | ✅ ishlaydi, lekin **bitta namespace** | `dev/core/network/.../ws/SocketIoClient.kt:81` |
| `CallDto`, `MessageDto.call` | ❌ yo'q |
| WebRTC bindings (Android/iOS) | ❌ yo'q |
| CallKit / ConnectionService | ❌ yo'q |
| VoIP token ro'yxatdan o'tishi | ❌ yo'q (`RegisterDeviceDto` da `tokenType` yo'q) |

Ya'ni klientda **xabar modeli tayyor, transport tayyor, UI joyi tayyor** — media qatlami va
kontrakt yo'q.

---

## 2. Namespace qarori — `/calls` **emas**, `/chat` ichida

> ⚠️ **Bu `CHAT_MEDIA_AND_CALLS_BACKEND.md` §12 dagi qarordan farq qiladi.** U yerda alohida
> `/calls` namespace taklif qilingan edi. **Endi bunday qilinmasin** — sabab quyida.
> Agar `/calls` allaqachon yozilgan bo'lsa, faqat namespace nomini o'zgartirish yetadi:
> hodisalar va payloadlar aynan o'sha.

**Talab: barcha `call:*` hodisalari mavjud `/chat` namespace ichida yursin.**

### Nega

1. **Klientning Socket.IO implementatsiyasi bitta namespace'ni qo'llab-quvvatlaydi.**
   `SocketIoClient` konstruktorda bitta `namespace` oladi va boshqa namespace'ga tegishli
   paketni `decode` bosqichida **tashlab yuboradi**
   (`SocketIoProtocol.decode(namespace, packet)` → `null`).
   Ya'ni `/calls` degani — **ikkinchi `SocketIoClient` nusxasi**, ikkinchi Engine.IO
   handshake'i, ikkinchi TCP+TLS ulanishi. Klient tomonida bu kichik ish emas: qayta ulanish
   mantiqi, token yangilash, hayot sikli, backoff — hammasi ikki nusxada bo'ladi va ikkalasi
   bir-biridan mustaqil uziladi.

2. **Qo'ng'iroq boshlanishi sekinlashadi.** Alohida namespace ulanishi faqat kerak bo'lganda
   ochilsa — foydalanuvchi «Qo'ng'iroq» tugmasini bosgach avval handshake kutiladi
   (mobil tarmoqda TLS bilan 300–800 ms), keyin `call:invite` ketadi. Doim ochiq turishi esa
   batareyani ikki barobar yeydi. Ikkala variant ham yomon.

3. **Presence allaqachon `/chat` da.** «Bu odam onlaynmi» degan ma'lumot qo'ng'iroq uchun
   ham kerak (tugmani faollashtirish, «Oflayn» ogohlantirishi). Ikki namespace bo'lsa bu
   ma'lumot ikkinchisiga ko'chirilishi yoki takrorlanishi kerak bo'ladi.

4. **Server tomonida ham arzonroq.** Redis adapteri, room'lar, `TOKEN_EXPIRED` tekshiruvi,
   rate-limit middleware'i — bitta joyda qoladi.

**Nomlar to'qnashmaydi:** hodisalar `call:` prefiksi bilan, mavjudlari `message:` / `typing:` /
`presence:` — `03-WEBSOCKET.md` dagi uslub o'zgarmaydi.

**Kelajakda ajratish kerak bo'lsa** (guruh qo'ng'irog'i, SFU) — hodisa nomlari o'zgarmagani
uchun namespace'ni ko'chirish klient protokolini buzmaydi.

---

## 3. Signalizatsiya

Autentifikatsiya, ack shakli va xato konverti — `/chat` dagidek (`03-WEBSOCKET.md`).
Yangi qoida yo'q.

`callId` — **server** generatsiya qiladi (ULID). Klient generatsiya qilmasin: `callId` ikkala
tomon uchun yagona haqiqat bo'lishi kerak va u push payloadida ham ketadi.

### 3.1 Klient → Server

| Hodisa | Payload | Ack |
|---|---|---|
| `call:invite` | `{ calleeId, media, sdp }` | `{ callId, expiresAt, status: "ok" }` |
| `call:accept` | `{ callId, sdp }` | `{ callId, status: "ok" }` |
| `call:decline` | `{ callId }` | `{ callId, status: "ok" }` |
| `call:cancel` | `{ callId }` | `{ callId, status: "ok" }` |
| `call:end` | `{ callId }` | `{ callId, durationMs, status: "ok" }` |
| `call:ice` | `{ callId, candidate }` | — (ataylab yo'q, §3.5) |
| `call:renegotiate` | `{ callId, sdp }` | `{ callId, status: "ok" }` |
| `call:media-state` | `{ callId, audioEnabled, videoEnabled }` | — (ataylab yo'q) |
| `call:resume` | `{ callId }` | `{ callId, status, media, peerId, answeredAt, peerMediaState, status: "ok" }` |

Xato ack'i — `03-WEBSOCKET.md` dagi aynan o'sha shakl:

```jsonc
{ "callId": "cal_…", "status": "error", "error": { "code": "CALL_BUSY", "message": "Abonent band" } }
```

### 3.2 Server → Klient

| Hodisa | Kimga | Payload |
|---|---|---|
| `call:incoming` | chaqirilganning **barcha** qurilmalariga | `{ callId, conversationId, caller, media, sdp, expiresAt }` |
| `call:ringing` | chaquvchiga | `{ callId }` — kamida bitta qurilma hodisani oldi |
| `call:accepted` | chaquvchiga | `{ callId, sdp }` |
| `call:declined` | chaquvchiga | `{ callId, reason }` |
| `call:canceled` | chaqirilganning barcha qurilmalariga | `{ callId }` |
| `call:taken` | chaqirilganning **qolgan** qurilmalariga | `{ callId }` |
| `call:ice` | ikkinchi tomonga | `{ callId, candidate }` — **o'zgarishsiz** |
| `call:renegotiate` | ikkinchi tomonga | `{ callId, sdp }` — **o'zgarishsiz** |
| `call:media-state` | ikkinchi tomonga | `{ callId, audioEnabled, videoEnabled }` |
| `call:ended` | **ikkala** tomonning barcha qurilmalariga | `{ callId, reason, durationMs, endedBy, messageId }` |

### 3.3 Payloadlar

**`call:invite` (klient → server)**

```jsonc
{
  "calleeId": "std_01H…",          // KIMGA. conversationId YOZILMAYDI — §4.2
  "media": "AUDIO",                // AUDIO | VIDEO
  "sdp": "v=0\r\no=- 4611…"        // offer, to'liq SDP matni
}
```

Ack:

```jsonc
{ "callId": "cal_01J…", "expiresAt": "2026-07-31T09:15:07.000Z", "status": "ok" }
```

**`call:incoming` (server → chaqirilgan)**

```jsonc
{
  "callId": "cal_01J…",
  "conversationId": "cnv_01H…",     // server topgan yoki yaratgan suhbat (§4.2)
  "caller": {
    "id": "std_01H…",
    "firstName": "Aziz",
    "lastName": "Karimov",
    "avatarUrl": "https://cdn…/a.webp"
  },
  "media": "VIDEO",
  "sdp": "v=0\r\no=- 4611…",
  "expiresAt": "2026-07-31T09:15:07.000Z"
}
```

> ⚠️ `caller` ichiga **telefon raqami, email yoki `phoneVisibility` bilan yopilgan hech
> qanday maydon solinmasin.** Bu obyekt qulflangan ekranda, CallKit interfeysida ko'rinadi —
> ya'ni telefonni qo'lga olgan har kim o'qiy oladi. Ism, familiya, avatar — hammasi shu.

**`call:ice` (ikki yo'nalishda ham bir xil)**

```jsonc
{
  "callId": "cal_01J…",
  "candidate": {
    "candidate": "candidate:842163049 1 udp 1677729535 …",
    "sdpMid": "0",
    "sdpMLineIndex": 0,
    "usernameFragment": "vY3k"
  }
}
```

**`call:ended` (server → ikkalasiga)**

```jsonc
{
  "callId": "cal_01J…",
  "reason": "HANGUP",
  "durationMs": 184000,             // javob berilmagan bo'lsa 0
  "endedBy": "std_01H…",            // null — server tugatgan bo'lsa (TIMEOUT, MAX_DURATION)
  "messageId": "msg_01J…"           // §8 dagi CALL xabari — klient uni darhol chizadi
}
```

`reason` to'plami:

| Qiymat | Qachon |
|---|---|
| `HANGUP` | Tomonlardan biri `call:end` yubordi (javob berilgandan keyin) |
| `DECLINED` | Chaqirilgan rad etdi |
| `BUSY` | Chaqirilganning boshqa faol qo'ng'irog'i bor |
| `CANCELED` | Chaquvchi javob kutmay tashladi |
| `TIMEOUT` | 45 s jiringladi, javob bo'lmadi |
| `UNREACHABLE` | Chaqirilganning na ochiq soketi, na push tokeni bor (§5.4) |
| `FAILED` | Ulanish o'rnatilmadi yoki uzilib qoldi (§5.5) |
| `BLOCKED` | Qo'ng'iroq davomida bloklandi (§4.3) |
| `MAX_DURATION` | 4 soatlik chegara |

### 3.4 Nega offer `invite` bilan birga ketadi

O'zgarmadi (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §12.2): SDP offer taklif bilan birga yuboriladi,
chaqirilgan «Javob berish» ni bosgan zahoti answer tayyorlanadi. Bu ulanishni ~1 soniyaga
tezlashtiradi. ICE nomzodlari **trickle** rejimida oqim bo'lib ketaveradi.

### 3.5 Nega `call:ice` va `call:media-state` ack qaytarmaydi

Bir qo'ng'iroqda 10–40 ta ICE nomzodi ketadi, mikrofon tugmasi esa suhbat davomida o'nlab marta
bosiladi. Har biriga ack — bu ikki barobar paket va hech kimga kerak bo'lmagan tasdiq: nomzod
yo'qolsa WebRTC'ning o'zi boshqa nomzod bilan ulanadi, mikrofon holati esa keyingi hodisada
baribir yangilanadi.

Bu `typing:start` / `typing:stop` bilan bir xil qoida (`03-WEBSOCKET.md`) — uslub o'zgarmaydi.

### 3.6 Server — **faqat uzatuvchi**

`sdp` va `candidate` maydonlariga **bir baytiga ham tegilmasin**: o'qilmasin, tahrirlanmasin,
qayta formatlanmasin, JSON'dan JSON'ga ko'chirishda `\r\n` lar saqlansin.

Sababi ikkita:

1. SDP'da klient ovoz sifatini belgilaydigan sozlamalar bor (`useinbandfec`, `usedtx`,
   `maxaveragebitrate` — `CHAT_MEDIA_AND_CALLS_BACKEND.md` §15.1). Ularning bittasi
   yo'qolsa qo'ng'iroq ishlaydi, lekin **yomon ishlaydi** — va buni topish deyarli imkonsiz,
   chunki hech qayerda xato chiqmaydi.
2. `\r\n` ni `\n` ga aylantirish SDP'ni **yaroqsiz** qiladi va qo'ng'iroq umuman ulanmaydi.
   Bu klassik xato: JSON kutubxonasi emas, oraliqdagi "tozalash" kodi qiladi.

Serverning butun ishi — `callId` bo'yicha ikkinchi tomonni topib, paketni **o'sha ko'yi**
uzatish.

### 3.7 ICE nomzodlarini yo'naltirish — kimga, qachon

Bu qismda ikkita nozik joy bor.

**a) Jiringlash paytida chaquvchining nomzodlari.** Chaqirilganning 3 ta qurilmasi bo'lishi
mumkin va qaysi biri javob berishi noma'lum. Shuning uchun chaquvchining `call:ice` nomzodlari
**jiringlayotgan barcha qurilmalarga** yuboriladi — har biri o'zida buferlaydi. Javobdan keyin
faqat g'olib qurilmaga.

**b) ⚠️ Chaqirilganning nomzodlari javob berishdan OLDIN yuborilmasin.**

> Server `status = RINGING` bo'lganda chaqirilgan tomondan kelgan `call:ice` ni **tashlab
> yuborsin** (jimgina, xatosiz). Aks holda foydalanuvchi qo'ng'iroqni **rad etsa ham**
> chaquvchi uning IP manzilini (srflx nomzod orqali — real mobil/uy IP'si) bilib oladi.
> Ya'ni istalgan odam "qo'ng'iroq qilib, darhol tashlash" orqali bog'langan talabaning
> joylashuvini taxminan aniqlay oladi. Bu maxfiylik nuqsoni va u signalizatsiya darajasida
> yopilishi kerak — klientga ishonib bo'lmaydi.

Klient tomonda ham shu qoida bor (nomzodlar `accept` gacha buferlanadi), lekin ikki tomonlama
himoya kerak: eski yoki o'zgartirilgan klient serverning tekshiruvidan o'tolmasin.

### 3.8 Ko'p qurilma va `call:taken`

`call:incoming` chaqirilganning **barcha** ulangan socket'lariga boradi.
Birinchi `call:accept` yutadi; qolgan qurilmalarga `call:taken` ketadi va ularda jiringlash
darhol to'xtaydi (iOS'da — CallKit sessiyasi yopiladi, aks holda telefon jiringlab turaveradi).

Ikkinchi va undan keyingi `call:accept` → `CALL_INVALID_STATE`.

### 3.9 Glare — ikkalasi bir vaqtda qo'ng'iroq qildi

`callId` i **leksikografik kichik** bo'lgani davom etadi, ikkinchisi `BUSY` bilan yopiladi.

ULID vaqt bo'yicha tartiblanadigan bo'lgani uchun bu amalda **avval boshlangani yutadi**
degani — ya'ni qoida tasodifiy emas, adolatli. Ikkala klient ham bir xil xulosaga keladi,
qo'shimcha kelishuv kerak emas.

---

## 4. Ruxsat — qo'ng'iroq chat bilan bir xil eshikdan o'tadi

### 4.1 Tekshiruvlar (`call:invite` da, shu tartibda)

| # | Tekshiruv | Xato |
|---|---|---|
| 1 | Token tirikmi | `TOKEN_EXPIRED` |
| 2 | `calleeId != callerId` | `CALL_SELF` |
| 3 | `calleeId` mavjud va talaba hisobi | `CALL_NOT_FOUND` |
| 4 | Ikkalasi **bog'langan** (`Connection`, holati qabul qilingan) | `CALL_NOT_CONNECTED` |
| 5 | **Hech biri ikkinchisini bloklamagan** (ikki yo'nalishda ham) | `CALL_BLOCKED` |
| 6 | Chaquvchining boshqa faol qo'ng'irog'i yo'q | `CALL_ALREADY_ACTIVE` |
| 7 | Chaqirilgan band emas | → `call:declined { reason: "BUSY" }` |
| 8 | Rate-limit (§10) | `CALL_RATE_LIMITED` |

**Bu chatdagi aynan o'sha eshik.** Yangi ruxsat qatlami yozilmasin — mavjud `Connection` va
blok tekshiruvi qayta ishlatilsin. Sababi oddiy: ikkita alohida qatlam vaqt o'tib bir-biridan
ajralib ketadi va **qo'ng'iroq chatdan ko'ra ochiqroq** bo'lib qoladi. Bloklangan odam xabar
yubora olmay, lekin tunning yarmida qo'ng'iroq qila olishi — eng yomon variant.

### 4.2 `conversationId` — klientdan olinmaydi, server topadi

`call:invite` da `conversationId` **yo'q**, faqat `calleeId` bor.

Sababi: qo'ng'iroq **profil ekranidan** ham qilinadi (`PeerProfileSheet`), ya'ni bu ikkalasi
hech qachon yozishmagan bo'lishi mumkin — suhbat hali yo'q. Server birinchi xabardagi kabi
**topadi yoki yaratadi** (find-or-create), so'ng `conversationId` ni `call:incoming` va
`Call` yozuviga qo'yadi.

Bu bir vaqtning o'zida xavfsizlik ham: klient begona `conversationId` yubora olmaydi.

⚠️ Suhbat **`call:invite` da emas, birinchi `CALL` xabari yozilayotganda** yaratilsin. Aks
holda rad etilgan qo'ng'iroqdan keyin ham chat ro'yxatida bo'sh suhbat paydo bo'ladi.

### 4.3 Qo'ng'iroq davomida bloklash

Foydalanuvchi suhbat davomida ikkinchi tomonni bloklasa — qo'ng'iroq **darhol** tugatilsin:
`call:ended { reason: "BLOCKED" }`. Bloklash "endi men bilan gaplashma" degani; keyingi
qo'ng'iroqni to'sib, joriysini davom ettirish mantiqsiz.

---

## 5. Holatlar, chegaralar va uzilishlar

### 5.1 Holat mashinasi

```
                  call:invite
                       │
                       ▼
   ┌──────────── RINGING ────────────┐
   │  accept        decline/cancel   │  45 s
   ▼                    ▼            ▼
 ACTIVE ─────────►   ENDED       ENDED(TIMEOUT)
   │  end / uzilish /  ▲
   │  4 soat ──────────┘
```

> ⚠️ **`CHAT_MEDIA_AND_CALLS_BACKEND.md` §14.1 dagi `status` enum'i o'zgaradi.**
> U yerda `status` ga `MISSED | DECLINED | CANCELED | FAILED` ham kirgan edi — bu `endReason`
> bilan takrorlanadi va **mumkin bo'lmagan kombinatsiyalarga** yo'l ochadi
> (`status = MISSED`, `endReason = HANGUP`).
>
> **Talab:** `status` — faqat hayot sikli: `RINGING | ACTIVE | ENDED`.
> Natija esa `endReason` da (§3.3). Bitta haqiqat — bitta maydonda.

### 5.2 Vaqt chegaralari

| Nima | Qiymat | Nega aynan shuncha |
|---|---|---|
| Jiringlash (javobsiz) | **45 s** | Telefon operatorlarining odatiy qiymati; 30 s qisqa (odam telefonini olishga ulgurmaydi), 60 s uzun (iOS'da CallKit baribir ~45 s dan keyin so'nadi) |
| `accept` dan keyin media ulanishi | **30 s** | Bundan uzun kutish foydalanuvchi uchun "buzuq" degani; TURN bilan ham 30 s dan ortiq ketmaydi |
| Maksimal davomiylik | **4 soat** | Unutib qo'yilgan qo'ng'iroq TURN trafigini kunlab yeb turmasin |
| Socket uzilgach kutish | **20 s** | §5.5 |
| TURN kredensiali TTL | **1 soat** | §6.3 |

### 5.3 Bir vaqtda bitta qo'ng'iroq

Chegara **foydalanuvchi bo'yicha**, qurilma bo'yicha emas. Ya'ni telefonda gaplashib turgan
odam planshetidan ikkinchi qo'ng'iroq qila olmaydi (`CALL_ALREADY_ACTIVE`), unga kelgan
qo'ng'iroq esa `BUSY` oladi.

**Ikkala tomon ham band bo'lsa:** hech qanday maxsus holat yo'q — chaquvchi 6-tekshiruvda
(`CALL_ALREADY_ACTIVE`) to'xtaydi, ya'ni `invite` umuman yaratilmaydi va chaqirilganning
bandligi tekshirilmaydi ham.

⚠️ Kutish/almashish (call waiting) — **v1 da yo'q** (§12).

### 5.4 Chaqirilgan umuman yetib bo'lmaydigan bo'lsa

Chaqirilganning **na ochiq socket'i, na birorta push tokeni** bo'lsa — 45 soniya jiringlashning
ma'nosi yo'q. Server darhol `call:declined { reason: "UNREACHABLE" }` qaytarsin va
javobsiz qo'ng'iroq yozuvini (§8) **baribir yozsin**.

Sababi: chaquvchi 45 soniya bo'sh joyga tinglab o'tirmaydi, chaqirilgan esa ilovani ochganda
«javobsiz qo'ng'iroq» ni ko'radi. Ikkala tomon ham to'g'ri ma'lumot oladi.

### 5.5 Tarmoq uzilsa — qo'ng'iroq o'lmasligi kerak

Bu qismda eng ko'p xato qilinadi. **Media WebRTC'da P2P ketadi, ya'ni socket'dan mustaqil.**
Signalizatsiya socket'i uzilgani qo'ng'iroq uzilgani **emas** — odam liftga kirdi, tunneldan
o'tdi, Wi-Fi dan mobilga o'tdi. Ovoz esa davom etayotgan bo'lishi mumkin.

| Vaziyat | Server nima qiladi |
|---|---|
| Socket uzildi, 20 s ichida qaytdi | **Hech narsa.** Qo'ng'iroq davom etadi |
| Socket uzildi, 20 s dan ko'p | `call:ended { reason: "FAILED" }` ikkala tomonga |
| Klient `call:resume` yubordi | Joriy holatni ack'da qaytaradi |
| Klient tarmoq almashdi → `call:renegotiate` (ICE restart) | Uzatadi, boshqa hech narsa |

**`call:resume` — yangi hodisa, eski hujjatda yo'q edi.** Zarurati: klient qayta ulanganda
qo'ng'iroq hali tirikmi, ikkinchi tomon mikrofonini o'chirganmi, qachon javob berilgan
edi — bularning hech birini bilmaydi. Bularsiz UI "0:00" dan sanay boshlaydi yoki allaqachon
tugagan qo'ng'iroqni ko'rsatib turadi.

```jsonc
// call:resume ack
{
  "callId": "cal_01J…",
  "state": "ACTIVE",
  "media": "VIDEO",
  "peerId": "std_01H…",
  "answeredAt": "2026-07-31T09:14:31.000Z",
  "peerMediaState": { "audioEnabled": true, "videoEnabled": false },
  "status": "ok"
}
```

Qo'ng'iroq allaqachon tugagan bo'lsa → `CALL_NOT_FOUND` (klient ekranni yopadi).

### 5.6 `GET /v1/calls/active` — sovuq startdan keyin

Ilova **butunlay yopiq** bo'lganda VoIP push kelsa, iOS ilovani uyg'otadi va u **darhol**
CallKit'ga qo'ng'iroqni ko'rsatishi shart (§7.4) — WebSocket ulanishini kutishga vaqt yo'q.

Ilova jiringlashni ko'rsatgach, socket ulangunicha qo'ng'iroq allaqachon tugagan bo'lishi
mumkin. Shuning uchun tez REST tekshiruvi kerak:

```
GET /v1/calls/active
→ { "result": { "call": { "callId", "state", "media", "peer": {…}, "expiresAt" } } }
→ result.call = null — faol qo'ng'iroq yo'q
```

Klient `null` olsa CallKit sessiyasini darhol yopadi. Busiz telefon **bo'sh joyga jiringlab
turadi** va bu foydalanuvchi eng ko'p shikoyat qiladigan nuqson.

---

## 6. coturn — TURN / STUN

Asosiy konfiguratsiya `CHAT_MEDIA_AND_CALLS_BACKEND.md` §11.1 da. Bu yerda — **o'sha
konfiguratsiya bilan ham qo'ng'iroqni ishlamay qoldiradigan** narsalar.

### 6.1 ⚠️ 443/TLS — bu "yaxshi bo'lardi" emas, **majburiy**

Bizning foydalanuvchilarimiz — **talabalar**, ya'ni kunning yarmini universitet Wi-Fi sida
o'tkazadi. Bunday tarmoqlarda odatda:

- UDP butunlay yopiq (yoki faqat DNS uchun ochiq);
- 3478 (TURN'ning standart porti) yopiq — u "biznesga aloqasiz" ro'yxatda;
- 443/TCP esa **doim** ochiq, chunki usiz internet umuman ishlamaydi;
- ko'pincha oldinda "transparent proxy" turadi, ya'ni 443 dan chiqayotgan trafik **TLS'ga
  o'xshashi** kerak.

`turns:` (TLS ustidagi TURN) 443-portda aynan shu talabni qondiradi: tashqaridan bu oddiy
HTTPS ulanishiga o'xshaydi.

**Busiz nima bo'ladi:** qo'ng'iroqlarning bir qismi — aynan universitetdan qilinganlari —
"Ulanmoqda…" da qotib, 30 soniyadan keyin `FAILED` bilan tugaydi. Uy Wi-Fi sida esa hammasi
ideal ishlaydi. Ya'ni nuqson **testda ko'rinmaydi**, faqat foydalanuvchida ko'rinadi va
sababini topish haftalar oladi.

### 6.2 ⚠️ 443 porti nginx bilan to'qnashadi

`api.studentclub.uz` da nginx allaqachon 443 ni band qilgan. Bitta IP'da ikkalasi turolmaydi.

**Talab: coturn alohida IP manzilda bo'lsin** — alohida server yoki o'sha serverga qo'shilgan
ikkinchi IP. DNS: `turn.studentclub.uz` → **o'sha IP**.

⛔ **Cloudflare yoki boshqa proksi orqasiga qo'yilmasin.** TURN — bu HTTP emas; CDN uni
uzata olmaydi va relay nomzodlari umuman ishlamaydi.

### 6.3 `GET /v1/calls/ice-servers` — vaqtinchalik kredensiallar

Doimiy TURN paroli ilovaga solinmasin: APK dekompilyatsiya qilinadi va TURN serveringiz
begonalarning bepul proksisiga aylanadi (bu real va tez-tez uchraydigan hodisa).

coturn'ning `use-auth-secret` mexanizmi:

```
username   = "<unixTimestamp + ttl>:<studentId>"
credential = base64( HMAC_SHA1( static-auth-secret, username ) )
```

Server hech qanday parol saqlamaydi — coturn o'sha sirdan HMAC'ni qayta hisoblab tekshiradi.

```jsonc
// GET /v1/calls/ice-servers  →  200
{
  "result": {
    "iceServers": [
      { "urls": ["stun:turn.studentclub.uz:3478"] },
      {
        "urls": [
          "turn:turn.studentclub.uz:3478?transport=udp",
          "turn:turn.studentclub.uz:3478?transport=tcp",
          "turns:turn.studentclub.uz:443?transport=tcp"
        ],
        "username": "1785315600:std_01H…",
        "credential": "b0Xk9…"
      }
    ],
    "ttlSeconds": 3600
  }
}
```

**Tartib muhim:** `stun` → `turn/udp` → `turn/tcp` → `turns/443`. WebRTC nomzodlarni shu
tartibda yig'adi va eng arzon ishlaydiganini tanlaydi. `turns:443` birinchi tursa, ochiq
tarmoqdagi barcha qo'ng'iroqlar keraksiz ravishda relay orqali ketadi — bu ham sifat, ham
trafik xarajati.

**TTL va uzoq qo'ng'iroq.** Kredensial faqat **ICE yig'ish paytida** kerak; 1 soatdan uzun
davom etgan qo'ng'iroqning allaqachon ochilgan relay sessiyasi kredensial eskirgani uchun
uzilmaydi. Lekin **ICE restart** (tarmoq almashdi) yangi kredensial talab qiladi, shuning
uchun klient TTL tugashiga 5 daqiqa qolganda endpointni qayta chaqiradi.

### 6.4 Jimgina ishdan chiqadigan uch narsa

| Nuqson | Nima bo'ladi | Chora |
|---|---|---|
| `external-ip` noto'g'ri (server NAT orqasida) | coturn relay nomzodini **ichki IP** bilan e'lon qiladi. Log toza, xato yo'q, qo'ng'iroq esa hech qachon ulanmaydi | `external-ip=<PUBLIC>/<PRIVATE>` shaklida yozilsin va `turnutils_uclient` bilan tekshirilsin |
| Let's Encrypt sertifikati yangilandi, coturn qayta yuklanmadi | 90 kundan keyin **faqat `turns:443`** ishdan chiqadi — ya'ni faqat universitetdagi foydalanuvchilar. Qolganlar uchun hammasi joyida | `certbot` ga `--deploy-hook "systemctl reload coturn"` qo'shilsin |
| UDP relay portlari firewall'da yopiq | Relay olinadi, lekin ovoz ketmaydi — "ulandi, jim" | `49152–65535/udp` ochilsin (yoki `min-port`/`max-port` bilan toraytirilib, o'sha oraliq ochilsin) |

### 6.5 Sig'im — oldindan hisoblab qo'yilsin

Relay qilingan qo'ng'iroqda trafik **ikki marta** o'tadi (kiradi va chiqadi):

| Qo'ng'iroq turi | Bitta relay qilingan qo'ng'iroq |
|---|---|
| Audio (Opus ~40 kbps) | ≈ **0.2 Mbps** |
| Video (720p, ~2 Mbps) | ≈ **8 Mbps** |

Amalda qo'ng'iroqlarning **20–30%** i relay talab qiladi (qolganlari P2P ketadi). Ya'ni
bir vaqtda 100 ta video qo'ng'iroq bo'lsa: 100 × 0.25 × 8 ≈ **200 Mbps**.

Shuning uchun coturn serveri **hisoblangan trafikli** tarifda bo'lmasin, va
`user-quota` / `total-quota` chegaralari qoldirilsin — bitta buzuq klient butun kanalni
yeb qo'ymasin.

`ulimit -n` kamida **65535** ga ko'tarilsin: har relay sessiyasi bir nechta fayl deskriptorini
oladi va standart 1024 chegarasiga bir necha o'nlab qo'ng'iroqdayoq urilinadi.

---

## 7. Push — yopiq ilovada jiringlash

Bu qo'ng'iroqning **eng ko'p e'tibordan chetda qoladigan va eng muhim** qismi. Ilova yopiq
bo'lsa WebSocket ham yopiq, ya'ni `call:incoming` yetib bormaydi. Telefon jiringlamasa esa
qo'ng'iroq imkoniyati **umuman yo'q** demakdir.

### 7.1 ⚠️ FCM VoIP push yubora olmaydi — to'g'ridan-to'g'ri APNs kerak

`handoff/05-PUSH-SETUP.md` da yozilganidek, oddiy bildirishnomalar uchun FCM iOS'ga ham
yetkazadi va **bitta integratsiya yetadi**. Qo'ng'iroqda bu ishlamaydi:

- VoIP push `apns-push-type: voip` sarlavhasi bilan ketishi shart;
- FCM bu sarlavhani qo'ymaydi va qo'yish imkonini ham bermaydi;
- `apns-topic` ham boshqacha: `<bundleId>` emas, **`<bundleId>.voip`**.

**Talab: backendda APNs bilan to'g'ridan-to'g'ri gaplashadigan alohida adapter bo'lsin**
(HTTP/2, `api.push.apple.com`). Bu FCM adapterini almashtirmaydi — **yonida** turadi:

| Nima | Kanal |
|---|---|
| Yangi xabar, javobsiz qo'ng'iroq | FCM (hozirgidek) |
| Kiruvchi qo'ng'iroq (iOS) | **APNs to'g'ridan-to'g'ri**, `apns-push-type: voip` |
| Kiruvchi qo'ng'iroq (Android) | FCM, `priority: high`, faqat `data` |

### 7.2 Autentifikatsiya — ikkita yo'l

| Yo'l | Nima kerak | Izoh |
|---|---|---|
| **Token-based (`.p8`)** — **tavsiya** | Firebase'ga yuklangan **o'sha** APNs Auth Key, `apns-topic` esa `<bundleId>.voip` | Bitta kalit ikkala kanalga; muddati tugamaydi; JWT server tomonda 1 soatda bir marta yangilanadi |
| Sertifikat (`.p12`) | Apple Developer → **VoIP Services Certificate** (alohida sertifikat) | Klassik yo'l; **yiliga bir marta muddati tugaydi** va tugaganda qo'ng'iroq jimgina to'xtaydi |

> ⚠️ `.p8` faylini **bir marta** yuklab olish mumkin (`05-PUSH-SETUP.md` §2.2). Agar u
> Firebase'ga yuklanib, fayl o'chirilgan bo'lsa — Firebase uni qaytarib bermaydi va Apple
> Developer'da **yangi kalit** yasashga to'g'ri keladi (eskisini bekor qilib, Firebase'ga
> yangisini qayta yuklab). Shuning uchun: **hozir tekshiring, fayl saqlanganmi.**

Sertifikat yo'li tanlansa — muddati tugashiga **kalendarga eslatma** qo'yilsin. Bu shunday
nuqsonki, u tugagan kuni hech qayerda xato ko'rinmaydi: iOS foydalanuvchilariga qo'ng'iroq
kelmay qo'yadi, Android esa ishlab turaveradi.

### 7.3 `RegisterDeviceDto` kengayadi

Hozir (tekshirildi): `{ token, platform }`, `platform ∈ IOS | ANDROID | WEB`.

```jsonc
{ "token": "…", "platform": "IOS", "tokenType": "APNS_VOIP" }
```

| Maydon | Qiymatlar | Qoida |
|---|---|---|
| `tokenType` | `FCM \| APNS \| APNS_VOIP` | **Ixtiyoriy.** Berilmasa: `ANDROID → FCM`, `IOS → FCM`, `WEB → FCM` |

Odatiy qiymat **eski klientlarni buzmaslik uchun** kerak: bugungi ilova `tokenType` yubormaydi
va yubormasligi ham kerak.

⚠️ Bitta iPhone'da **ikkita token** ro'yxatdan o'tadi — oddiy FCM tokeni (xabarlar uchun) va
VoIP tokeni (qo'ng'iroq uchun). Ular **alohida qatorlar** bo'lsin: bir xil qurilma, bir xil
foydalanuvchi, turli `tokenType`. Bittasini ikkinchisi bilan almashtirib yubormang — bu
xabarlarni yoki qo'ng'iroqlarni jimgina o'chiradi.

### 7.4 iOS — VoIP push

Sarlavhalar:

| Sarlavha | Qiymat | Nega |
|---|---|---|
| `apns-push-type` | `voip` | Busiz iOS 13+ push'ni **rad etadi** |
| `apns-topic` | `<bundleId>.voip` | Oddiy `<bundleId>` bilan yuborilsa yetib bormaydi |
| `apns-priority` | `10` | Darhol |
| `apns-expiration` | `0` | §7.7 |

Payload:

```jsonc
{
  "callId": "cal_01J…",
  "type": "call",
  "conversationId": "cnv_01H…",
  "callerId": "std_01H…",
  "callerName": "Aziz Karimov",
  "callerAvatarUrl": "https://cdn…/a.webp",
  "media": "VIDEO",
  "expiresAt": "2026-07-31T09:15:07.000Z"
}
```

> ⚠️ **iOS ning qattiq qoidasi:** VoIP push kelgan zahoti ilova **majburan**
> `CXProvider.reportNewIncomingCall` ni chaqirishi shart. Chaqirmasa iOS ilovani o'ldiradi va
> bir necha marta takrorlansa qurilmaga **keyingi VoIP push'larni umuman yubormay qo'yadi**.
>
> Shundan kelib chiqadigan backend qoidasi: **VoIP kanaliga qo'ng'iroqdan boshqa hech narsa
> yuborilmasin.** Xabar, e'lon, marketing — hech biri. Bitta "sinov uchun" yuborilgan VoIP
> push foydalanuvchining qo'ng'iroqlarini butunlay o'chirib qo'yishi mumkin.

`expiresAt` payload ichida bo'lishi shart: push kechikib yetib kelsa, klient uni ochmasdan
tashlab yuboradi (§7.7).

### 7.5 Android — yuqori muhimlikdagi data-push

```jsonc
{
  "message": {
    "token": "…",
    "android": {
      "priority": "high",
      "ttl": "45s",
      "collapse_key": "call"
    },
    "data": {
      "type": "call",
      "callId": "cal_01J…",
      "conversationId": "cnv_01H…",
      "callerId": "std_01H…",
      "callerName": "Aziz Karimov",
      "callerAvatarUrl": "https://cdn…/a.webp",
      "media": "VIDEO",
      "expiresAt": "2026-07-31T09:15:07.000Z"
    }
  }
}
```

| Talab | Nega |
|---|---|
| **`notification` bloki bo'lmasin** | `notification` bo'lsa Android tizim o'zi bildirishnoma chizadi va ilova **uyg'onmaydi** — ya'ni jiringlash ekrani ko'rsatilmaydi. Faqat `data` bo'lsa `FirebaseMessagingService.onMessageReceived` Doze rejimida ham chaqiriladi |
| `priority: "high"` | Doze / App Standby ni buzib o'tishning yagona yo'li |
| `ttl: "45s"` | Jiringlash muddatidan uzun bo'lmasin (§7.7) |
| `data` qiymatlari **doim `string`** | FCM boshqa turni qabul qilmaydi (`05-PUSH-SETUP.md` §4) |

Qulflangan ekranda jiringlash uchun klient tomonda kerak bo'ladiganlar (backend ishi emas,
lekin rejaga kirsin):

- `USE_FULL_SCREEN_INTENT` ruxsati va `CATEGORY_CALL` li bildirishnoma;
- Android 14+ da to'liq ekranli intent **faqat qo'ng'iroq/budilnik ilovalariga** ruxsat
  etilgan — ilova o'zini shunday e'lon qilishi kerak, aks holda bildirishnoma pastdan
  chiqadi-yu, ekranni egallamaydi;
- barqarorroq yo'l — **`ConnectionService` (Telecom API)**: tizim qo'ng'iroq ekranini o'zi
  chizadi, Bluetooth naushnik tugmalari ishlaydi, oddiy telefon qo'ng'irog'i bilan
  to'qnashuv tizim tomonidan hal qilinadi.

iOS'da bunga mos narsa — **CallKit**, va u yerda tanlov yo'q: VoIP push CallKit'siz
ishlamaydi (§7.4).

### 7.6 Push **doim** yuboriladi — bu xabarlardan farq qiladi

`05-PUSH-SETUP.md` §5 dagi qoida: **ochiq WebSocket bo'lsa push yuborilmaydi** (ikki marta
bildirishnoma chiqmasligi uchun). 

> ⚠️ **Qo'ng'iroqda bu qoida qo'llanmasin.**

| Platforma | Qoida |
|---|---|
| **iOS** | VoIP push **har doim** yuborilsin — ochiq socket bo'lsa ham |
| **Android** | Ochiq socket bo'lsa push yuborilmasa ham bo'ladi; lekin yuborilsa ham zarar yo'q |

Nega iOS'da doim: iOS ilova fonga o'tgach WebSocket'ni **bir necha soniyada uzadi yoki
muzlatadi**, lekin server buni darhol bilmaydi — soket hali "ochiq" ko'rinib turadi. Ya'ni
"socket bor" degan tekshiruv iOS'da **yolg'on** natija beradi va qo'ng'iroq jimgina
yo'qoladi.

Ikki marta ko'rsatish muammosi klientda `callId` bo'yicha hal qilinadi: WS hodisasi ham,
push ham bir xil `callId` olib keladi, klient ikkinchisini e'tiborsiz qoldiradi. Shuning
uchun `callId` push payloadida **majburiy**.

### 7.7 Bekor qilish va eskirgan push

Chaquvchi tashladi, boshqa qurilma javob berdi yoki 45 soniya tugadi — server **darhol**
"bekor" push'ini yuborsin:

```jsonc
{ "type": "call_cancel", "callId": "cal_01J…" }
```

| Platforma | Kanal |
|---|---|
| iOS | **Yana VoIP push** (oddiy push emas) — ilova uxlagan bo'lishi mumkin, faqat VoIP uni uyg'otadi va u CallKit sessiyasini yopadi |
| Android | O'sha data-push |

Busiz telefon **bo'sh joyga jiringlab turadi** — foydalanuvchi javob beradi, u yerda hech kim
yo'q. Bu qo'ng'iroq tizimining eng ko'zga tashlanadigan nuqsoni.

**Eskirgan push yetib kelmasin:** `apns-expiration: 0` (APNs uni darhol yetkazadi yoki
butunlay tashlaydi, navbatda ushlab turmaydi) va FCM'da `ttl: "45s"`. Aks holda tarmoqsiz
qolgan telefon 10 daqiqadan keyin internetga ulanib, allaqachon tugagan qo'ng'iroq uchun
jiringlay boshlaydi.

---

## 8. Chatdagi yozuv — `type = CALL` xabari

### 8.1 Qachon yaratiladi

**Har bir qo'ng'iroq uchun aynan bitta xabar, qo'ng'iroq tugagan paytda.**

Boshlanganda yaratib, keyin yangilash **mumkin emas**: protokolda `message:updated` hodisasi
yo'q (`03-WEBSOCKET.md`), ya'ni klient o'zgarishni bilmaydi va ekranda «Qo'ng'iroq davom
etmoqda» abadiy qolib ketadi.

Xabar `call:ended` bilan **bir vaqtda** yuborilsin, va `call:ended.messageId` unga ishora
qilsin — klient darhol chizadi, `message:new` ni kutmaydi.

Yaratiladigan holatlar: **hammasi**, jumladan `TIMEOUT`, `DECLINED`, `CANCELED`, `UNREACHABLE`,
`FAILED`. Qo'ng'iroq bo'lgani — bu voqea; foydalanuvchi uni ko'rishi kerak.

### 8.2 Shakl

```jsonc
{
  "id": "msg_01J…",
  "conversationId": "cnv_01H…",
  "senderId": "std_01H…",          // DOIM callerId — kim boshlagani shundan bilinadi
  "seq": 149,
  "type": "CALL",
  "body": null,                     // CALL da matn YO'Q
  "attachment": null,
  "sticker": null,
  "albumId": null,
  "clientMsgId": null,              // serverdan tug'ilgan xabar
  "call": {
    "callId": "cal_01J…",
    "media": "VIDEO",               // AUDIO | VIDEO
    "endReason": "HANGUP",
    "durationMs": 184000,           // javob berilmagan bo'lsa 0
    "startedAt": "2026-07-31T09:11:27.000Z",
    "answeredAt": "2026-07-31T09:11:34.000Z",   // null — javob berilmagan
    "endedAt": "2026-07-31T09:14:38.000Z"
  },
  "createdAt": "2026-07-31T09:14:38.000Z"
}
```

`senderId = callerId` bo'lishi shart: klient «kiruvchi/chiquvchi» ni shu bo'yicha aniqlaydi
va hech qanday qo'shimcha maydon kerak emas.

### 8.3 Klient nimani chizadi

Serverdan **tayyor matn kerak emas** — lokalizatsiya klientda (`CHAT_MEDIA_AND_CALLS_BACKEND.md`
§2.3 dagi qoida). Klient `endReason` + `senderId == me` juftligidan matnni o'zi yasaydi:

| `endReason` | Chaquvchida | Chaqirilganda |
|---|---|---|
| `HANGUP` | «Chiquvchi qo'ng'iroq · 3:04» | «Kiruvchi qo'ng'iroq · 3:04» |
| `TIMEOUT`, `UNREACHABLE` | «Javob bo'lmadi» | «Javobsiz qo'ng'iroq» |
| `CANCELED` | «Bekor qilindi» | «Javobsiz qo'ng'iroq» |
| `DECLINED` | «Rad etildi» | «Siz rad etdingiz» |
| `BUSY` | «Band edi» | (ko'rinmaydi) |
| `FAILED` | «Ulanmadi» | «Ulanmadi» |

### 8.4 ⚠️ O'qilmaganlar sanog'i — ikkita alohida qoida

`CALL` xabari oddiy xabar kabi `senderId` bilan keladi, ya'ni **standart mantiq uni
chaqirilgan uchun o'qilmagan deb hisoblaydi**. Bu ikki holatda to'g'ri, bir holatda xato:

| Holat | O'qilmaganmi | Nega |
|---|---|---|
| Javobsiz (`TIMEOUT`, `CANCELED`, `UNREACHABLE`) | ✅ **ha** | Bu — bildirishnoma; foydalanuvchi ko'rishi kerak |
| Rad etilgan (`DECLINED`) | ❌ yo'q | Foydalanuvchining o'zi rad etgan, ya'ni ko'rgan |
| **Javob berilgan** (`answeredAt != null`) | ❌ **yo'q** | Ikkala tomon ham qo'ng'iroqda edi |

> ⚠️ Uchinchi qatorsiz **har bir muvaffaqiyatli qo'ng'iroqdan keyin chatda soxta "1 ta
> o'qilmagan" belgisi** qoladi. Foydalanuvchi suhbatni ochib yopadi, u yo'qoladi — va
> keyingi qo'ng'iroqda yana paydo bo'ladi. Chegaraviy holatga o'xshaydi, amalda esa eng
> ko'p uchraydigan holat.

Yechim: `answeredAt != null` yoki `endReason = DECLINED` bo'lsa, server chaqirilganning
`readSeq` ini shu xabar `seq` igacha **avtomatik suradi**.

Ikkala holatda ham `CALL` xabari `lastMessageAt` ni yangilaydi va suhbat ro'yxatining
tepasiga chiqadi — bu to'g'ri, qo'ng'iroq ham muloqot.

### 8.5 Javobsiz qo'ng'iroq push'i

Javobsiz qoldirilgan qo'ng'iroqdan keyin **oddiy** push (VoIP emas!) ketsin:

```
title: "Javobsiz qo'ng'iroq"
body:  "📞 Aziz Karimov"        // video bo'lsa "📹"
data:  { conversationId, messageType: "CALL", callId }
```

Tartib muhim: **avval** `call_cancel` (jiringlashni to'xtatish, §7.7), **keyin** javobsiz
qo'ng'iroq push'i. Teskarisi bo'lsa telefon avval "javobsiz" deb yozadi, keyin jiringlay
boshlaydi.

### 8.6 `GET /v1/calls` — tarix

```
GET /v1/calls?page=1&size=20&peerId=std_01H…
→ { items: CallDto[], page, size, total, hasNext }
```

⚠️ Sahifalash **chat uslubida**: `?page=` **1 dan**, query'da
(`CHAT_MEDIA_AND_CALLS_BACKEND.md` §19.4). Ilovada allaqachon ikki xil sahifalash qoidasi
bor, uchinchisi qo'shilmasin.

Bu ekran v1 uchun **majburiy emas** (§14) — chatdagi `CALL` xabarlari tarixning o'zi.

---

## 9. Xato kodlari

| Kod | HTTP / WS | Qachon |
|---|---|---|
| `CALL_NOT_CONNECTED` | 403 | Chaquvchi va chaqirilgan bog'lanmagan |
| `CALL_BLOCKED` | 403 | Tomonlardan biri ikkinchisini bloklagan |
| `CALL_SELF` | 422 | O'ziga qo'ng'iroq |
| `CALL_NOT_FOUND` | 404 | `callId` yo'q, tugagan, yoki so'rovchi bu qo'ng'iroq ishtirokchisi emas |
| `CALL_BUSY` | 409 | Chaqirilganning boshqa faol qo'ng'irog'i bor |
| `CALL_ALREADY_ACTIVE` | 409 | **Chaquvchining** o'zida faol qo'ng'iroq bor |
| `CALL_INVALID_STATE` | 409 | Holatga mos kelmaydigan hodisa (masalan `ACTIVE` da ikkinchi `accept`) |
| `CALL_MEDIA_UNSUPPORTED` | 422 | `media` `AUDIO`/`VIDEO` dan tashqarida |
| `CALL_SDP_INVALID` | 422 | `sdp` bo'sh yoki 64 KB dan katta |
| `CALL_RATE_LIMITED` | 429 | §10 chegarasi |
| `TURN_UNAVAILABLE` | 503 | `TURN_SECRET` sozlanmagan yoki coturn javob bermayapti |
| `TOKEN_EXPIRED` | 401 | **Mavjud kod, o'zgarmaydi** |

**`CALL_NOT_FOUND` — begona `callId` uchun ham aynan shu kod.** «Bunday qo'ng'iroq yo'q» va
«bu sizning qo'ng'irog'ingiz emas» ni ajratmang: ajratsangiz, begona odam `callId` larni
sinab ko'rib, qaysi biri mavjudligini aniqlay oladi.

**Nega `CALL_` prefiksi**, chatdagi `NOT_CONNECTED` / `BLOCKED` qayta ishlatilmadi: chatda bu
xatolar xabar pufagining yonida kichkina yozuv bo'lib chiqadi, qo'ng'iroqda esa butun ekranni,
mikrofonni va CallKit sessiyasini yopish kerak. Ikki xil ish — ikki xil kod bo'lsin.

`TOKEN_EXPIRED` esa **aynan shu nom bilan** qolsin: klientda unga bog'langan avtomatik token
yangilash mantiqi bor (`03-WEBSOCKET.md`), yangi nom uni jimgina buzadi.

---

## 10. Limitlar

| Chegara | Qiymat | Nega |
|---|---|---|
| `call:invite` | **daqiqasiga 10 ta**, **soatiga 60 ta** | Spam-qo'ng'iroqqa qarshi. Javobsiz qoldirilganlari **alohida** hisoblansin |
| Bitta odamga ketma-ket javobsiz qo'ng'iroq | 5 tadan keyin **15 daqiqa** to'siq | «Javob bermayapti» → 40 marta qo'ng'iroq — bu ta'qib |
| `call:ice` | soniyasiga 50 ta | Normal qo'ng'iroqda 10–40 ta nomzod bo'ladi, ya'ni bu chegara faqat buzuq klientga tegadi |
| `sdp` hajmi | ≤ 64 KB | Signalizatsiya kanalini fayl tashish uchun ishlatishning oldini oladi |
| `GET /v1/calls/ice-servers` | daqiqasiga 10 ta | Kredensial TTL 1 soat — undan tez-tez so'rashning sababi yo'q |

`POST /v1/reports` ga **`callId`** qabul qilinsin — qo'ng'iroq ustidan shikoyat qilish uchun.
Hozir u faqat `messageId` oladi; qo'ng'iroqning `CALL` xabari bor, ya'ni `messageId` orqali
ham ishlaydi, lekin faqat qo'ng'iroq **tugagandan keyin**. Suhbat davomida shikoyat qilish
uchun `callId` kerak.

---

## 11. Telemetriya (ixtiyoriy, lekin juda foydali)

```
POST /v1/calls/{id}/stats
{ "rttMs": 78, "packetsLost": 41, "jitterMs": 12,
  "bytesSent": 2410000, "bytesReceived": 2380000,
  "candidatePairType": "relay",           // host | srflx | relay
  "audioLevelAvg": 0.31, "framesDecoded": 5412 }
```

Bu bitta maydon — `candidatePairType` — «TURN necha foiz qo'ng'iroqda ishlatilyapti» degan
savolga javob beradi, ya'ni coturn serveriga qancha trafik kerakligini **taxmin qilib emas,
o'lchab** bilasiz (§6.5). `rttMs` va `packetsLost` esa «qayerda sifat yomon» ni ko'rsatadi.

Ishga tushirishdan keyingi birinchi haftada bu ma'lumot juda qimmat bo'ladi. Keyin
kerak bo'lmasa o'chirib qo'yish mumkin.

---

## 12. Nima qilinmasin — v1 uchun kerak emas

| Nima | Nega yo'q |
|---|---|
| **Guruh qo'ng'irog'i (3+)** | SFU (mediasoup/LiveKit) talab qiladi — bu alohida server, alohida operatsion yuk va butunlay boshqa hajmdagi ish. 1:1 sof P2P bo'lgani uchun media serverdan umuman o'tmaydi (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §10). Hodisa nomlari kelajakda SFU'ga o'tishni buzmaydi |
| **Qo'ng'iroqni yozib olish** | Media DTLS-SRTP bilan uchidan-uchiga shifrlangan — server uni **ko'ra olmaydi**. Yozib olish uchun shifrni serverda ochish kerak, ya'ni butun arxitekturani o'zgartirish. Bundan tashqari suhbatni yozib olish O'zbekiston qonunchiligida ikkala tomonning roziligini talab qiladi |
| **Ekran ulashish** | Protokolda joyi bor (`call:renegotiate` orqali), lekin klient tomonda Android va iOS uchun alohida ruxsatlar va tizim API'lari kerak. Talabalar ilovasida kutilayotgan funksiya emas |
| **Kutish / almashish (call waiting)** | Bir vaqtda bitta qo'ng'iroq (§5.3). Ikkinchisini qabul qilish UI'da uch xil holat va serverda ikkita parallel media sessiyasini bildiradi — foydasiga arzimaydi |
| **Qo'ng'iroqni boshqa qurilmaga uzatish** | Kamdan-kam ishlatiladi, murakkabligi esa `call:taken` mantig'ini butunlay qayta yozishni talab qiladi |
| **Bog'lanmagan odamga qo'ng'iroq** | §4 dagi eshik — chat bilan bir xil. Istisno yo'q |
| **Veb-klient** | Ilova mobil; veb uchun signalizatsiya bir xil, lekin sinov va qo'llab-quvvatlash yuki hozir ortiqcha |
| **Maxsus jiringlash ohanglari** | Tizim ohangi (CallKit / ConnectionService) ishlatiladi — bu to'g'ri va bepul |

---

## 13. Klient tomonda nima tayyor / nima kerak

| Narsa | Holat | Izoh |
|---|---|---|
| `MessageType.CALL` domen enum'i | ✅ **bor** | `Chat.kt:19`. Lekin OpenAPI `MessageTypeDto` da **yo'q** — qo'shilishi shart |
| Klientdan `CALL` yuborilmasligi | ✅ bloklangan | `SendPayload.kt:103` — server ham `422` qaytarsin |
| Qo'ng'iroq tugmalari (UI joyi) | ⚠️ bor, «tez orada» | `PeerProfileSheet.kt:249, 252` |
| Socket.IO transporti | ✅ ishlaydi | Bitta namespace — §2 dagi qarorning sababi |
| Push token ro'yxatdan o'tishi | ✅ bor | `tokenType` yo'q — §7.3 |
| `CallDto` / `MessageDto.call` | ❌ | Spec'dan generatsiya qilinadi |
| **WebRTC bindings** | ❌ **eng katta ish** | KMP'da tayyor umumiy kutubxona yo'q: Android — `libwebrtc`, iOS — `WebRTC.framework`, ustidan `expect/actual` qatlam yoziladi |
| CallKit (iOS) / ConnectionService (Android) | ❌ | Platformaga xos, `expect/actual` |
| VoIP token (PushKit) | ❌ | iOS'da `PKPushRegistry` |
| Qo'ng'iroq ekrani | ❌ | Compose Multiplatform + platforma video surface'i |

**Klient tomonidagi ish backend ishidan uzun.** Shuning uchun kontraktni (spec + WS hujjati)
imkon qadar erta qotirish kerak — WebRTC qatlami yozilayotganda backend ham parallel
ishlaydi.

---

## 14. Spec (OpenAPI) o'zgarishlari

**Yangi endpointlar:**

| Metod | Yo'l | Nima uchun |
|---|---|---|
| `GET` | `/v1/calls/ice-servers` | Vaqtinchalik TURN kredensiali (§6.3) |
| `GET` | `/v1/calls/active` | Sovuq startdan keyin holatni tekshirish (§5.6) |
| `GET` | `/v1/calls` | Tarix (§8.6) — v1 uchun majburiy emas |
| `POST` | `/v1/calls/{id}/stats` | Telemetriya (§11) — ixtiyoriy |

**O'zgaradigan modellar:**

| Model | O'zgarish |
|---|---|
| `MessageTypeDto` | **`+CALL`** — hozir yo'q (tekshirildi) |
| `MessageDto` | `+call` (nullable `CallInfoDto`) |
| `RegisterDeviceDto` | `+tokenType` (`FCM \| APNS \| APNS_VOIP`, ixtiyoriy) |
| `ReportRequestDto` | `+callId` (nullable) |

**Yangi modellar:** `CallInfoDto`, `CallDto`, `IceServersDto`, `IceServerDto`,
`ActiveCallDto`, `CallStatsDto`.

> ⚠️ **Tiplash — codegen'ni buzadigan joy** (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §19).
> NestJS nullable satrni tipsiz `object` deb yozadi va generator undan `kotlin.Any?` chiqaradi,
> natijada **klient umuman kompilyatsiya bo'lmaydi**. Yangi maydonlar aniq tiplansin:
>
> - `durationMs`, `ttlSeconds`, `rttMs`, `packetsLost` → `{"type":"integer","format":"int32"}`
>   (`number` **emas** — u `Double` chiqaradi)
> - `answeredAt`, `endedAt` → `{"type":"string","format":"date-time","nullable":true}`
> - `call` → `{"allOf":[{"$ref":"#/components/schemas/CallInfoDto"}],"nullable":true}`
>   (OpenAPI 3.0 da `$ref` yonidagi kalitlar e'tiborsiz qoladi, ya'ni `"$ref": …, "nullable": true`
>   **ishlamaydi**)
> - `iceServers[].urls` → `{"type":"array","items":{"type":"string"}}`

**WebSocket hujjati:** §3 dagi barcha `call:*` hodisalari `handoff/03-WEBSOCKET.md` ga
qo'shilsin — Swagger'da ularning joyi yo'q va bu hujjat yagona manba bo'lib qoladi.

---

## 15. Ustuvorlik

Tartib **bog'liqlik va yetkazib berish muddati** bo'yicha, murakkablik bo'yicha emas.

| # | Ish | Nega aynan shu tartibda |
|---|---|---|
| **1** | **coturn: server/IP, DNS, sertifikat, 443/TLS** (§6) | **Eng uzun muddatli va tashqi bog'liqlikka ega.** Alohida IP, DNS yozuvi, Let's Encrypt — bularning hech biri kod emas va bir kunda bo'lmaydi. Qolgan hamma narsa tayyor bo'lib, TURN yo'qligi uchun kutib turishi — eng bekor vaqt yo'qotish |
| **2** | **Spec: `MessageTypeDto +CALL`, `MessageDto.call`, `RegisterDeviceDto.tokenType`** (§14) | **Butunlay qo'shuvchi (additive) va hech narsani buzmaydi** — bugun deploy qilinsa ham hech kim sezmaydi. Lekin klient shundan keyingina kod generatsiya qilib, qo'ng'iroq modelini qura boshlaydi. Ya'ni bu bir kunlik ish ikki haftalik klient ishini ochadi |
| **3** | **`call:*` signalizatsiyasi `/chat` da + `Call` jadvali** (§3–§5) | Protokolning o'zi. Buni ikki tomon (klient/server) parallel yoza olishi uchun **kontrakt oldin** qotirilsin (`03-WEBSOCKET.md` yangilansin), implementatsiya keyin |
| **4** | **`GET /v1/calls/ice-servers`** (§6.3) | 1-punkt tayyor bo'lishi bilan darhol. Klient buni signalizatsiyadan **oldin** sinay oladi (WebRTC ulanishini qo'lda tekshirish) |
| **5** | **VoIP push adapteri (APNs to'g'ridan-to'g'ri)** (§7) | Busiz **iOS'da qo'ng'iroq faqat ilova ochiq bo'lganda ishlaydi** — ya'ni amalda ishlamaydi. Lekin 3-punktsiz uni sinab ko'rishning imkoni yo'q, shuning uchun keyin |
| **6** | **`CALL` xabari, o'qilmaganlar qoidasi, javobsiz push** (§8) | Qo'ng'iroq ishlagandan keyin qo'shiladi; hech narsani bloklamaydi |
| **7** | `GET /v1/calls/active` (§5.6) | Faqat VoIP push bilan birga ma'noga ega |
| **8** | `GET /v1/calls`, `POST /v1/calls/{id}/stats` (§8.6, §11) | v1 uchun majburiy emas. Tarix chatning o'zida bor |

### Nima parallel ketishi mumkin

- **1 va 2** bir-biriga bog'liq emas — ikkalasi ham birinchi kuni boshlanishi mumkin.
- **3** ishlayotganda klient WebRTC bindings'ini yozadi (§13) — bu eng uzun klient ishi.
- **5** ni faqat `.p8` kaliti topilgandan keyin boshlash mumkin (§7.2) —
  **buni bugun tekshiring**, chunki kalit yo'qolgan bo'lsa yangisini yasash va Firebase'dagi
  eskisini almashtirish alohida kun oladi.

### Eng katta xavf

Klient tomonidagi **WebRTC + CallKit/ConnectionService** ishi backend ishidan uzunroq
(§13). Ya'ni backend "tayyor" bo'lgach ham qo'ng'iroq bir necha hafta ko'rinmaydi.
Buni rejaga kiritish kerak — aks holda 1–5 punktlar bajarilgandan keyin «nega hali
ishlamayapti» degan savol tug'iladi.
