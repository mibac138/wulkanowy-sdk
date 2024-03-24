package io.github.wulkanowy.sdk.scrapper.interceptor

import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator
import okhttp3.Interceptor
import okhttp3.Response
import java.net.CookieStore
import java.net.HttpCookie

internal class StudentCookieInterceptor(
    private val cookieStore: CookieStore,
    private val urlGenerator: UrlGenerator,
    diaryId: Int,
    kindergartenDiaryId: Int,
    studentId: Int,
    schoolYear: Int,
) : Interceptor {

    private val cookiesData = arrayOf(
        "idBiezacyDziennik" to diaryId,
        "idBiezacyUczen" to studentId,
        "idBiezacyDziennikPrzedszkole" to kindergartenDiaryId,
        "biezacyRokSzkolny" to schoolYear,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        cookiesData.forEach { (name, value) ->
            val url = urlGenerator.generateBase(UrlGenerator.Site.STUDENT)
            HttpCookie(name, value.toString()).let {
                it.path = "/"
                it.domain = url.host
                cookieStore.add(url.toUri(), it)
            }
        }

        // This is probably used to refresh the cookies in the request (after setting them through cookieJarCabinet above)
        return chain.proceed(chain.request().newBuilder().build())
    }
}
