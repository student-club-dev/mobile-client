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
 * `NET_CAPABILITY_VALIDATED` — Wi-Fi'ga ulangan, lekin haqiqiy internet yo'q holatni ham
 * to'g'ri aniqlaydi (masalan captive portal). Context Koin `androidContext()` dan keladi.
 */
actual class NetworkConnectivity(private val context: Context) {

    private val cm: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * `ACCESS_NETWORK_STATE` bo'lmasa `SecurityException` uchadi. Bu tekshiruv har API
     * so'rovi oldidan chaqiriladi — shuning uchun u hech qachon ilovani yiqitmasligi kerak:
     * holatni bilmasak, "online" deb hisoblab so'rovni o'tkazamiz (haqiqiy tarmoq xatosi
     * keyin baribir typed [AppException] ga aylanadi).
     */
    actual fun isOnline(): Boolean = try {
        val manager = cm
        val network = manager?.activeNetwork
        val caps = network?.let(manager::getNetworkCapabilities)
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: SecurityException) {
        Napier.w("ACCESS_NETWORK_STATE ruxsati yo'q — internet holati noma'lum", e)
        true
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
