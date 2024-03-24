package io.github.wulkanowy.sdk.scrapper.service

import io.github.wulkanowy.sdk.common.addInterceptor
import io.github.wulkanowy.sdk.common.interceptor.NullInterceptor
import io.github.wulkanowy.sdk.scrapper.CookieJarCabinet
import io.github.wulkanowy.sdk.scrapper.Scrapper
import io.github.wulkanowy.sdk.scrapper.adapter.ObjectSerializer
import io.github.wulkanowy.sdk.scrapper.exception.ScrapperException
import io.github.wulkanowy.sdk.scrapper.interceptor.AutoLoginInterceptor
import io.github.wulkanowy.sdk.scrapper.interceptor.EmptyCookieJarInterceptor
import io.github.wulkanowy.sdk.scrapper.interceptor.ErrorInterceptor
import io.github.wulkanowy.sdk.scrapper.interceptor.HttpErrorInterceptor
import io.github.wulkanowy.sdk.scrapper.interceptor.ModuleHeaders
import io.github.wulkanowy.sdk.scrapper.interceptor.StudentCookieInterceptor
import io.github.wulkanowy.sdk.scrapper.login.LoginHelper
import io.github.wulkanowy.sdk.scrapper.login.LoginResult
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.Interceptor
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pl.droidsonroids.retrofit2.JspoonConverterFactory
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

internal class ServiceManager(
    private val httpClient: OkHttpClient,
    private val cookieJarCabinet: CookieJarCabinet,
    loginType: Scrapper.LoginType,
    val urlGenerator: UrlGenerator,
    private val email: String,
    private val password: String,
    private val studentId: Int,
    private val diaryId: Int,
    private val kindergartenDiaryId: Int,
    schoolYear: Int,
    loginLock: ReentrantLock,
    headersByHost: MutableMap<String, ModuleHeaders>,
    emptyCookieJarIntercept: Boolean,
) {

    private val schoolYear = when (schoolYear) {
        0 -> if (LocalDate.now().monthValue < 9) LocalDate.now().year - 1 else LocalDate.now().year // fallback
        else -> schoolYear
    }

    private val loginHelper by lazy {
        LoginHelper(
            loginType = loginType,
            cookieJarCabinet = cookieJarCabinet,
            api = getLoginService(),
            urlGenerator = urlGenerator,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json by lazy {
        Json {
            explicitNulls = false
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            serializersModule = SerializersModule {
                contextual(ObjectSerializer)
            }
        }
    }

    suspend fun userLogin(): LoginResult {
        return loginHelper.login(email, password)
    }

    suspend fun loginModule(site: UrlGenerator.Site) {
        loginHelper.loginModule(site)
    }

    private val interceptors: MutableList<Pair<Interceptor, Boolean>> = mutableListOf(
        ErrorInterceptor(cookieJarCabinet) to false,
        AutoLoginInterceptor(
            loginType = loginType,
            loginLock = loginLock,
            headersByHost = headersByHost,
            notLoggedInCallback = ::userLogin,
            fetchModuleCookies = { site -> loginHelper.loginModule(site) },
        ) to false,
        (EmptyCookieJarInterceptor(cookieJarCabinet.userCookieManager.cookieStore).takeIf { emptyCookieJarIntercept } ?: NullInterceptor) to false,
        HttpErrorInterceptor() to false,
    )

    fun setInterceptor(interceptor: Interceptor, network: Boolean = false) {
        interceptors.add(0, interceptor to network)
    }

    fun getSymbolService(): SymbolService {
        return getRetrofit(
            client = getClientBuilder(errIntercept = true, loginIntercept = false, separateJar = true),
            baseUrl = urlGenerator.generateBase(UrlGenerator.Site.HOME).toString(),
            json = false,
        ).create()
    }

    fun getLoginService(): LoginService {
        if (email.isBlank() && password.isBlank()) throw ScrapperException("Email and password are not set")
        if (email.isBlank()) throw ScrapperException("Email is not set")
        if (password.isBlank()) throw ScrapperException("Password is not set")
        return getRetrofit(getClientBuilder(loginIntercept = false), urlGenerator.generateWithSymbol(UrlGenerator.Site.LOGIN), false).create()
    }

    fun getAccountService(): AccountService {
        return getRetrofit(
            client = getClientBuilder(errIntercept = false, loginIntercept = false, separateJar = true),
            baseUrl = urlGenerator.generateWithSymbol(UrlGenerator.Site.LOGIN),
            json = false,
        ).create()
    }

    fun getRegisterService(): RegisterService {
        return getRetrofit(
            client = getClientBuilder(errIntercept = true, loginIntercept = false, separateJar = true),
            baseUrl = urlGenerator.generateWithSymbol(UrlGenerator.Site.LOGIN),
            json = false,
        ).create()
    }

    fun getStudentService(withLogin: Boolean = true, studentInterceptor: Boolean = true): StudentService {
        return getRetrofit(
            client = prepareStudentHttpClient(withLogin, studentInterceptor),
            baseUrl = urlGenerator.generateWithSymbol(UrlGenerator.Site.STUDENT),
            json = true,
        ).create()
    }

    fun getStudentPlusService(withLogin: Boolean = true): StudentPlusService {
        return getRetrofit(
            client = getClientBuilder(loginIntercept = withLogin),
            baseUrl = urlGenerator.generateWithSymbol(UrlGenerator.Site.STUDENT_PLUS),
            json = true,
        ).create()
    }

    private fun prepareStudentHttpClient(withLogin: Boolean, studentInterceptor: Boolean): OkHttpClient {
        if (withLogin && urlGenerator.schoolId.isBlank()) throw ScrapperException("School id is not set")

        return getClientBuilder(loginIntercept = withLogin) {
            if (studentInterceptor) {
                if ((0 == diaryId && 0 == kindergartenDiaryId) || 0 == studentId) throw ScrapperException("Student or/and diaryId id are not set")

                addInterceptor(
                    StudentCookieInterceptor(
                        cookieStore = cookieJarCabinet.userCookieManager.cookieStore,
                        urlGenerator = urlGenerator,
                        diaryId = diaryId,
                        kindergartenDiaryId = kindergartenDiaryId,
                        studentId = studentId,
                        schoolYear = schoolYear,
                    ),
                )
            }
        }
    }

    fun getMessagesService(withLogin: Boolean = true): MessagesService {
        return getRetrofit(
            client = getClientBuilder(loginIntercept = withLogin),
            baseUrl = urlGenerator.generateWithSymbol(UrlGenerator.Site.MESSAGES),
            json = true,
        ).create()
    }

    fun getHomepageService(): HomepageService {
        return getRetrofit(getClientBuilder(), urlGenerator.generateWithSymbol(UrlGenerator.Site.HOME), json = true).create()
    }

    private fun getRetrofit(client: OkHttpClient, baseUrl: String, json: Boolean = false) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(
            when {
                json -> this.json.asConverterFactory("application/json".toMediaType())
                else -> JspoonConverterFactory.create()
            },
        )
        .build()

    private fun getClientBuilder(
        errIntercept: Boolean = true,
        loginIntercept: Boolean = true,
        separateJar: Boolean = false,
        block: OkHttpClient.Builder.() -> Unit = {},
    ) = httpClient.newBuilder()
        .cookieJar(
            when {
                separateJar -> JavaNetCookieJar(cookieJarCabinet.alternativeCookieManager)
                else -> JavaNetCookieJar(cookieJarCabinet.userCookieManager)
            },
        )
        .apply {
            interceptors.forEach { (interceptor, network) ->
                when (interceptor) {
                    is ErrorInterceptor -> if (errIntercept) addInterceptor(interceptor, network)
                    is AutoLoginInterceptor -> if (loginIntercept) addInterceptor(interceptor, network)
                    else -> addInterceptor(interceptor, network)
                }
            }
        }
        .also(block)
        .build()
}
