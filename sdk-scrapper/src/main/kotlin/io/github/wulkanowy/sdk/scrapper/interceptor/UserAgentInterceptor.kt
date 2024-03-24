package io.github.wulkanowy.sdk.scrapper.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * @see <a href="https://github.com/jhy/jsoup/blob/220b77140bce70dcf9c767f8f04758b09097db14/src/main/java/org/jsoup/helper/HttpConnection.java#L59">JSoup default user agent</a>
 * @see <a href="https://developer.chrome.com/multidevice/user-agent#chrome_for_android_user_agent">User Agent Strings - Google Chrome</a>
 */
internal class UserAgentInterceptor(
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder().addHeader("User-Agent", userAgent).build(),
    )
}
