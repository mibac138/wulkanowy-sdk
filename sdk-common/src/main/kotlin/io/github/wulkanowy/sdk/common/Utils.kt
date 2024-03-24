package io.github.wulkanowy.sdk.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient

fun OkHttpClient.Builder.addInterceptor(interceptor: Interceptor, network: Boolean) = if (network) addNetworkInterceptor(interceptor) else addInterceptor(interceptor)
