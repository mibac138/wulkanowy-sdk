package io.github.wulkanowy.sdk.hebe.repository

import io.github.wulkanowy.sdk.common.addInterceptor
import io.github.wulkanowy.sdk.hebe.interceptor.ErrorInterceptor
import io.github.wulkanowy.sdk.hebe.interceptor.SignInterceptor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create

internal class RepositoryManager(
    private val httpClient: OkHttpClient,
    keyId: String,
    privatePem: String,
) {

    private val interceptors: MutableList<Pair<Interceptor, Boolean>> = mutableListOf(
        SignInterceptor(keyId, privatePem) to false,
        ErrorInterceptor() to false,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private val json by lazy {
        Json {
            explicitNulls = false
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }

    fun getRoutesRepository(): RoutingRulesRepository {
        return RoutingRulesRepository(
            getRetrofitBuilder(getClientBuilder(signInterceptor = false), baseUrl = "https://komponenty.vulcan.net.pl", json = false).create(),
        )
    }

    fun getStudentRepository(baseUrl: String, schoolId: String): StudentRepository = StudentRepository(
        getRetrofitBuilder(getClientBuilder(), "${baseUrl.removeSuffix("/")}/$schoolId/").create(),
    )

    internal fun getRegisterRepository(baseUrl: String, symbol: String = ""): RegisterRepository = getRegisterRepository(
        baseUrl = "${baseUrl.removeSuffix("/")}/$symbol",
    )

    private fun getRegisterRepository(baseUrl: String): RegisterRepository = RegisterRepository(
        getRetrofitBuilder(getClientBuilder(), "${baseUrl.removeSuffix("/")}/api/mobile/register/").create(),
    )

    private fun getRetrofitBuilder(client: OkHttpClient, baseUrl: String, json: Boolean = true) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            when {
                json -> this.json.asConverterFactory("application/json".toMediaType())
                else -> ScalarsConverterFactory.create()
            },
        )
        .client(client)
        .build()

    private fun getClientBuilder(
        signInterceptor: Boolean = true,
    ) = httpClient.newBuilder()
        .apply {
            interceptors.forEach { (interceptor, network) ->
                when (interceptor) {
                    is SignInterceptor -> if (signInterceptor) addInterceptor(interceptor, network)
                    else -> addInterceptor(interceptor, network)
                }
            }
        }
        .build()

}
