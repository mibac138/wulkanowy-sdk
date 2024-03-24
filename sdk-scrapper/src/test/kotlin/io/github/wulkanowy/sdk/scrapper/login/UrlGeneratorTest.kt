package io.github.wulkanowy.sdk.scrapper.login

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlGeneratorTest {
    private val generator = UrlGenerator(schema = "https", host = "host", port = null, domainSuffix = "domainSuffix", symbol = "symbol", schoolId = "schoolId")

    @Test
    fun test() {
        assertEquals("https://host", generator.getReferenceUrl())
        assertEquals("https://uonetplus-uczendomainsuffix.host/", generator.generateBase(UrlGenerator.Site.STUDENT).toString())
        assertEquals("https://uonetplus-uczendomainsuffix.host/symbol/schoolId/", generator.generateWithSymbol(UrlGenerator.Site.STUDENT))
    }
}
