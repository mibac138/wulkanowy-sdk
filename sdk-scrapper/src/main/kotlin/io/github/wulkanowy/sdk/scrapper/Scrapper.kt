package io.github.wulkanowy.sdk.scrapper

import io.github.wulkanowy.sdk.common.exception.UnavailableFeatureException
import io.github.wulkanowy.sdk.scrapper.attendance.Absent
import io.github.wulkanowy.sdk.scrapper.attendance.Attendance
import io.github.wulkanowy.sdk.scrapper.attendance.AttendanceSummary
import io.github.wulkanowy.sdk.scrapper.attendance.Subject
import io.github.wulkanowy.sdk.scrapper.conferences.Conference
import io.github.wulkanowy.sdk.scrapper.exams.Exam
import io.github.wulkanowy.sdk.scrapper.exception.ScrapperException
import io.github.wulkanowy.sdk.scrapper.grades.GradePointsSummary
import io.github.wulkanowy.sdk.scrapper.grades.Grades
import io.github.wulkanowy.sdk.scrapper.grades.GradesStatisticsPartial
import io.github.wulkanowy.sdk.scrapper.grades.GradesStatisticsSemester
import io.github.wulkanowy.sdk.scrapper.home.DirectorInformation
import io.github.wulkanowy.sdk.scrapper.home.GovernmentUnit
import io.github.wulkanowy.sdk.scrapper.home.LastAnnouncement
import io.github.wulkanowy.sdk.scrapper.home.LuckyNumber
import io.github.wulkanowy.sdk.scrapper.homework.Homework
import io.github.wulkanowy.sdk.scrapper.interceptor.ModuleHeaders
import io.github.wulkanowy.sdk.scrapper.interceptor.UserAgentInterceptor
import io.github.wulkanowy.sdk.scrapper.login.LoginHelper
import io.github.wulkanowy.sdk.scrapper.login.UrlGenerator
import io.github.wulkanowy.sdk.scrapper.menu.Menu
import io.github.wulkanowy.sdk.scrapper.messages.Folder
import io.github.wulkanowy.sdk.scrapper.messages.Mailbox
import io.github.wulkanowy.sdk.scrapper.messages.MessageDetails
import io.github.wulkanowy.sdk.scrapper.messages.MessageMeta
import io.github.wulkanowy.sdk.scrapper.messages.MessageReplayDetails
import io.github.wulkanowy.sdk.scrapper.messages.Recipient
import io.github.wulkanowy.sdk.scrapper.mobile.Device
import io.github.wulkanowy.sdk.scrapper.mobile.TokenResponse
import io.github.wulkanowy.sdk.scrapper.notes.Note
import io.github.wulkanowy.sdk.scrapper.register.RegisterStudent
import io.github.wulkanowy.sdk.scrapper.register.RegisterUser
import io.github.wulkanowy.sdk.scrapper.register.Semester
import io.github.wulkanowy.sdk.scrapper.repository.AccountRepository
import io.github.wulkanowy.sdk.scrapper.repository.HomepageRepository
import io.github.wulkanowy.sdk.scrapper.repository.MessagesRepository
import io.github.wulkanowy.sdk.scrapper.repository.RegisterRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentPlusRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentRepository
import io.github.wulkanowy.sdk.scrapper.repository.StudentStartRepository
import io.github.wulkanowy.sdk.scrapper.repository.SymbolRepository
import io.github.wulkanowy.sdk.scrapper.school.School
import io.github.wulkanowy.sdk.scrapper.school.Teacher
import io.github.wulkanowy.sdk.scrapper.service.ServiceManager
import io.github.wulkanowy.sdk.scrapper.student.StudentInfo
import io.github.wulkanowy.sdk.scrapper.student.StudentPhoto
import io.github.wulkanowy.sdk.scrapper.timetable.CompletedLesson
import io.github.wulkanowy.sdk.scrapper.timetable.Timetable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.CookieManager
import java.security.KeyStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal val httpClientWithBasicLogging = OkHttpClient().newBuilder().addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)).build()

class Scrapper(
    val urlGenerator: UrlGenerator = UrlGenerator.Empty,
    val userAgent: String = androidUserAgentString(),
    httpClient: OkHttpClient = httpClientWithBasicLogging,
    loginType: LoginType = LoginType.AUTO,
    email: String = "",
    password: String = "",
    private val studentId: Int = 0,
    private val classId: Int = 0,
    private val diaryId: Int = 0,
    private val unitId: Int = 0,
    kindergartenDiaryId: Int = 0,
    schoolYear: Int = 0,
    emptyCookieJarInterceptor: Boolean = false,
) {

    // TODO: refactor
    enum class LoginType {
        AUTO,
        STANDARD,
        ADFS,
        ADFSCards,
        ADFSLight,
        ADFSLightScoped,
        ADFSLightCufs,
    }

    private val cookieJarCabinet = CookieJarCabinet()

    var isEduOne = false

    private val headersByHost: MutableMap<String, ModuleHeaders> = mutableMapOf()
    private val loginLock = ReentrantLock(true)
    private val serviceManager = ServiceManager(
        httpClient = httpClient.configureForScrapper(urlGenerator.host, userAgent),
        cookieJarCabinet = cookieJarCabinet,
        loginType = loginType,
        urlGenerator = urlGenerator,
        email = email,
        password = password,
        studentId = studentId,
        diaryId = diaryId,
        kindergartenDiaryId = kindergartenDiaryId,
        schoolYear = schoolYear,
        emptyCookieJarIntercept = emptyCookieJarInterceptor,
        loginLock = loginLock,
        headersByHost = headersByHost,
    )

    private val symbolRepository by lazy { SymbolRepository(serviceManager.getSymbolService()) }

    private val account by lazy { AccountRepository(serviceManager.getAccountService()) }

    private val register = RegisterRepository(
        startSymbol = urlGenerator.symbol,
        email = email,
        password = password,
        loginHelperFactory = { urlGenerator ->
            LoginHelper(
                loginType = loginType,
                cookieJarCabinet = cookieJarCabinet,
                api = serviceManager.getLoginService(),
                urlGenerator = urlGenerator,
            )
        },
        register = serviceManager.getRegisterService(),
        student = serviceManager.getStudentService(withLogin = false, studentInterceptor = false),
        studentPlus = serviceManager.getStudentPlusService(withLogin = false),
        symbolService = serviceManager.getSymbolService(),
        baseUrlGenerator = serviceManager.urlGenerator,
    )

    private val studentStart by lazy {
        // Lazy to avoid throwing exceptions about no student id (or other ids) set when just using the register functionality (only just searching for users)
        if (0 == studentId) throw ScrapperException("Student id is not set")
        if (0 == classId && 0 == kindergartenDiaryId) throw ScrapperException("Class id is not set")
        StudentStartRepository(
            studentId = studentId,
            classId = classId,
            unitId = unitId,
            api = serviceManager.getStudentService(withLogin = true, studentInterceptor = false),
            forceSignIn = { serviceManager.loginModule(UrlGenerator.Site.STUDENT) },
        )
    }

    private val student by lazy {
        // Lazy to avoid throwing exceptions about no student id (or other ids) set when just using the register functionality (only just searching for users)
        StudentRepository(
            api = serviceManager.getStudentService(),
            forceSignIn = { serviceManager.loginModule(UrlGenerator.Site.STUDENT) },
        )
    }

    private val studentPlus = StudentPlusRepository(
        api = serviceManager.getStudentPlusService(),
    )

    private val messages = MessagesRepository(
        api = serviceManager.getMessagesService(),
        forceSignIn = { serviceManager.loginModule(UrlGenerator.Site.MESSAGES) },
    )

    private val homepage = HomepageRepository(serviceManager.getHomepageService())

    fun setAdditionalCookieManager(cookieManager: CookieManager) {
        cookieJarCabinet.setAdditionalCookieManager(cookieManager)
    }

    // Unauthorized

    suspend fun isSymbolNotExist(symbol: String): Boolean = symbolRepository.isSymbolNotExist(symbol)

    suspend fun getPasswordResetCaptcha(registerBaseUrl: String, symbol: String): Pair<String, String> =
        account.getPasswordResetCaptcha(registerBaseUrl, urlGenerator.domainSuffix, symbol)

    suspend fun sendPasswordResetRequest(registerBaseUrl: String, symbol: String, email: String, captchaCode: String): String {
        return account.sendPasswordResetRequest(registerBaseUrl, urlGenerator.domainSuffix, symbol, email.trim(), captchaCode)
    }

    suspend fun getUserSubjects(): RegisterUser = register.getUserSubjects()

    // AUTHORIZED - student

    suspend fun getCurrentStudent(): RegisterStudent? {
        val loginResult = serviceManager.userLogin()
        return when (loginResult.isStudentSchoolUseEduOne) {
            true -> studentPlus.getStudent(studentId, unitId)
            else -> studentStart.getStudent(studentId, unitId)
        }
    }

    suspend fun authorizePermission(pesel: String): Boolean = when (isEduOne) {
        true -> studentPlus.authorizePermission(pesel, studentId, diaryId, unitId)
        else -> student.authorizePermission(pesel)
    }

    suspend fun getSemesters(): List<Semester> = when (isEduOne) {
        true -> studentPlus.getSemesters(studentId, diaryId, unitId)
        else -> studentStart.getSemesters()
    }

    suspend fun getAttendance(startDate: LocalDate, endDate: LocalDate? = null): List<Attendance> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> studentPlus.getAttendance(startDate, endDate, studentId, diaryId, unitId)
            else -> student.getAttendance(startDate, endDate)
        }
    }

    suspend fun getAttendanceSummary(subjectId: Int? = -1): List<AttendanceSummary> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> studentPlus.getAttendanceSummary(studentId, diaryId, unitId)
            else -> student.getAttendanceSummary(subjectId)
        }
    }

    suspend fun excuseForAbsence(absents: List<Absent>, content: String? = null): Boolean {
        return when (isEduOne) {
            true -> studentPlus.excuseForAbsence(absents, content, studentId, diaryId, unitId)
            else -> student.excuseForAbsence(absents, content)
        }
    }

    suspend fun getSubjects(): List<Subject> = when (isEduOne) {
        true -> listOf(Subject())
        else -> student.getSubjects()
    }

    suspend fun getExams(startDate: LocalDate, endDate: LocalDate? = null): List<Exam> {
        if (diaryId == 0) return emptyList()
        return when (isEduOne) {
            true -> studentPlus.getExams(startDate, endDate, studentId, diaryId, unitId)
            else -> student.getExams(startDate, endDate)
        }
    }

    suspend fun getGrades(semester: Int): Grades {
        if (diaryId == 0) {
            return Grades(
                details = emptyList(),
                summary = emptyList(),
                descriptive = emptyList(),
                isAverage = false,
                isPoints = false,
                isForAdults = false,
                type = -1,
            )
        }
        return when (isEduOne) {
            true -> studentPlus.getGrades(semester, studentId, diaryId, unitId)
            else -> student.getGrades(semester)
        }
    }

    suspend fun getGradesPartialStatistics(semesterId: Int): List<GradesStatisticsPartial> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> throw UnavailableFeatureException()
            else -> student.getGradesPartialStatistics(semesterId)
        }
    }

    suspend fun getGradesPointsStatistics(semesterId: Int): List<GradePointsSummary> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> throw UnavailableFeatureException()
            else -> student.getGradesPointsStatistics(semesterId)
        }
    }

    suspend fun getGradesSemesterStatistics(semesterId: Int): List<GradesStatisticsSemester> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> throw UnavailableFeatureException()
            else -> student.getGradesAnnualStatistics(semesterId)
        }
    }

    suspend fun getHomework(startDate: LocalDate, endDate: LocalDate? = null): List<Homework> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> studentPlus.getHomework(startDate, endDate, studentId, diaryId, unitId)
            else -> student.getHomework(startDate, endDate)
        }
    }

    suspend fun getNotes(): List<Note> = when (isEduOne) {
        true -> studentPlus.getNotes(studentId, diaryId, unitId)
        else -> student.getNotes()
    }

    suspend fun getConferences(): List<Conference> = when (isEduOne) {
        true -> studentPlus.getConferences(studentId, diaryId, unitId)
        else -> student.getConferences()
    }

    suspend fun getMenu(date: LocalDate): List<Menu> = when (isEduOne) {
        true -> TODO()
        else -> student.getMenu(date)
    }

    suspend fun getTimetable(startDate: LocalDate, endDate: LocalDate? = null): Timetable {
        if (diaryId == 0) {
            return Timetable(
                headers = emptyList(),
                lessons = emptyList(),
                additional = emptyList(),
            )
        }

        return when (isEduOne) {
            true -> studentPlus.getTimetable(startDate, endDate, studentId, diaryId, unitId)
            else -> student.getTimetable(startDate, endDate)
        }
    }

    suspend fun getCompletedLessons(startDate: LocalDate, endDate: LocalDate? = null, subjectId: Int = -1): List<CompletedLesson> {
        if (diaryId == 0) return emptyList()

        return when (isEduOne) {
            true -> studentPlus.getCompletedLessons(startDate, endDate, studentId, diaryId, unitId)
            else -> student.getCompletedLessons(startDate, endDate, subjectId)
        }
    }

    suspend fun getRegisteredDevices(): List<Device> = when (isEduOne) {
        true -> studentPlus.getRegisteredDevices(studentId, diaryId, unitId)
        else -> student.getRegisteredDevices()
    }

    suspend fun getToken(): TokenResponse {
        return when (isEduOne) {
            true -> studentPlus.getToken(studentId, diaryId, unitId)
            else -> student.getToken()
        }
    }

    suspend fun unregisterDevice(id: Int): Boolean = when (isEduOne) {
        true -> TODO()
        else -> student.unregisterDevice(id)
    }

    suspend fun getTeachers(): List<Teacher> = when (isEduOne) {
        true -> studentPlus.getTeachers(studentId, diaryId, unitId)
        else -> student.getTeachers()
    }

    suspend fun getSchool(): School = when (isEduOne) {
        true -> studentPlus.getSchool(studentId, diaryId, unitId)
        else -> student.getSchool()
    }

    suspend fun getStudentInfo(): StudentInfo = when (isEduOne) {
        true -> studentPlus.getStudentInfo(studentId, diaryId, unitId)
        else -> student.getStudentInfo()
    }

    suspend fun getStudentPhoto(): StudentPhoto = when (isEduOne) {
        true -> studentPlus.getStudentPhoto(studentId, diaryId, unitId)
        else -> student.getStudentPhoto()
    }

    // MESSAGES

    suspend fun getMailboxes(): List<Mailbox> = messages.getMailboxes()

    suspend fun getRecipients(mailboxKey: String): List<Recipient> = messages.getRecipients(mailboxKey)

    suspend fun getMessages(
        folder: Folder,
        mailboxKey: String? = null,
        lastMessageKey: Int = 0,
        pageSize: Int = 50,
    ): List<MessageMeta> = when (folder) {
        Folder.RECEIVED -> messages.getReceivedMessages(mailboxKey, lastMessageKey, pageSize)
        Folder.SENT -> messages.getSentMessages(mailboxKey, lastMessageKey, pageSize)
        Folder.TRASHED -> messages.getDeletedMessages(mailboxKey, lastMessageKey, pageSize)
    }

    suspend fun getReceivedMessages(mailboxKey: String? = null, lastMessageKey: Int = 0, pageSize: Int = 50): List<MessageMeta> =
        messages.getReceivedMessages(mailboxKey, lastMessageKey, pageSize)

    suspend fun getSentMessages(mailboxKey: String? = null, lastMessageKey: Int = 0, pageSize: Int = 50): List<MessageMeta> =
        messages.getSentMessages(mailboxKey, lastMessageKey, pageSize)

    suspend fun getDeletedMessages(mailboxKey: String? = null, lastMessageKey: Int = 0, pageSize: Int = 50): List<MessageMeta> =
        messages.getDeletedMessages(mailboxKey, lastMessageKey, pageSize)

    suspend fun getMessageReplayDetails(globalKey: String): MessageReplayDetails = messages.getMessageReplayDetails(globalKey)

    suspend fun getMessageDetails(globalKey: String, markAsRead: Boolean): MessageDetails = messages.getMessageDetails(globalKey, markAsRead)

    suspend fun sendMessage(subject: String, content: String, recipients: List<String>, senderMailboxId: String) =
        messages.sendMessage(subject, content, recipients, senderMailboxId)

    suspend fun restoreMessages(messagesToRestore: List<String>) = messages.restoreFromTrash(messagesToRestore)

    suspend fun deleteMessages(messagesToDelete: List<String>, removeForever: Boolean) = messages.deleteMessages(messagesToDelete, removeForever)

    // Homepage

    suspend fun getDirectorInformation(): List<DirectorInformation> = homepage.getDirectorInformation()

    suspend fun getLastAnnouncements(): List<LastAnnouncement> = homepage.getLastAnnouncements()

    suspend fun getSelfGovernments(): List<GovernmentUnit> = homepage.getSelfGovernments()

    suspend fun getStudentThreats(): List<String> = homepage.getStudentThreats()

    suspend fun getStudentsTrips(): List<String> = homepage.getStudentsTrips()

    suspend fun getLastGrades(): List<String> = homepage.getLastGrades()

    suspend fun getFreeDays(): List<String> = homepage.getFreeDays()

    suspend fun getKidsLuckyNumbers(): List<LuckyNumber> = homepage.getKidsLuckyNumbers()

    suspend fun getKidsLessonPlan(): List<String> = homepage.getKidsLessonPlan()

    suspend fun getLastHomework(): List<String> = homepage.getLastHomework()

    suspend fun getLastTests(): List<String> = homepage.getLastTests()

    suspend fun getLastStudentLessons(): List<String> = homepage.getLastStudentLessons()
}

private const val TIMEOUT_IN_SECONDS = 30L
internal fun OkHttpClient.configureForScrapper(host: String, userAgent: String): OkHttpClient = this
    .newBuilder()
    .connectTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    .callTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    .readTimeout(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    .apply {
        when (host) {
            "edu.gdansk.pl",
            "edu.lublin.eu",
            "eduportal.koszalin.pl",
            "vulcan.net.pl",
            -> {
                sslSocketFactory(TLSSocketFactory(), getTrustManager())
            }
        }
    }
    .addInterceptor(UserAgentInterceptor(userAgent))
    .build()

private fun getTrustManager(): X509TrustManager {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(null as? KeyStore?)
    val trustManagers = trustManagerFactory.trustManagers
    if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
        throw IllegalStateException("Unexpected default trust managers: $trustManagers")
    }
    return trustManagers[0] as X509TrustManager
}
