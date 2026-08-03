package dev.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

// iOS'da debug interceptor'lar yo'q (ular OkHttp tushunchasi) — parametr e'tiborsiz.
actual fun platformHttpClient(
    debugInterceptors: Boolean,
    config: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(Darwin, config)
