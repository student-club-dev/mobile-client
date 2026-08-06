package dev.feature.notifications.data.push

import dev.core.common.AppDispatchers
import dev.core.common.Resource
import dev.core.common.network.NetworkConnectivity
import dev.core.common.push.PushRegistrar
import dev.core.network.generated.api.NotificationsApi
import dev.core.network.generated.model.DevicePlatformDto
import dev.core.network.generated.model.DeviceTokenTypeDto
import dev.core.network.generated.model.RegisterDeviceDto
import dev.core.network.response.safeCall
import dev.feature.notifications.domain.push.DevicePlatform
import dev.feature.notifications.domain.push.DeviceTokenType
import dev.feature.notifications.domain.push.PushRepository
import dev.feature.notifications.domain.push.PushTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * `POST /v1/devices` / `DELETE /v1/devices/{token}` ustidagi qatlam — bir vaqtning o'zida
 * [PushRegistrar] ham: auth qatlami sessiya ochilganda/yopilganda shuni chaqiradi.
 *
 * **Xato hech qachon tashqariga chiqmaydi.** Push — qo'shimcha qulaylik: token olinmasa
 * (ruxsat berilmagan, Play Services yo'q) yoki so'rov yiqilsa, kirish/chiqish oqimi
 * o'zgarishsiz davom etadi. Shuning uchun [PushRegistrar] metodlari `Resource` qaytarmaydi.
 */
class PushRepositoryImpl(
    private val api: NotificationsApi,
    private val tokenSource: PushTokenSource,
    private val connectivity: NetworkConnectivity,
    dispatchers: AppDispatchers,
) : PushRepository, PushRegistrar {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /** Token yangilanishini kuzatuvchi — faqat sessiya ochiq bo'lganda ishlaydi. */
    private var refreshJob: Job? = null

    override suspend fun register(): Resource<Unit> {
        // Token yo'q — bu xato emas, shunchaki push ishlamaydi (03-WEBSOCKET.md §10).
        val token = tokenSource.currentToken() ?: return Resource.Success(Unit)
        return send(token)
    }

    override suspend fun unregister(): Resource<Unit> {
        val token = tokenSource.currentToken() ?: return Resource.Success(Unit)
        return safeCall(connectivity) { api.devicesRemove(token).body() }
    }

    private suspend fun send(token: String): Resource<Unit> = safeCall(connectivity) {
        api.devicesRegister(
            RegisterDeviceDto(
                token = token,
                platform = tokenSource.platform.toDto(),
                // `tokenType` ATAYLAB aniq yuboriladi. Server berilmagan holat uchun o'z
                // sukutiga ega (`IOS → APNS`), lekin u serverning ichki qarori: o'zgarsa,
                // tarqatilgan ilova o'z tokenini uni adreslay olmaydigan xizmatga
                // topshirib qo'yardi va push jimgina o'chardi.
                tokenType = tokenSource.tokenType.toDto(),
            ),
        ).body()
    }

    // --- PushRegistrar ---------------------------------------------------------------------

    override suspend fun onSessionStarted() {
        register()
        // Token almashsa (ilova qayta o'rnatildi, server aylantirdi) — eskisiga push yetib
        // bormaydi, shuning uchun har o'zgarishda qayta yozamiz.
        refreshJob?.cancel()
        refreshJob = scope.launch {
            tokenSource.tokenRefreshes.collect { send(it) }
        }
    }

    override suspend fun onSessionEnding() {
        refreshJob?.cancel()
        refreshJob = null
        // MUHIM: tokenlar tozalanishidan OLDIN chaqiriladi, aks holda so'rov 401 bo'lardi.
        unregister()
    }

    private fun DeviceTokenType.toDto(): DeviceTokenTypeDto = when (this) {
        DeviceTokenType.FCM -> DeviceTokenTypeDto.FCM
        DeviceTokenType.APNS -> DeviceTokenTypeDto.APNS
        DeviceTokenType.APNS_VOIP -> DeviceTokenTypeDto.APNS_VOIP
    }

    private fun DevicePlatform.toDto(): DevicePlatformDto = when (this) {
        DevicePlatform.ANDROID -> DevicePlatformDto.ANDROID
        DevicePlatform.IOS -> DevicePlatformDto.IOS
        DevicePlatform.WEB -> DevicePlatformDto.WEB
    }
}
