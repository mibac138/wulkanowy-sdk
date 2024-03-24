package io.github.wulkanowy.sdk.scrapper.repository

import io.github.wulkanowy.sdk.scrapper.BaseLocalTest
import io.github.wulkanowy.sdk.scrapper.Scrapper
import io.github.wulkanowy.sdk.scrapper.login.LoginTest
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator
import io.github.wulkanowy.sdk.scrapper.messages.MessagesTest
import io.github.wulkanowy.sdk.scrapper.register.RegisterTest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextStudentStartRepositoryTest : BaseLocalTest() {

    private fun createApi(studentId: Int, classId: Int, loginType: Scrapper.LoginType = Scrapper.LoginType.STANDARD): Scrapper {
        return Scrapper(
            urlGenerator = UrlGenerator(schema = "http", host = "fakelog.localhost", port = 3000, symbol = "Default", schoolId = "123456"),
            httpClient = OkHttpClient().newBuilder().addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)).build(),
            loginType = loginType,
            email = "jan@fakelog.cf",
            password = "jan123",
            diaryId = 101,
            studentId = studentId,
            classId = classId,
        )
    }

    @Test
    fun getSemesters() {
        with(server) {
            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(1, 1)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(6, semesters.size)

        assertEquals(1234568, semesters[0].semesterId)
        assertEquals(1234567, semesters[1].semesterId)
        assertEquals(2018, semesters[0].schoolYear)
        assertEquals(2018, semesters[1].schoolYear)

        assertEquals(1234566, semesters[2].semesterId)
        assertEquals(2017, semesters[2].schoolYear)
        assertEquals(2017, semesters[3].schoolYear)
    }

    @Test
    fun getSemesters_empty() {
        with(server) {
            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(1, 2)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(0, semesters.size)
    }

    @Test
    fun getSemesters_studentWithMultiClasses() {
        with(server) {
            enqueue("UczenDziennik-multi.json", RegisterTest::class.java)
            start(3000)
        }

        val api = createApi(3881, 121)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(2, semesters.size)

        assertEquals(714, semesters[0].semesterId)
        assertEquals(713, semesters[1].semesterId)
    }

    @Test
    fun getSemesters_graduate() {
        with(server) {
            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(2, 2)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(6, semesters.size)

        assertEquals(1234568, semesters[0].semesterId)
        assertEquals(1234567, semesters[1].semesterId)
    }

    @Test
    fun getSemesters_normal() {
        with(server) {
            enqueue("Logowanie-standard.html", LoginTest::class.java)

            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success.html", LoginTest::class.java)

            enqueue("Start.html", MessagesTest::class.java)
            enqueue("WitrynaUcznia.html", RegisterTest::class.java)

            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(1, 1)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(6, semesters.size)

        assertEquals(1234568, semesters[0].semesterId)
        assertEquals(1234567, semesters[1].semesterId)
    }

    @Test
    fun getSemesters_ADFS() {
        with(server) {
            enqueue("ADFS.html", LoginTest::class.java) //

            enqueue("Logowanie-cufs.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success.html", LoginTest::class.java)

            enqueue("Start.html", MessagesTest::class.java)
            enqueue("WitrynaUcznia.html", RegisterTest::class.java)

            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(1, 1, loginType = Scrapper.LoginType.ADFS)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(6, semesters.size)

        assertEquals(1234568, semesters[0].semesterId)
        assertEquals(1234567, semesters[1].semesterId)
    }

    @Test
    fun getSemesters_ADFSLight() {
        with(server) {
            enqueue("ADFSLight-form-1.html", LoginTest::class.java)

            enqueue("Logowanie-cufs.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success.html", LoginTest::class.java)

            enqueue("Start.html", MessagesTest::class.java)
            enqueue("WitrynaUcznia.html", RegisterTest::class.java)

            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(1, 1, Scrapper.LoginType.ADFSLight)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(6, semesters.size)

        assertEquals(1234568, semesters[0].semesterId)
        assertEquals(1234567, semesters[1].semesterId)
    }

    @Test
    fun getSemesters_ADFSCards() {
        with(server) {
            enqueue("unknown-error.txt", RegisterTest::class.java)

            enqueue("ADFSCards.html", LoginTest::class.java)
            enqueue("Logowanie-cufs.html", LoginTest::class.java)
            enqueue("Logowanie-uonet.html", LoginTest::class.java)
            enqueue("Login-success.html", LoginTest::class.java)

            enqueue("Start.html", MessagesTest::class.java)
            enqueue("WitrynaUcznia.html", RegisterTest::class.java)

            enqueue("UczenDziennik.json", RegisterTest::class.java)
            start(3000) //
        }

        val api = createApi(1, 1, Scrapper.LoginType.ADFSCards)

        val semesters = runBlocking { api.getSemesters() }

        assertEquals(6, semesters.size)

        assertEquals(1234568, semesters[0].semesterId)
        assertEquals(1234567, semesters[1].semesterId)
        assertEquals(2018, semesters[0].schoolYear)
        assertEquals(2018, semesters[1].schoolYear)
    }
}
