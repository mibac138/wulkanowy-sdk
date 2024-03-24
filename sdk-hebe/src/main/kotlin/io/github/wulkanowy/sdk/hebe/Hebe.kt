package io.github.wulkanowy.sdk.hebe

import io.github.wulkanowy.sdk.hebe.interceptor.DeviceModelInterceptor
import io.github.wulkanowy.sdk.hebe.models.Exam
import io.github.wulkanowy.sdk.hebe.models.Grade
import io.github.wulkanowy.sdk.hebe.models.GradeAverage
import io.github.wulkanowy.sdk.hebe.models.GradeSummary
import io.github.wulkanowy.sdk.hebe.register.RegisterDevice
import io.github.wulkanowy.sdk.hebe.register.StudentInfo
import io.github.wulkanowy.sdk.hebe.repository.RepositoryManager
import io.github.wulkanowy.signer.hebe.generateKeyPair
import okhttp3.OkHttpClient
import java.time.LocalDate

class Hebe(val baseUrl: String = "", val schoolId: String = "", val pupilId: Int = -1, val deviceModel: String = "", httpClient: OkHttpClient = OkHttpClient()) {

    private val resettableManager = resettableManager()

    var keyId = ""
        set(value) {
            field = value
            resettableManager.reset()
        }

    var privatePem = ""
        set(value) {
            field = value
            resettableManager.reset()
        }


    private val serviceManager by resettableLazy(resettableManager) {
        RepositoryManager(
            httpClient = httpClient.configureForHebe(deviceModel),
            keyId = keyId,
            privatePem = privatePem,
        )
    }

    private val routes by resettableLazy(resettableManager) { serviceManager.getRoutesRepository() }

    private val studentRepository by resettableLazy(resettableManager) {
        serviceManager.getStudentRepository(
            baseUrl = baseUrl,
            schoolId = schoolId,
        )
    }

    suspend fun register(token: String, pin: String, symbol: String, firebaseToken: String? = null): RegisterDevice {
        val (publicPem, privatePem, publicHash) = generateKeyPair()

        this.keyId = publicHash
        this.privatePem = privatePem

        val envelope = serviceManager.getRegisterRepository(
            baseUrl = routes.getRouteByToken(token),
            symbol = symbol,
        ).register(
            firebaseToken = firebaseToken,
            token = token,
            pin = pin,
            certificatePem = publicPem,
            certificateId = publicHash,
            deviceModel = deviceModel,
        )

        return RegisterDevice(
            loginId = envelope.loginId,
            restUrl = envelope.restUrl,
            userLogin = envelope.userLogin,
            userName = envelope.userName,
            certificateHash = publicHash,
            privatePem = privatePem,
        )
    }

    suspend fun getStudents(url: String): List<StudentInfo> {
        return serviceManager
            .getRegisterRepository(url)
            .getStudentInfo()
    }

    suspend fun getGrades(periodId: Int): List<Grade> {
        return studentRepository.getGrades(
            pupilId = pupilId,
            periodId = periodId,
        )
    }

    suspend fun getGradesSummary(periodId: Int): List<GradeSummary> {
        return studentRepository.getGradesSummary(
            pupilId = pupilId,
            periodId = periodId,
        )
    }

    suspend fun getGradesAverage(periodId: Int): List<GradeAverage> {
        return studentRepository.getGradesAverage(
            pupilId = pupilId,
            periodId = periodId,
        )
    }

    suspend fun getExams(startDate: LocalDate, endDate: LocalDate): List<Exam> {
        return studentRepository.getExams(
            pupilId = pupilId,
            startDate = startDate,
            endDate = endDate,
        )
    }
}

internal fun OkHttpClient.configureForHebe(deviceModel: String): OkHttpClient = this
    .newBuilder()
    .addInterceptor(DeviceModelInterceptor(deviceModel))
    .build()
