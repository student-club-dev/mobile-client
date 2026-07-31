package dev.core.network.media

import dev.core.network.NetworkConfig

/**
 * API manzilining **origin** qismi — `https://api.studentclub.uz` (`/v1/` siz).
 *
 * [MediaUrl.normalize] ga aynan shu beriladi. Ilgari har chaqiruvchi buni o'zi
 * `substringBefore("/v1")` bilan qirqib olardi; bitta joyda turgani xavfsizroq.
 */
val NetworkConfig.apiOrigin: String get() = baseUrl.substringBefore("/v1").trimEnd('/')

/**
 * Serverdan kelgan **media havolasini tuzatadi**.
 *
 * Backend `POST /v1/media/upload` javobida havolani o'z ishga tushirilgan manzili bilan
 * quradi. Amalda u ko'pincha ilova ochib bo'lmaydigan shaklda keladi:
 *
 * | Serverdan kelgani | Nima uchun ochilmaydi |
 * |---|---|
 * | `http://localhost:3000/uploads/…` | `localhost` — **telefonning o'zi**, server emas |
 * | `http://api.studentclub.uz/…` | Android 9+ `http://` ni **bloklaydi** (cleartext) |
 * | `/uploads/LOGO/abc.jpg` | Nisbiy yo'l — Coil uni ocholmaydi |
 *
 * Shuning uchun havola ko'rsatishdan oldin shu yerdan o'tkaziladi: xosti almashtiriladi,
 * `http` → `https` ga ko'tariladi, nisbiy yo'lga bazaviy manzil qo'shiladi.
 *
 * ⚠️ Bu **vaqtinchalik himoya**: to'g'ri yechim — backend ochiq `https` havolasini
 * qaytarishi. Lekin bunday tuzatishsiz noto'g'ri sozlangan serverda **bitta ham rasm
 * ko'rinmaydi**, shuning uchun klient o'zini himoya qiladi.
 *
 * ⚠️ Rasmlar uchun buni Coil o'zining `Mapper` i orqali avtomatik qiladi, **video va ovoz
 * esa Coil'dan o'tmaydi**: ExoPlayer/AVPlayer nisbiy yo'lni `file://` deb o'qiydi va
 * "fayl topilmadi" bilan yiqiladi (ekranda esa jimgina qora ekran qoladi). Shuning uchun
 * media havolalari **domen modeliga o'tayotganda** shu yerdan o'tkaziladi — o'shanda
 * ekranlar allaqachon to'g'ri havola oladi.
 */
object MediaUrl {

    /** Server o'z ichida ishlaganini bildiruvchi xostlar — bularni almashtirish shart. */
    private val LOOPBACK_HOSTS = setOf(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        // Android emulyatorida "asosiy mashina", lekin haqiqiy qurilmada ma'nosiz.
        "10.0.2.2",
        "[::1]",
        "::1",
    )

    /**
     * [raw] havolani ko'rsatishga yaroqli holga keltiradi.
     *
     * [apiOrigin] — API manzilining **origin** qismi (`https://api.studentclub.uz`), ya'ni
     * `/v1/` siz. Bo'sh yoki noto'g'ri havolada `null` qaytadi — chaqiruvchi rasmni
     * umuman chizmaydi.
     */
    fun normalize(raw: String?, apiOrigin: String): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty()) return null

        val origin = apiOrigin.trim().trimEnd('/')
        if (origin.isEmpty()) return url

        return when {
            // Protokolsiz havola: `//cdn.example.uz/a.jpg`
            url.startsWith("//") -> "https:$url"

            // Nisbiy yo'l: `/uploads/LOGO/a.jpg` yoki `uploads/LOGO/a.jpg`
            !url.contains("://") -> origin + "/" + url.trimStart('/')

            // Server o'zining ichki manzilini bergan — xostni almashtiramiz.
            hostOf(url) in LOOPBACK_HOSTS -> origin + pathOf(url)

            // O'z serverimizga `http://` — Android buni bloklaydi, `https` ga ko'taramiz.
            url.startsWith("http://") && hostOf(url) == hostOf(origin) ->
                "https://" + url.removePrefix("http://")

            else -> url
        }
    }

    /** `https://api.studentclub.uz:443/a/b` → `api.studentclub.uz` (port hisobga olinmaydi). */
    private fun hostOf(url: String): String = url
        .substringAfter("://", url)
        .substringBefore('/')
        .substringBefore('?')
        .substringBeforeLast(':')
        .ifEmpty { url.substringAfter("://", url).substringBefore('/') }

    /** `http://localhost:3000/uploads/a.jpg` → `/uploads/a.jpg` (so'rov qismi bilan). */
    private fun pathOf(url: String): String {
        val afterScheme = url.substringAfter("://", "")
        val slash = afterScheme.indexOf('/')
        return if (slash < 0) "" else afterScheme.substring(slash)
    }
}
