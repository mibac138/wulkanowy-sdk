package io.github.wulkanowy.sdk.scrapper.repository

import io.github.wulkanowy.sdk.scrapper.handleErrors
import io.github.wulkanowy.sdk.scrapper.register.RegisterStudent
import io.github.wulkanowy.sdk.scrapper.register.Semester
import io.github.wulkanowy.sdk.scrapper.register.getStudentsFromDiaries
import io.github.wulkanowy.sdk.scrapper.register.toSemesters
import io.github.wulkanowy.sdk.scrapper.service.StudentService
import io.github.wulkanowy.sdk.scrapper.timetable.CacheResponse
import org.slf4j.LoggerFactory

internal class StudentStartRepository(
    private val studentId: Int,
    private val classId: Int,
    private val unitId: Int,
    private val api: StudentService,
    private val forceSignIn: suspend () -> Unit,
) {

    companion object {
        @JvmStatic
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun getSemesters(): List<Semester> {
        val diaries = api.getDiaries().handleErrors().data
        return diaries?.toSemesters(studentId, classId, unitId).orEmpty()
            .sortedByDescending { it.semesterId }
            .ifEmpty {
                logger.debug("Student {}, class {} not found in diaries: {}", studentId, classId, diaries)
                emptyList()
            }
    }

    suspend fun getStudent(studentId: Int, unitId: Int): RegisterStudent? {
        return getStudentsFromDiaries(
            isParent = getCache().isParent,
            diaries = api.getDiaries().handleErrors().data.orEmpty(),
            unitId = unitId,
            isEduOne = false,
        ).find {
            it.studentId == studentId
        }
    }

    private suspend fun getCache(): CacheResponse {
        forceSignIn()
        return api.getUserCache().handleErrors().let {
            requireNotNull(it.data)
        }
    }
}
