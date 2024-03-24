package io.github.wulkanowy.sdk.scrapper.login

import io.github.wulkanowy.sdk.scrapper.getNormalizedSymbol
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.LOGIN
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator.Site.STUDENT
import okhttp3.HttpUrl
import java.net.URL

class UrlGenerator(
    val schema: String,
    val host: String,
    val port: Int? = null,
    val domainSuffix: String = "",
    symbol: String = "",
    val schoolId: String = "",
) {

    val symbol: String = symbol.getNormalizedSymbol()

    constructor(url: URL, domainSuffix: String, symbol: String, schoolId: String) : this(
        url.protocol,
        url.host,
        url.port.takeUnless { it == -1 },
        domainSuffix,
        symbol,
        schoolId,
    )

    enum class Site(internal val subDomain: String) {
        LOGIN("cufs"), HOME("uonetplus"), STUDENT("uonetplus-uczen"), STUDENT_PLUS("uonetplus-uczenplus"), MESSAGES("uonetplus-wiadomosciplus");

        val isStudent: Boolean
            get() = this == STUDENT_PLUS || this == STUDENT
    }

    companion object {
        @JvmStatic
        val Empty = UrlGenerator("https", "fakelog.cf", null, "", "powiatwulkanowy", "")
    }

    fun getReferenceUrl() = "$schema://$host"

    fun generateWithSymbol(type: Site): String {
        return generateBase(type).newBuilder().addPathSegment(symbol).also {
            if (type.isStudent) it.addPathSegment(schoolId)
        }.addPathSegment("").toString()
    }

    fun generateBase(type: Site) = HttpUrl.Builder()
        .scheme(schema)
        .host("${type.subDomain}$domainSuffix.$host")
        .also {
            port?.let { port ->
                it.port(port)
            }
        }
        .build()

    fun createReferer(type: Site): String {
        return when (type) {
            LOGIN -> "$schema://cufs$domainSuffix.$host/"
            STUDENT -> "$schema://uonetplus$domainSuffix.$host/"
            else -> ""
        }
    }

    // Manual copy constructor because this cant be a data class - for that all constructor parameters would have to be properties, but then we couldn't normalize the symbol
    fun copy(
        schema: String = this.schema,
        host: String = this.host,
        port: Int? = this.port,
        domainSuffix: String = this.domainSuffix,
        symbol: String = this.symbol,
        schoolId: String = this.schoolId,
    ) = UrlGenerator(schema, host, port, domainSuffix, symbol, schoolId)

    override fun toString(): String {
        return "UrlGenerator(schema='$schema', host='$host', port=$port, domainSuffix='$domainSuffix', schoolId='$schoolId', symbol='$symbol')"
    }
}
