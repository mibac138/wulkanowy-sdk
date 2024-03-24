package io.github.wulkanowy.sdk.scrapper.interceptor

import okhttp3.Interceptor

internal object NullInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(chain.request())
}
