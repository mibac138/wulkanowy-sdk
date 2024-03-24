package io.github.wulkanowy.sdk.scrapper.interceptor

import io.github.wulkanowy.sdk.scrapper.CookieJarCabinet
import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpCookie
import java.net.URI

internal class StudentCookieInterceptor(
    private val cookieJarCabinet: CookieJarCabinet,
    private val schema: String,
    private val host: String,
    private val domainSuffix: String,
    private val diaryId: Int,
    private val kindergartenDiaryId: Int,
    private val studentId: Int,
    private val schoolYear: Int,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        arrayOf(
            "idBiezacyDziennik" to diaryId,
            "idBiezacyUczen" to studentId,
            "idBiezacyDziennikPrzedszkole" to kindergartenDiaryId,
            "biezacyRokSzkolny" to schoolYear,
        ).forEach { (name, value) ->
            HttpCookie(name, value.toString()).let {
                it.path = "/"
                it.domain = "uonetplus-uczen$domainSuffix.$host"
                cookieJarCabinet.addStudentCookie(URI("$schema://${it.domain}"), it)
            }
        }

        // This is probably used to refresh the cookies in the request (after setting them through cookieJarCabinet above)
        return chain.proceed(chain.request().newBuilder().build())
    }
}
