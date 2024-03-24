package io.github.wulkanowy.sdk.hebe.interceptor

import okhttp3.Interceptor
import okhttp3.Response

internal class DeviceModelInterceptor(private val deviceModel: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
        req.header("User-Agent", "Dart/2.10 (dart:io)")
        req.header("vOS", "Android")
        req.header("vDeviceModel", deviceModel)
        req.header("vAPI", "1")
        return chain.proceed(req.build())
    }
}
