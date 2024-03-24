package io.github.wulkanowy.sdk.common.interceptor

import okhttp3.Interceptor

object NullInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(chain.request())
}
