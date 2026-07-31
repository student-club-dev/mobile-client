package dev.feature.chat.data.mapper

import dev.core.network.appJson
import dev.core.network.generated.api.ChatApi
import dev.core.network.generated.model.GifDto
import dev.core.network.generated.model.GifRefDto
import dev.core.network.generated.model.GifSearchResponseDto
import dev.core.network.generated.model.MediaProviderDto
import dev.core.network.generated.model.StickerDto
import dev.core.network.generated.model.StickerPackDto
import dev.core.network.generated.model.ProviderStickerDto
import dev.core.network.generated.model.StickerPacksDto
import dev.core.network.generated.model.StickerRefDto
import dev.core.network.generated.model.StickerSearchResponseDto
import dev.feature.chat.domain.model.GifErrorKind
import dev.feature.chat.domain.model.GifItem
import dev.feature.chat.domain.model.GifLocale
import dev.feature.chat.domain.model.GifPage
import dev.feature.chat.domain.model.GifProvider
import dev.feature.chat.domain.model.GifRef
import dev.feature.chat.domain.model.Sticker
import dev.feature.chat.domain.model.StickerPack
import dev.feature.chat.domain.model.StickerRef
import dev.feature.chat.domain.model.StickerSearchItem
import dev.feature.chat.domain.model.StickerSearchPage
import dev.core.network.response.BaseResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// ---------------------------------------------------------------------------
// GIF
// ---------------------------------------------------------------------------

/** `GifSearchResponseDto` → domen sahifasi. */
fun GifSearchResponseDto.toDomain(): GifPage {
    val gifProvider = provider.toDomain()
    return GifPage(
        items = items.map { it.toDomain(gifProvider) },
        // Kursor **shaffof**: bo'sh satr ham "yo'q" degani (provayder ba'zan shunday beradi).
        next = next?.takeIf { it.isNotBlank() },
        provider = gifProvider,
    )
}

fun GifDto.toDomain(provider: GifProvider): GifItem = GifItem(
    id = id,
    provider = provider,
    url = url,
    thumbUrl = thumbUrl,
    width = width,
    height = height,
    durationMs = durationMs,
)

fun MediaProviderDto.toDomain(): GifProvider = GifProvider.of(value)

/**
 * Yuborish uchun shakl.
 *
 * ⚠️ Maydonlar **hech qanday tozalash/normalizatsiyasiz** ko'chiriladi: server `url` ni
 * domen oq ro'yxatidan o'tkazadi va eng kichik o'zgarish ham `422 GIF_URL_NOT_ALLOWED`
 * beradi (`04-GIF-INTEGRATION.md`).
 */
fun GifRef.toDto(): GifRefDto = GifRefDto(
    provider = provider.toDto(),
    externalId = externalId,
    url = url,
    thumbUrl = thumbUrl,
    width = width,
    height = height,
    durationMs = durationMs,
)

/**
 * Domen provayderi → DTO.
 *
 * `UNKNOWN` uchun `KLIPY` beriladi: DTO enum'da noma'lum qiymat yo'q, va bu holat faqat
 * provayder almashib, klient hali yangilanmaganda yuzaga keladi — o'shanda ham `externalId`
 * va `url` to'g'ri, server esa provayderni o'zi biladi.
 */
fun GifProvider.toDto(): MediaProviderDto = when (this) {
    GifProvider.KLIPY, GifProvider.UNKNOWN -> MediaProviderDto.KLIPY
    GifProvider.TENOR -> MediaProviderDto.TENOR
    GifProvider.GIPHY -> MediaProviderDto.GIPHY
}

/**
 * Yuborish nuqtasi uchun **xom JSON** ko'rinishi.
 *
 * `message:send` tanasini quruvchi kod (`SendPayload.gif`) GIF moduliga bog'lanmasligi uchun
 * `JsonObject` kutadi. Bu funksiya `GifRefDto` serializatsiyasidan foydalanadi, ya'ni
 * maydonlar nomi va qiymatlari **spec bilan bir xil** bo'lib qoladi — qo'lda JSON yig'ilsa,
 * bitta xato nom `422 VALIDATION_ERROR` bo'lib qaytardi.
 */
fun GifRef.toJson(): JsonObject =
    appJson.encodeToJsonElement(GifRefDto.serializer(), toDto()).jsonObject

fun GifLocale.toDto(): ChatApi.LocaleGifsSearch = when (this) {
    GifLocale.UZ -> ChatApi.LocaleGifsSearch.uz_UZ
    GifLocale.RU -> ChatApi.LocaleGifsSearch.ru_RU
    GifLocale.EN -> ChatApi.LocaleGifsSearch.en_US
}

/** O'sha til, boshqa enum: generator har endpoint uchun alohida `Locale…` klassi chiqaradi. */
fun GifLocale.toStickerDto(): ChatApi.LocaleStickersSearch = when (this) {
    GifLocale.UZ -> ChatApi.LocaleStickersSearch.uz_UZ
    GifLocale.RU -> ChatApi.LocaleStickersSearch.ru_RU
    GifLocale.EN -> ChatApi.LocaleStickersSearch.en_US
}

// ---------------------------------------------------------------------------
// GIF xatolari — ikkita 429 ni AJRATISH
// ---------------------------------------------------------------------------

/**
 * Backenddagi xato kodlari (`04-GIF-INTEGRATION.md` va `handoff/06-STICKER-SEARCH.md` §2 jadvallari) — satrlar
 * bir joyda tursin.
 *
 * Stiker qidiruvi GIF qidiruvining aynan nusxasi, ya'ni kodlar ham bir xil naqshda, faqat
 * prefiks boshqa.
 */
internal object GifErrorCodes {
    const val PROVIDER_RATE_LIMITED = "GIF_PROVIDER_RATE_LIMITED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val PROVIDER_ERROR = "GIF_PROVIDER_ERROR"
    const val STICKER_PROVIDER_RATE_LIMITED = "STICKER_PROVIDER_RATE_LIMITED"
    const val STICKER_PROVIDER_ERROR = "STICKER_PROVIDER_ERROR"
}

/**
 * HTTP status + konvertdagi `error.code` → [GifErrorKind].
 *
 * **Sof funksiya** — testi `GifErrorTest` da. Nega alohida: umumiy `safeCall` xato *kodini*
 * tashlab yuboradi (u faqat statusni ko'radi), 429 ning ikki xili esa aynan kod bilan
 * farqlanadi va ular foydalanuvchiga **teskari** maslahat beradi:
 * biri "kuting" (provayder kvotasi), ikkinchisi "sekinlashing" (bizning chegara).
 *
 * Kod kelmagan (yoki tana bo'sh) 429 — ehtiyotkorlik uchun **provayder** kvotasi deb
 * hisoblanadi: hozirgi test kaliti global va soatiga 100 ta, ya'ni amalda 429 larning
 * ko'pchiligi shundan keladi va foydalanuvchini bekorga ayblamaymiz.
 */
fun gifErrorKindOf(status: Int?, code: String?): GifErrorKind = when {
    // `when` ichidagi tartib muhim: kod statusdan **ustun**, chunki aynan u ikkita 429 ni
    // ajratadi.
    code == GifErrorCodes.PROVIDER_RATE_LIMITED -> GifErrorKind.PROVIDER_RATE_LIMITED
    code == GifErrorCodes.STICKER_PROVIDER_RATE_LIMITED -> GifErrorKind.PROVIDER_RATE_LIMITED
    code == GifErrorCodes.RATE_LIMITED -> GifErrorKind.RATE_LIMITED
    // `…_PROVIDER_ERROR` ikkala statusda ham keladi (502 va 503) — ularni AJRATADIGAN
    // narsa kod emas, status. Shuning uchun bu ikki qator statusni ham tekshiradi.
    code == GifErrorCodes.PROVIDER_ERROR || code == GifErrorCodes.STICKER_PROVIDER_ERROR ->
        if (status == 503) GifErrorKind.PROVIDER_NOT_CONFIGURED else GifErrorKind.PROVIDER_UNAVAILABLE
    status == 429 -> GifErrorKind.PROVIDER_RATE_LIMITED
    // 503 — kalit sozlanmagan: qayta urinish yordam bermaydi, shuning uchun 502/504 dan
    // ajratiladi (`handoff/06-STICKER-SEARCH.md` §5).
    status == 503 -> GifErrorKind.PROVIDER_NOT_CONFIGURED
    status == 502 || status == 504 -> GifErrorKind.PROVIDER_UNAVAILABLE
    // 404 — endpoint bu deploymentda hali yo'q (stiker qidiruvi backendga endi qo'shildi va
    // spec'ga ham tushmagan). Bu foydalanuvchi uchun "xizmat ishlamayapti" bilan bir xil:
    // panel tushunarli matn ko'rsatib, paketlarga qaytadi. `UNKNOWN` bo'lsa "xatolik, qayta
    // urining" deb aldab, bir nechta bekorchi so'rovga sabab bo'lardi.
    status == 404 -> GifErrorKind.PROVIDER_UNAVAILABLE
    else -> GifErrorKind.UNKNOWN
}

/**
 * Xato javobining **tanasidan** `error.code` ni oladi.
 *
 * Umumiy `parseErrorEnvelope` kodni qaytarmaydi (u faqat `AppException` quradi), shu sababli
 * konvert shu yerda qo'lda o'qiladi. Tana JSON bo'lmasa yoki konvert bo'lmasa `null` —
 * chaqiruvchi HTTP statusga qaytadi.
 */
fun apiErrorCodeOf(body: String): String? {
    if (body.isBlank()) return null
    val envelope = runCatching {
        appJson.decodeFromString(BaseResponse.serializer(JsonElement.serializer()), body)
    }.getOrNull() ?: return null
    return (envelope.error?.code ?: envelope.code)?.takeIf { it.isNotBlank() }
}

// ---------------------------------------------------------------------------
// Stikerlar
// ---------------------------------------------------------------------------

/**
 * Server katalogi → domen paketlari.
 *
 * Bo'sh (stikersiz) paketlar tashlab yuboriladi: backendda sxema va endpoint tayyor, lekin
 * **tasvirlarning o'zi hali yo'q** (`PENDING_ACTIONS.md` §6), ya'ni bo'sh paket yorlig'i
 * foydalanuvchiga bo'sh to'r ko'rsatgan bo'lardi.
 */
fun StickerPacksDto.toDomain(): List<StickerPack> =
    packs.filter { it.stickers.isNotEmpty() }.map { it.toDomain() }

fun StickerPackDto.toDomain(): StickerPack = StickerPack(
    id = id,
    name = name,
    // Yorliqdagi zaxira belgi — paketning birinchi stikeri emojisi (muqova rasmi
    // yuklanmaguncha yoki umuman ochilmasa shu ko'rinadi).
    cover = stickers.firstOrNull()?.emoji.orEmpty(),
    stickers = stickers.map { it.toDomain() },
    coverUrl = coverUrl.takeIf { it.isNotBlank() },
)

// ---------------------------------------------------------------------------
// Stiker qidiruvi (KLIPY) — `handoff/06-STICKER-SEARCH.md`
// ---------------------------------------------------------------------------

/** `StickerSearchResponseDto` → domen sahifasi. */
fun StickerSearchResponseDto.toDomain(): StickerSearchPage {
    val stickerProvider = provider.toDomain()
    return StickerSearchPage(
        items = items.map { it.toDomain(stickerProvider) },
        // Kursor **shaffof**: bo'sh satr ham "yo'q" degani (provayder ba'zan shunday beradi).
        next = next?.takeIf { it.isNotBlank() },
        provider = stickerProvider,
    )
}

fun ProviderStickerDto.toDomain(provider: GifProvider): StickerSearchItem = StickerSearchItem(
    id = id,
    provider = provider,
    url = url,
    // Kichik nusxa (~90×90, `xs`) — panel katakchasi uchun aynan mos. Bo'sh kelsa to'liq
    // tasvir ko'rsatiladi: bo'sh katakdan yaxshiroq.
    thumbUrl = thumbUrl.takeIf { it.isNotBlank() } ?: url,
    // ⚠️ O'lchamlar QAT'IY EMAS — KLIPY 96×96 dan 498×498 gacha beradi
    // (`handoff/06-STICKER-SEARCH.md` §1). Katak balandligi shulardan hisoblanadi.
    width = width,
    height = height,
    isAnimated = isAnimated,
)

/**
 * Yuborish uchun **xom JSON** shakli (`message:send` dagi `sticker` obyekti).
 *
 * ⚠️ Maydonlar **hech qanday tozalash/normalizatsiyasiz** ko'chiriladi: server `url` ni
 * domen oq ro'yxatidan o'tkazadi va eng kichik o'zgarish ham `422 STICKER_URL_NOT_ALLOWED`
 * beradi (`handoff/06-STICKER-SEARCH.md` §2).
 *
 * `GifRef.toJson()` bilan bir xil yo'l: `StickerRefDto` serializatsiyasidan quriladi, ya'ni
 * maydon nomlari **spec bilan bir xil** bo'lib qoladi — qo'lda JSON yig'ilsa, bitta xato nom
 * `422 VALIDATION_ERROR` bo'lib qaytardi.
 */
fun StickerRef.toDto(): StickerRefDto = StickerRefDto(
    provider = provider.toDto(),
    externalId = externalId,
    url = url,
    thumbUrl = thumbUrl,
    width = width,
    height = height,
)

fun StickerRef.toJson(): JsonObject =
    appJson.encodeToJsonElement(StickerRefDto.serializer(), toDto()).jsonObject

fun StickerDto.toDomain(): Sticker = Sticker(
    id = id,
    emoji = emoji,
    url = url.takeIf { it.isNotBlank() },
    packId = packId,
    // Serverdagi katalogdan — ya'ni `stickerId` bilan yuborilishi mumkin. Zaxira
    // katalogdagi (Fluent) stikerlarning ham tasviri bor, shuning uchun bu URL'dan
    // chiqarilmaydi (`Sticker.isRemote`).
    isRemote = true,
)
