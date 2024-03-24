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
    private val keyId: String,
    private val privatePem: String,
) {

    private val interceptors: MutableList<Pair<Interceptor, Boolean>> = mutableListOf(
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
            getRetrofitBuilder(isJson = false, signInterceptor = false)
                .baseUrl("https://komponenty.vulcan.net.pl")
                .build()
                .create(),
        )
    }

    fun getStudentRepository(baseUrl: String, schoolId: String): StudentRepository = StudentRepository(
        getRetrofitBuilder(isJson = true, signInterceptor = true)
            .baseUrl("${baseUrl.removeSuffix("/")}/$schoolId/")
            .build()
            .create(),
    )

    internal fun getRegisterRepository(baseUrl: String, symbol: String = ""): RegisterRepository = getRegisterRepository(
        baseUrl = "${baseUrl.removeSuffix("/")}/$symbol",
    )

    private fun getRegisterRepository(baseUrl: String): RegisterRepository = RegisterRepository(
        getRetrofitBuilder(signInterceptor = true)
            .baseUrl("${baseUrl.removeSuffix("/")}/api/mobile/register/")
            .build()
            .create(),
    )

    private fun getRetrofitBuilder(isJson: Boolean = true, signInterceptor: Boolean): Retrofit.Builder {
        return Retrofit.Builder()
            .apply {
                when {
                    isJson -> addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    else -> addConverterFactory(ScalarsConverterFactory.create())
                }
            }
            .client(
                httpClient.newBuilder().apply {
                        if (signInterceptor) {
                            addInterceptor(SignInterceptor(keyId, privatePem))
                        }
                    interceptors.forEach { (interceptor, network) ->
                        addInterceptor(interceptor, network)
                        }
                }.build(),
            )
    }
}
