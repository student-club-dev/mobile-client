# Qo'ng'iroq — klient tomonidagi holat

Backend handoff'i (`09-CALLS-README.md` … `09-CALLS-DEVIATIONS.md`) bo'yicha nima qilingani,
nima qolgani va nima uchun. Sana: 2026-08-03.

---

## Bajarilgan uchta majburiy ish (`09-CALLS-PREREQUISITES.md`)

| # | Ish | Holat | Qayerda |
|---|---|---|---|
| 1 | `MessageType.CALL` ga chidamlilik | ✅ | `dev/api-client-generator/build.gradle.kts` (qadam 11) |
| 2 | Qayta ulanishda `call:connected` | ✅ | `CallSessionManager.watchReconnect` |
| 3 | `call:auth { token }` | ✅ | `CallSessionManager.watchToken` |

### 1. Enum chidamliligi — **qanday hal qilindi**

Bizning yechim «`UNKNOWN` a'zosi» emas: `cleanSwagger` ning yangi 11-qadami **kengayadigan
enum'larni oddiy `string` ga o'giradi** (`lenientEnums` ro'yxati — `MessageTypeDto`,
`MediaKindDto`, `MediaStatusDto`, `ConversationTypeDto` va to'rtta qo'ng'iroq enum'i).
Domenga o'girish har doim `parseEnum(raw, default)` orqali ketadi.

Nima uchun aynan shunday: kotlinx.serialization noma'lum enum qiymatida
`SerializationException` tashlaydi va u bitta maydonni emas — **butun javobni** yiqitadi.
`coerceInputValues` esa maydonda **odatiy qiymat** bo'lishini talab qiladi, generator esa
majburiy maydonga odatiy qiymat qo'ymaydi. `String` + `parseEnum` bu ikkalasidan ham
qat'iyroq: server ro'yxatni xohlagancha kengaytirsa ham javob pars bo'laveradi va noma'lum
qiymat faqat **o'sha bitta xabarni** «noma'lum tur» qiladi.

⚠️ **Tasdiq:** bu yangi klient. Allaqachon tarqatilgan versiyada `MessageTypeDto` qat'iy
enum bo'lgan, ya'ni `CALL` qatori unga yetib borsa chat ekrani yiqiladi. Backend deploy'i
shu sababdan **yangi versiya tarqalgandan keyin** rejalashtirilishi kerak.

---

## Qanday qurilgan

| Qatlam | Modul | Nima qiladi |
|---|---|---|
| Domen | `:dev:feature:calls:domain` | `Call`, `CallSession`, `CallStatus`, `IceServers`, `CallStats`, xato kodlari |
| Signalizatsiya | `…:data/realtime/CallsSocket` | 17 hodisa, payload cheklari, ack shakli |
| REST | `…:data/remote` + `repository` | `ice-servers` (kesh + 503), `GET /v1/calls`, `POST …/stats` |
| Media | `…:data/engine/CallEngine` | interfeys; Android — `org.webrtc`, iOS — hali yo'q |
| Holat mashinasi | `…:data/session/CallSessionManager` | taymerlar, glare, qayta ulanish, teardown |
| UI | `:dev:feature:calls:presentation` | qo'ng'iroq ekrani, tarix, `CallHost` (ilova ustida) |

`chat:domain` → `calls:domain` ga bog'landi: chatdagi `CALL` pufakchasi aynan o'sha
enum'lardan foydalanadi.

### Protokolning nozik joylari — qayerda hisobga olingan

| Talab | Kod |
|---|---|
| `call:invite` ga `conversationId` **yubormaslik** | `CallsSocket.invite` — payloadda umuman yo'q |
| `candidate` da **aynan uchta kalit** | `CallsSocket.sendIce` — `usernameFragment` chiqmaydi |
| `sdpMid == null` bo'lgan nomzod yuborilmasin | `sendIce` bo'sh `sdpMid` da `null` qaytaradi |
| `relayOnly` → `iceTransportPolicy = RELAY` | `WebRtcCallEngine.openPeerConnection` |
| `RINGING` da `call:end` ishlamaydi | `CallSessionManager.hangUp` rolga qarab tanlaydi |
| `call:ringing` ni **chaqirilgan** yuboradi | `onIncoming` → `socket.sendRinging` |
| Band bo'lsa kiruvchini `BUSY` bilan rad etish | `onIncoming` — sessiya bo'lsa darhol `decline(BUSY)` |
| `RATE_LIMITED` da eksponensial pauza | `sendCandidate` — 250 ms → 500 → 1000 |
| Stats **tanlangan juftlikdan** | `WebRtcCallEngine.collectStats` |
| Javobsiz qo'ng'iroqda stats **yubormaslik** | `closeLocally(sendStats = …)` |
| 503 `NOT_IMPLEMENTED` ni ajratish | `AppException.errorCode` + `callsUnavailable` |

`AppException` ga yangi `errorCode` maydoni qo'shildi: backend konvertidagi `error.code`
endi typed xatogacha yetib boradi. Usiz `403` ning «bog'lanmagan» mi yoki «bloklangan» mi,
`503` ning «server yiqildi» mi yoki «qo'ng'iroq o'chirilgan» mi ekanini bilib bo'lmasdi.

---

## Nima ishlaydi va nima yo'q

| | Android | iOS |
|---|---|---|
| Signalizatsiya, holat mashinasi, taymerlar | ✅ | ✅ |
| REST (ice-servers / tarix / stats) | ✅ | ✅ |
| Chatdagi `CALL` pufakchasi | ✅ | ✅ |
| Qo'ng'iroq ekrani va tugmalari | ✅ | ✅ |
| **Media (WebRTC)** | ✅ `io.github.webrtc-sdk:android` | ❌ `WebRTC.framework` kerak |
| Fonda mikrofon (old plan xizmati) | ✅ | ❌ CallKit — 2-bosqich |
| Dumaloq video xabar — **qabul qilish** | ✅ | ✅ |
| Dumaloq video xabar — **yozib olish** | ✅ | ❌ kvadratga kesish yo'q |

### Dumaloq video xabar (`VIDEO_NOTE`)

Oqim: biriktirish varag'idagi «Video xabar» → tizim kamerasi → **dumaloq ko'rish varag'i**
(`VideoNotePreviewSheet`) → yuborish. Kvadratga kesish, 60 soniyaga qirqish va 12 MB ga
sig'adigan bitreytda qayta kodlash yuborilgandan **keyin**, yuklash halqasi ichida ketadi
(`rememberVideoNotePreparer` → media3 `Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP`).

Ko'rish varag'i shu sababdan bor: tizim kamerasi to'rtburchak kadr beradi va foydalanuvchi
doiraga nima tushishini ko'rmaydi. Varaqda video aynan yuboriladigan shaklda chiziladi.

⚠️ **Yon ta'sir:** `CAMERA` ruxsati (video qo'ng'iroq uchun) manifestga qo'shilgani sababli
tizim kamerasini ochish endi **ruxsat talab qiladi** — Android ilova ruxsatni e'lon qilgan
bo'lsa `ACTION_VIDEO_CAPTURE` ni ham himoyalangan deb hisoblaydi. Shuning uchun
`rememberVideoCapture` endi ruxsatni tugma bosilganda so'raydi; usiz mavjud «Kamera»
tugmasi `SecurityException` bilan yiqilardi.

iOS'da `IosCallEngineFactory` **jimgina yiqilmaydi**: `createOffer` `null` qaytaradi, ya'ni
`call:invite` gacha yetib bormaydi va server chegaralari sarflanmaydi.

## Nima qolgan

| Ish | Nima uchun hozir emas |
|---|---|
| iOS media qatlami (`WebRTC.framework` + CallKit) | Xcode loyihasiga paket qo'shish talab qiladi |
| iOS'da dumaloq video xabarni **yozib olish** | `AVAssetExportSession` kesa olmaydi; `AVMutableVideoComposition` kerak (Android ✅) |
| VoIP push | Backendning **2-bosqichi** — hozircha qo'ng'iroq faqat ilova ochiq bo'lganda keladi |
| Hujjat tanlagichining oqimli varianti | Hozircha fayl xotiraga o'qiladi → 100 MB chegara (`MAX_FILE_BYTES`) |

---

## Rollout darvozalari

Backend tomonda `CALLS_ENABLED` hali `false` va coturn ko'tarilmagan. Shu sabab **bugun**:

- `GET /v1/calls/ice-servers` → `503 NOT_IMPLEMENTED` (kutilgan holat),
- klient «Qo'ng'iroq hozircha mavjud emas» deydi va qo'ng'iroq boshlanmaydi,
- `GET /v1/calls` va chatdagi `CALL` pufakchasi bayroqdan qat'i nazar ishlaydi.

`CALLS_ENFORCE_TOKEN_EXPIRY` ni `true` ga o'girish uchun **`call:auth` chiqarilgan bo'lishi**
shart edi — Android tomonda u endi bor (`watchToken`), iOS'da ham (kod umumiy).
