package dev.core.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android internet holati — `ConnectivityManager`.
 *
 * Context Koin `androidContext()` dan keladi.
 *
 * ⚠️ `NET_CAPABILITY_VALIDATED` **talab qilinmaydi**. Tizim uni tarmoqqa ulangandan keyin
 * o'zining tekshiruv so'rovi qaytgach qo'yadi — bu bir necha soniya davom etadi va aynan
 * shu oynaga ilovaning ilk so'rovlari tushardi. Natijada har ochilishda "Internet aloqasi
 * yo'q" chiqib, hech qanday ma'lumot yuklanmasdi (mobil internetda deyarli har safar).
 *
 * Endi "tarmoq bor" degani INTERNET imkoniyati e'lon qilingan faol tarmoq bo'lishi. Tarmoq
 * yolg'onchi bo'lsa (captive portal) so'rovning O'ZI yiqiladi va `toAppException` uni
 * baribir [dev.core.common.error.AppException.NoInternet] ga aylantiradi — ya'ni to'g'ri
 * xabar yo'qolmaydi, faqat endi u taxminga emas, haqiqiy urinishga asoslanadi.
 */
actual class NetworkConnectivity(private val context: Context) {

    private val cm: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * `ACCESS_NETWORK_STATE` bo'lmasa `SecurityException` uchadi. Bu tekshiruv har API
     * so'rovi oldidan chaqiriladi — shuning uchun u hech qachon ilovani yiqitmasligi kerak:
     * holatni bilmasak, "online" deb hisoblab so'rovni o'tkazamiz (haqiqiy tarmoq xatosi
     * keyin baribir typed [dev.core.common.error.AppException] ga aylanadi).
     */
    actual fun isOnline(): Boolean {
        return try {
            // Xizmat yoki imkoniyatlar o'qilmasa — bilmaymiz, so'rovni TO'SMAYMIZ.
            val manager = cm ?: return true
            // Faol tarmoqning umuman yo'qligi ISHONCHLI belgi (aviarejim, Wi-Fi o'chirilgan).
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return true
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: SecurityException) {
            Napier.w("ACCESS_NETWORK_STATE ruxsati yo'q — internet holati noma'lum", e)
            true
        }
    }

    actual val online: Flow<Boolean> = callbackFlow {
        val manager = cm
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline())
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(isOnline())
            }
        }
        trySend(isOnline())
        val registered = try {
            manager.registerDefaultNetworkCallback(callback)
            true
        } catch (e: SecurityException) {
            Napier.w("Tarmoq kuzatuvchisini ro'yxatdan o'tkazib bo'lmadi", e)
            false
        }
        awaitClose { if (registered) manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
