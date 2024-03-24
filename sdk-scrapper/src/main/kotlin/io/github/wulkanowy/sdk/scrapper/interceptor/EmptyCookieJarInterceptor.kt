package io.github.wulkanowy.sdk.scrapper.interceptor

import io.github.wulkanowy.sdk.scrapper.login.NotLoggedInException
import okhttp3.Interceptor
import okhttp3.Response
import java.net.CookieStore

internal class EmptyCookieJarInterceptor(private val cookieStore: CookieStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (cookieStore.cookies.isEmpty()) {
            throw NotLoggedInException("No cookie found! You are not logged in yet")
        }

        return chain.proceed(chain.request())
    }
}
