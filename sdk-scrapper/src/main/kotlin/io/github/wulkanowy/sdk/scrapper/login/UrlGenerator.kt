package io.github.wulkanowy.sdk.scrapper.login

import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.HOME
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.LOGIN
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.MESSAGES
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.STUDENT
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.STUDENT_PLUS
import java.net.URL

internal class UrlGenerator(
    private val schema: String,
    private val host: String,
    private val domainSuffix: String,
    var symbol: String,
    var schoolId: String,
) {

    constructor(url: URL, domainSuffix: String, symbol: String, schoolId: String) : this(url.protocol, url.host, domainSuffix, symbol, schoolId)

    enum class Site {
        LOGIN,
        HOME,
        STUDENT,
        STUDENT_PLUS,
        MESSAGES,
        ;

        val isStudent: Boolean
            get() = this == STUDENT_PLUS || this == STUDENT
    }

    companion object {
        val EMPTY = UrlGenerator("https", "fakelog.cf", "", "powiatwulkanowy", "")
    }

    fun getReferenceUrl() = "$schema://$host"

    fun generate(type: Site): String {
        return "${generateBase(type).removeSuffix("/")}/$symbol/${if (type.isStudent) "$schoolId/" else ""}"
    }

    fun generateBase(type: Site): String {
        return "$schema://${getSubDomain(type)}$domainSuffix.$host/"
    }

    fun createReferer(type: Site): String {
        return when (type) {
            LOGIN -> "$schema://cufs$domainSuffix.$host/"
            STUDENT -> "$schema://uonetplus$domainSuffix.$host/"
            else -> ""
        }
    }

    private fun getSubDomain(type: Site): String {
        return when (type) {
            LOGIN -> "cufs"
            HOME -> "uonetplus"
            STUDENT -> "uonetplus-uczen"
            STUDENT_PLUS -> "uonetplus-uczenplus"
            MESSAGES -> "uonetplus-wiadomosciplus"
        }
    }
}
