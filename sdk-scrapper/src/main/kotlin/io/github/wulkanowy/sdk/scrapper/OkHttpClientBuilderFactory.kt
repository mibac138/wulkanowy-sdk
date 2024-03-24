package io.github.wulkanowy.sdk.scrapper

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TIMEOUT_IN_SECONDS = 30L

internal class OkHttpClientBuilderFactory(host: String) {

    private val okHttpClient by lazy {
        OkHttpClient().newBuilder()
            .connectTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            .apply {
            when (host) {
                "edu.gdansk.pl",
                "edu.lublin.eu",
                "eduportal.koszalin.pl",
                "vulcan.net.pl",
                -> {
                    sslSocketFactory(TLSSocketFactory(), getTrustManager())
                }
            }
        }.build()
    }

    fun create(): OkHttpClient.Builder = okHttpClient.newBuilder()
}

private fun getTrustManager(): X509TrustManager {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(null as? KeyStore?)
    val trustManagers = trustManagerFactory.trustManagers
    if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
        throw IllegalStateException("Unexpected default trust managers: $trustManagers")
    }
    return trustManagers[0] as X509TrustManager
}
