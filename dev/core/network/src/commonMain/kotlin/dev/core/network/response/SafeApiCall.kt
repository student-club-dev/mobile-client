package dev.core.network.response

import dev.core.common.Resource
import dev.core.common.errorOf
import dev.core.common.error.AppException
import dev.core.common.error.AppMessageBus
import dev.core.common.error.toAppException
import dev.core.common.network.NetworkConnectivity
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Barcha API so'rovlari uchun **yagona xavfsiz o'ram** (IYM-business naqshi).
 *
 * Bitta joyda: internet tekshiruvi → so'rov → konvert [check] → barcha istisnolarni typed
 * [AppException] ga aylantirish. Har data-source shuni chaqiradi, o'zi try/catch yozmaydi:
 *
 * ```
 * suspend fun getProfile() = safeApiCall(connectivity) { api.getProfile() } // BaseResponse<Profile>
 * ```
 *
 * [call] backend'ning standart konvertini ([BaseResponse]) qaytarishi kutiladi.
 */
suspend fun <T> safeApiCall(
    connectivity: NetworkConnectivity? = null,
    call: suspend () -> BaseResponse<T>,
): Resource<T> = runSafely(connectivity) { call().check() }

/**
 * Konvertsiz (raw) variant — generatsiya qilingan API to'g'ridan-to'g'ri modelni qaytarganda
 * yoki uchinchi-tomon xizmatlar uchun. Xato-ishlash aynan bir xil.
 */
suspend fun <T> safeCall(
    connectivity: NetworkConnectivity? = null,
    call: suspend () -> T,
): Resource<T> = runSafely(connectivity, call)

private suspend fun <T> runSafely(
    connectivity: NetworkConnectivity?,
    block: suspend () -> T,
): Resource<T> {
    // Internet yo'q bo'lsa — so'rov qilmasdan aniq xato.
    //
    // ⚠️ Lekin BIR MARTA qayta tekshiriladi. Ilova fondan qaytganda (yoki sovuq
    // ishga tushganda) tizim faol tarmoqni bir necha yuz millisekundda tiklaydi va
    // aynan shu oynaga ilovaning ilk so'rovlari tushardi: foydalanuvchi hech qanday
    // sabab ko'rmay "Internet aloqasi yo'q" toastini olardi, keyingi so'rov esa
    // muvaffaqiyatli ketardi. Qisqa kutish haqiqiy offline holatda ham sezilarli
    // kechikish bermaydi (bir marta, 400ms).
    if (connectivity?.isOnline() == false) {
        delay(OFFLINE_RECHECK_MS)
        if (!connectivity.isOnline()) return failure(AppException.NoInternet())
    }
    return try {
        Resource.Success(block())
    } catch (e: CancellationException) {
        throw e // korutina bekori — uzatiladi
    } catch (e: AppException) {
        failure(e) // checker allaqachon typed tashlagan
    } catch (e: ResponseException) {
        // ⚠️ 2xx BO'LMAGAN HAMMA javob — 3xx, 4xx, 5xx — bitta yo'ldan o'tadi va tanasi
        // o'qiladi. Ilgari faqat `ClientRequestException` (4xx) va `ServerResponseException`
        // (5xx) ushlanardi, qolgani esa "Noma'lum xatolik" bo'lib chiqardi.
        failure(e.toAppExceptionWithFields())
    } catch (e: Throwable) {
        // Tarmoq/timeout/parse — matn va joriy internet holatiga qarab.
        failure(e.toAppException(connectivity?.isOnline() ?: true))
    }
}

/** Tarmoq holati "offline" deb ko'ringanda shuncha kutib bir marta qayta tekshiriladi. */
private const val OFFLINE_RECHECK_MS = 400L

/**
 * Xatoni [Resource.Error] ga aylantiradi VA [AppMessageBus] ga yuboradi.
 *
 * Bu — butun ilovadagi yagona nuqta: har qanday API javobidagi xato shu yerdan o'tadi,
 * demak ildizdagi toast hech qanday ekran uni ko'rsatishini kutmaydi. Ekran o'z inline
 * xatosini ko'rsatishda davom etadi — ular bir-birini almashtirmaydi.
 */
private fun failure(e: AppException): Resource.Error {
    AppMessageBus.error(e)
    return errorOf(e)
}

/**
 * 2xx bo'lmagan javobning **tanasini o'qib** typed xato quradi.
 *
 * `expectSuccess = true` bo'lgani uchun bunday javoblar [EnvelopeUnwrapPlugin] gacha
 * yetmaydi — Ktor ularni shu istisno bilan tashlaydi. Tana esa aynan eng qimmatli
 * ma'lumotni saqlaydi: serverning O'Z xabari va (422 da) maydon xatolari. Uni o'qimasak
 * foydalanuvchi har doim klientning umumiy matnini ko'rardi.
 *
 * Tana qanday shaklda bo'lishidan qat'i nazar matn topiladi ([parseErrorEnvelope]).
 * Faqat butunlay bo'sh/foydasiz tanada HTTP status bo'yicha zaxira matnga o'tiladi.
 *
 * Istisnodagi javob — `save()` qilingan nusxa (Ktor tanani xotirada saqlaydi), shuning uchun
 * uni qayta o'qish xavfsiz.
 */
suspend fun ResponseException.toAppExceptionWithFields(): AppException {
    val status = response.status
    val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
    val parsed = parseErrorEnvelope(body, status.value) ?: return status.toAppException(this)
    // Konvertda `cause` yo'q — asl istisnoni log/telemetriya uchun saqlab qo'yamiz.
    return if (parsed is AppException.Validation) {
        AppException.Validation(parsed.reason, parsed.fields, this).withCode(parsed.errorCode)
    } else {
        parsed
    }
}

/**
 * HTTP status kodini typed [AppException] ga aylantiradi — **javob tanasisiz zaxira yo'l**.
 *
 * Bu yerga faqat server hech qanday o'qishga arziydigan matn bermaganda tushiladi.
 * O'shanda ham kod matnda qoladi (`(502)`), aks holda foydalanuvchining "xatolik chiqdi"
 * degan xabaridan keyin nima bo'lganini aniqlab bo'lmasdi.
 */
fun HttpStatusCode.toAppException(cause: Throwable? = null): AppException {
    // Ktor'ning o'z tavsifi (`Not Found`, `Bad Gateway`) — inglizcha, lekin hech nimadan
    // ko'ra aniqroq: u kamida qaysi turdagi xato ekanini aytadi.
    val label = description.ifBlank { "HTTP $value" }
    return when (value) {
        401 -> AppException.Unauthorized(cause)
        403 -> AppException.PermissionDenied(cause)
        404 -> AppException.NotFound(cause)
        408 -> AppException.Timeout(cause)
        in 500..599 -> AppException.Server(
            code = value,
            cause = cause,
            reason = NetworkStrings.serverError(label, value),
        )
        else -> AppException.Validation(
            reason = NetworkStrings.rejectedWithLabel(label, value),
            cause = cause,
        )
    }
}
