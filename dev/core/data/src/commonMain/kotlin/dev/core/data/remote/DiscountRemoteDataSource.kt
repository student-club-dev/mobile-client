package dev.core.data.remote

import dev.core.common.Resource
import dev.core.data.dto.DiscountsResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Chegirmalar uchun masofaviy (backend) manba — B4 offline-first shablonining tarmoq qismi.
 *
 * Repository shu interfeys orqali serverdan oladi va local DB'ga yozadi. Ktor klientiga
 * Firebase ID token allaqachon avtomatik qo'shiladi (B3). Boshqa domenlar (Jobs, Students...)
 * aynan shu shakldan nusxa oladi.
 */
interface DiscountRemoteDataSource {
    suspend fun fetchDiscounts(): Resource<DiscountsResponseDto>
}

/** Ktor implementatsiyasi. Endpoint real API kelганда `student-clubs.json` ga moslanadi. */
class KtorDiscountRemoteDataSource(
    private val client: HttpClient,
) : DiscountRemoteDataSource {

    override suspend fun fetchDiscounts(): Resource<DiscountsResponseDto> = try {
        // Bitta endpoint kategoriyalar + takliflarni qaytaradi. Agar API alohida bo'lsa,
        // shu yerda ikkita `get(...)` chaqirib birlashtiring.
        val body: DiscountsResponseDto = client.get("discounts").body()
        Resource.Success(body)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Chegirmalarni yuklab bo'lmadi", e)
    }
}
