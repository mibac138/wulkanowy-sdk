package io.github.wulkanowy.api.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import io.github.wulkanowy.api.ApiResponse
import io.github.wulkanowy.api.attendance.Absent
import io.github.wulkanowy.api.attendance.Attendance
import io.github.wulkanowy.api.attendance.AttendanceExcuseRequest
import io.github.wulkanowy.api.attendance.AttendanceRequest
import io.github.wulkanowy.api.attendance.AttendanceSummary
import io.github.wulkanowy.api.attendance.AttendanceSummaryRequest
import io.github.wulkanowy.api.attendance.Subject
import io.github.wulkanowy.api.attendance.mapAttendanceSummaryList
import io.github.wulkanowy.api.attendance.mapAttendanceList
import io.github.wulkanowy.api.exams.Exam
import io.github.wulkanowy.api.exams.ExamRequest
import io.github.wulkanowy.api.getGradeShortValue
import io.github.wulkanowy.api.getSchoolYear
import io.github.wulkanowy.api.getScriptParam
import io.github.wulkanowy.api.grades.Grade
import io.github.wulkanowy.api.grades.GradeRequest
import io.github.wulkanowy.api.grades.GradeStatistics
import io.github.wulkanowy.api.grades.GradeSummary
import io.github.wulkanowy.api.grades.GradesStatisticsRequest
import io.github.wulkanowy.api.grades.getGradeValueWithModifier
import io.github.wulkanowy.api.grades.isGradeValid
import io.github.wulkanowy.api.homework.Homework
import io.github.wulkanowy.api.interceptor.ErrorHandlerTransformer
import io.github.wulkanowy.api.mobile.Device
import io.github.wulkanowy.api.mobile.TokenResponse
import io.github.wulkanowy.api.mobile.UnregisterDeviceRequest
import io.github.wulkanowy.api.notes.Note
import io.github.wulkanowy.api.school.School
import io.github.wulkanowy.api.school.Teacher
import io.github.wulkanowy.api.service.StudentService
import io.github.wulkanowy.api.timetable.CacheResponse
import io.github.wulkanowy.api.timetable.CompletedLesson
import io.github.wulkanowy.api.timetable.CompletedLessonsRequest
import io.github.wulkanowy.api.timetable.Timetable
import io.github.wulkanowy.api.timetable.TimetableParser
import io.github.wulkanowy.api.timetable.TimetableRequest
import io.github.wulkanowy.api.timetable.TimetableResponse
import io.github.wulkanowy.api.toDate
import io.github.wulkanowy.api.toFormat
import io.github.wulkanowy.api.toLocalDate
import io.reactivex.Single
import org.jsoup.Jsoup
import org.threeten.bp.LocalDate
import java.lang.String.format
import java.util.Locale

class StudentRepository(private val api: StudentService) {

    private lateinit var cache: CacheResponse

    private lateinit var times: List<CacheResponse.Time>

    private val gson by lazy { GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss") }

    private fun LocalDate.toISOFormat(): String = toFormat("yyyy-MM-dd'T00:00:00'")

    private fun getCache(): Single<CacheResponse> {
        if (::cache.isInitialized) return Single.just(cache)

        return api.getStart("Start").flatMap {
            api.getUserCache(
                getScriptParam("antiForgeryToken", it),
                getScriptParam("appGuid", it),
                getScriptParam("version", it)
            )
        }.compose(ErrorHandlerTransformer()).map { it.data }
    }

    private fun getTimes(): Single<List<CacheResponse.Time>> {
        if (::times.isInitialized) return Single.just(times)

        return getCache().map { res -> res.times }.map { list ->
            list.apply { times = this }
        }
    }

    fun getAttendance(startDate: LocalDate, endDate: LocalDate?): Single<List<Attendance>> {
        return api.getAttendance(AttendanceRequest(startDate.toDate()))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .mapAttendanceList(startDate, endDate, ::getTimes)
    }

    fun getAttendanceSummary(subjectId: Int?): Single<List<AttendanceSummary>> {
        return api.getAttendanceStatistics(AttendanceSummaryRequest(subjectId))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { it.mapAttendanceSummaryList(gson) }
    }

    fun excuseForAbsence(absents: List<Absent>, content: String?): Single<Boolean> {
        return api.excuseForAbsence(
            AttendanceExcuseRequest(
                AttendanceExcuseRequest.Excuse(
                    absents = absents.map {
                        AttendanceExcuseRequest.Excuse.Absent(
                            date = it.date.toFormat("yyyy-MM-dd'T'HH:mm:ss"),
                            timeId = it.timeId
                        )
                    },
                    content = content
                )
            )
        ).compose(ErrorHandlerTransformer()).map { it.success }
    }

    fun getSubjects(): Single<List<Subject>> {
        return api.getAttendanceSubjects()
            .compose(ErrorHandlerTransformer()).map { it.data }
    }

    fun getExams(startDate: LocalDate, endDate: LocalDate? = null): Single<List<Exam>> {
        val end = endDate ?: startDate.plusDays(4)
        return api.getExams(ExamRequest(startDate.toDate(), startDate.getSchoolYear()))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.asSequence().map { weeks ->
                    weeks.weeks.map { day ->
                        day.exams.map { exam ->
                            exam.apply {
                                group = subject.split("|").last()
                                subject = subject.substringBeforeLast(" ")
                                if (group.contains(" ")) group = ""
                                date = day.date
                                type = when (type) {
                                    "1" -> "Sprawdzian"
                                    "2" -> "Kartkówka"
                                    else -> "Praca klasowa"
                                }
                                teacherSymbol = teacher.split(" [").last().removeSuffix("]")
                                teacher = teacher.split(" [").first()
                            }
                        }
                    }.flatten()
                }.flatten().filter {
                    it.date.toLocalDate() >= startDate && it.date.toLocalDate() <= end
                }.sortedBy { it.date }.toList()
            }
    }

    fun getGrades(semesterId: Int?): Single<List<Grade>> {
        return api.getGrades(GradeRequest(semesterId))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.gradesWithSubjects.map { gradesSubject ->
                    gradesSubject.grades.map { grade ->
                        val values = getGradeValueWithModifier(grade.entry)
                        grade.apply {
                            subject = gradesSubject.name
                            comment = entry.substringBefore(" (").run {
                                if (length > 4) this
                                else entry.substringBeforeLast(")").substringAfter(" (")
                            }
                            entry = entry.substringBefore(" (").run { if (length > 4) "..." else this }
                            if (comment == entry) comment = ""
                            value = values.first
                            date = privateDate
                            modifier = values.second
                            weight = format(Locale.FRANCE, "%.2f", weightValue)
                            weightValue = if (isGradeValid(entry)) weightValue else .0
                            color = if ("0" == color) "000000" else color.toInt().toString(16).toUpperCase()
                            symbol = symbol ?: ""
                            description = description ?: ""
                        }
                    }
                }.flatten().sortedByDescending { it.date }
            }
    }

    fun getGradesStatistics(semesterId: Int, annual: Boolean): Single<List<GradeStatistics>> {
        return if (annual) api.getGradesAnnualStatistics(GradesStatisticsRequest(semesterId))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map {
                it.map { annualSubject ->
                    annualSubject.items?.reversed()?.mapIndexed { index, item ->
                        item.apply {
                            this.semesterId = semesterId
                            gradeValue = index + 1
                            grade = item.gradeValue.toString()
                            subject = annualSubject.subject
                        }
                    }.orEmpty()
                }.flatten().reversed()
            } else return api.getGradesPartialStatistics(GradesStatisticsRequest(semesterId))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map {
                it.map { partialSubject ->
                    partialSubject.classSeries.items?.reversed()?.mapIndexed { index, item ->
                        item.apply {
                            this.semesterId = semesterId
                            gradeValue = index + 1
                            grade = item.gradeValue.toString()
                            subject = partialSubject.subject
                        }
                    }?.reversed().orEmpty()
                }.flatten()
            }
    }

    fun getGradesSummary(semesterId: Int?): Single<List<GradeSummary>> {
        return api.getGrades(GradeRequest(semesterId))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.gradesWithSubjects.map { subject ->
                    GradeSummary().apply {
                        visibleSubject = subject.visibleSubject
                        order = subject.order
                        name = subject.name
                        average = subject.average
                        predicted = getGradeShortValue(subject.proposed)
                        final = getGradeShortValue(subject.annual)
                        pointsSum = subject.pointsSum.orEmpty()
                        proposedPoints = subject.proposedPoints.orEmpty()
                        finalPoints = subject.finalPoints.orEmpty()
                    }
                }.sortedBy { it.name }.toList()
            }
    }

    fun getHomework(startDate: LocalDate, endDate: LocalDate? = null): Single<List<Homework>> {
        val end = endDate ?: startDate
        return api.getHomework(ExamRequest(startDate.toDate(), startDate.getSchoolYear()))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.asSequence().map { day ->
                    day.items.map {
                        val teacherAndDate = it.teacher.split(", ")
                        it.apply {
                            date = day.date
                            entryDate = teacherAndDate.last().toDate("dd.MM.yyyy")
                            teacher = teacherAndDate.first().split(" [").first()
                            teacherSymbol = teacherAndDate.first().split(" [").last().removeSuffix("]")
                        }
                    }
                }.flatten().filter {
                    it.date.toLocalDate() in startDate..end
                }.sortedWith(compareBy({ it.date }, { it.subject })).toList()
            }
    }

    fun getNotes(): Single<List<Note>> {
        return api.getNotes()
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.notes.map {
                    it.apply {
                        teacherSymbol = teacher.split(" [").last().removeSuffix("]")
                        teacher = teacher.split(" [").first()
                    }
                }.sortedWith(compareBy({ it.date }, { it.category }))
            }
    }

    fun getTimetable(startDate: LocalDate, endDate: LocalDate? = null): Single<List<Timetable>> {
        return api.getTimetable(TimetableRequest(startDate.toISOFormat()))
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.rows2api.flatMap { lessons ->
                    lessons.drop(1).mapIndexed { i, it ->
                        val times = lessons[0].split("<br />")
                        TimetableResponse.TimetableRow.TimetableCell().apply {
                            date = res.header.drop(1)[i].date.split("<br />")[1].toDate("dd.MM.yyyy")
                            start = "${date.toLocalDate().toFormat("yyyy-MM-dd")} ${times[1]}".toDate("yyyy-MM-dd HH:mm")
                            end = "${date.toLocalDate().toFormat("yyyy-MM-dd")} ${times[2]}".toDate("yyyy-MM-dd HH:mm")
                            number = times[0].toInt()
                            td = Jsoup.parse(it)
                        }
                    }.mapNotNull { TimetableParser().getTimetable(it) }
                }.asSequence().filter {
                    it.date.toLocalDate() >= startDate && it.date.toLocalDate() <= endDate ?: startDate.plusDays(4)
                }.sortedWith(compareBy({ it.date }, { it.number })).toList()
            }
    }

    fun getCompletedLessons(start: LocalDate, endDate: LocalDate?, subjectId: Int): Single<List<CompletedLesson>> {
        val end = endDate ?: start.plusMonths(1)
        return api.getCompletedLessons(CompletedLessonsRequest(start.toISOFormat(), end.toISOFormat(), subjectId)).map {
            gson.create().fromJson(it, ApiResponse::class.java)
        }.compose<ApiResponse<*>>(ErrorHandlerTransformer()).map { res ->
            (res.data as LinkedTreeMap<*, *>).map { list ->
                gson.create().fromJson<List<CompletedLesson>>(Gson().toJson(list.value), object : TypeToken<ArrayList<CompletedLesson>>() {}.type)
            }.flatten().map {
                it.apply {
                    teacherSymbol = teacher.substringAfter(" [").substringBefore("]")
                    teacher = teacher.substringBefore(" [")
                }
            }.sortedWith(compareBy({ it.date }, { it.number })).toList().filter {
                it.date.toLocalDate() >= start && it.date.toLocalDate() <= end
            }
        }
    }

    fun getTeachers(): Single<List<Teacher>> {
        return api.getSchoolAndTeachers()
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.teachers.map {
                    it.copy(
                        short = it.name.substringAfter("[").substringBefore("]"),
                        name = it.name.substringBefore(" [")
                    )
                }.sortedWith(compareBy({ it.subject }, { it.name }))
            }
    }

    fun getSchool(): Single<School> {
        return api.getSchoolAndTeachers()
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { it.school }
    }

    fun getRegisteredDevices(): Single<List<Device>> {
        return api.getRegisteredDevices()
            .compose(ErrorHandlerTransformer()).map { it.data }
    }

    fun getToken(): Single<TokenResponse> {
        return api.getToken()
            .compose(ErrorHandlerTransformer()).map { it.data }
            .map { res ->
                res.apply {
                    qrCodeImage = Jsoup.parse(qrCodeImage).select("img").attr("src").split("data:image/png;base64,")[1]
                }
            }
    }

    fun unregisterDevice(id: Int): Single<Boolean> {
        return api.getStart("Start").flatMap {
            api.unregisterDevice(
                getScriptParam("antiForgeryToken", it),
                getScriptParam("appGuid", it),
                getScriptParam("version", it),
                UnregisterDeviceRequest(id)
            )
        }.compose(ErrorHandlerTransformer()).map { it.success }
    }
}
