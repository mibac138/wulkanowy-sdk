package io.github.wulkanowy.sdk.scrapper.login

import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.LOGIN
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.STUDENT
import okhttp3.HttpUrl
import java.net.URL

internal class UrlGenerator(
    private val schema: String,
    private val host: String,
    private val domainSuffix: String,
    var symbol: String,
    var schoolId: String,
) {

    constructor(url: URL, domainSuffix: String, symbol: String, schoolId: String) : this(url.protocol, url.host, domainSuffix, symbol, schoolId)

    enum class Site(internal val subDomain: String) {
        LOGIN("cufs"),
        HOME("uonetplus"),
        STUDENT("uonetplus-uczen"),
        STUDENT_PLUS("uonetplus-uczenplus"),
        MESSAGES("uonetplus-wiadomosciplus");

        val isStudent: Boolean
            get() = this == STUDENT_PLUS || this == STUDENT
    }

    companion object {
        val EMPTY = UrlGenerator("https", "fakelog.cf", "", "powiatwulkanowy", "")
    }

    fun getReferenceUrl() = "$schema://$host"

    fun generateWithSymbol(type: Site): String {
        return generateBase(type).newBuilder().addPathSegment(symbol).also {
            if (type.isStudent) it.addPathSegment(schoolId)
        }.toString()
    }

    fun generateBase(type: Site) = HttpUrl.Builder().scheme(schema).host("${type.subDomain}$domainSuffix.$host").build()

    fun createReferer(type: Site): String {
        return when (type) {
            LOGIN -> "$schema://cufs$domainSuffix.$host/"
            STUDENT -> "$schema://uonetplus$domainSuffix.$host/"
            else -> ""
        }
    }
}
