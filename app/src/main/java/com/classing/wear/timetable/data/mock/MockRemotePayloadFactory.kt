package com.classing.wear.timetable.data.mock

import com.classing.wear.timetable.data.sync.RemoteCourse
import com.classing.wear.timetable.data.sync.RemoteException
import com.classing.wear.timetable.data.sync.RemoteSchedulePayload
import com.classing.wear.timetable.data.sync.RemoteSemester
import com.classing.wear.timetable.data.sync.RemoteSession
import com.classing.wear.timetable.data.sync.RemoteTimeSlot
import java.time.LocalDate
import java.time.LocalTime

object MockRemotePayloadFactory {
    private const val SEMESTER_REMOTE_ID = "semester_2026_spring"

    fun fullPayload(): RemoteSchedulePayload {
        return RemoteSchedulePayload(
            dataVersion = 5,
            semesters = listOf(
                RemoteSemester(
                    remoteId = SEMESTER_REMOTE_ID,
                    name = "2026 春季学期",
                    startDate = LocalDate.of(2026, 2, 23),
                    endDate = LocalDate.of(2026, 7, 5),
                    totalWeeks = 19,
                    isActive = true,
                    version = 5,
                ),
            ),
            timeSlots = defaultTimeSlots(),
            courses = defaultCourses(),
            sessions = defaultSessions(),
            exceptions = listOf(
                RemoteException(
                    remoteId = "exception_cancel_math_20260317",
                    semesterRemoteId = SEMESTER_REMOTE_ID,
                    sessionRemoteId = "session_math_tue_3_4",
                    exceptionType = "CANCEL",
                    date = LocalDate.of(2026, 3, 17),
                    reason = "学校运动会停课",
                    courseRemoteId = null,
                    timeSlotRemoteId = null,
                    dayOfWeek = 2,
                    newCourseRemoteId = null,
                    newTimeSlotRemoteId = null,
                    version = 5,
                ),
                RemoteException(
                    remoteId = "exception_makeup_math_20260320",
                    semesterRemoteId = SEMESTER_REMOTE_ID,
                    sessionRemoteId = null,
                    exceptionType = "MAKE_UP",
                    date = LocalDate.of(2026, 3, 20),
                    reason = "补课",
                    courseRemoteId = "course_math",
                    timeSlotRemoteId = "slot_7_8",
                    dayOfWeek = 5,
                    newCourseRemoteId = null,
                    newTimeSlotRemoteId = null,
                    version = 5,
                ),
            ),
        )
    }

    fun deltaPayload(sinceVersion: Long): RemoteSchedulePayload {
        if (sinceVersion >= 5) {
            return RemoteSchedulePayload(
                dataVersion = 5,
                semesters = emptyList(),
                timeSlots = emptyList(),
                courses = emptyList(),
                sessions = emptyList(),
                exceptions = emptyList(),
            )
        }

        return RemoteSchedulePayload(
            dataVersion = 5,
            semesters = emptyList(),
            timeSlots = emptyList(),
            courses = listOf(
                RemoteCourse(
                    remoteId = "course_android",
                    semesterRemoteId = SEMESTER_REMOTE_ID,
                    name = "Android 应用开发",
                    teacher = "赵老师",
                    classroom = "逸夫楼 403",
                    note = "本周实验课需携带耳机",
                    colorLabel = "teal",
                    isFavorite = true,
                    version = 5,
                ),
            ),
            sessions = emptyList(),
            exceptions = emptyList(),
        )
    }

    private fun defaultTimeSlots(): List<RemoteTimeSlot> {
        return listOf(
            RemoteTimeSlot("slot_1_2", SEMESTER_REMOTE_ID, 1, "1-2 节", LocalTime.of(8, 0), LocalTime.of(9, 35), 1),
            RemoteTimeSlot("slot_3_4", SEMESTER_REMOTE_ID, 2, "3-4 节", LocalTime.of(10, 0), LocalTime.of(11, 35), 1),
            RemoteTimeSlot("slot_5_6", SEMESTER_REMOTE_ID, 3, "5-6 节", LocalTime.of(14, 0), LocalTime.of(15, 35), 1),
            RemoteTimeSlot("slot_7_8", SEMESTER_REMOTE_ID, 4, "7-8 节", LocalTime.of(16, 0), LocalTime.of(17, 35), 1),
            RemoteTimeSlot("slot_9_10", SEMESTER_REMOTE_ID, 5, "9-10 节", LocalTime.of(19, 0), LocalTime.of(20, 35), 1),
        )
    }

    private fun defaultCourses(): List<RemoteCourse> {
        return listOf(
            RemoteCourse("course_math", SEMESTER_REMOTE_ID, "高等数学 II", "李老师", "教学楼 A201", "每周随堂测", "red", false, 1),
            RemoteCourse("course_android", SEMESTER_REMOTE_ID, "Android 应用开发", "赵老师", "逸夫楼 403", "双周实验", "teal", true, 1),
            RemoteCourse("course_english", SEMESTER_REMOTE_ID, "大学英语", "王老师", "教学楼 B105", "口语签到", "blue", false, 1),
            RemoteCourse("course_network", SEMESTER_REMOTE_ID, "计算机网络", "陈老师", "信息楼 302", "课堂提问计分", "orange", false, 1),
            RemoteCourse("course_pe", SEMESTER_REMOTE_ID, "体育", "周老师", "东操场", "雨天改教室", "green", false, 1),
        )
    }

    private fun defaultSessions(): List<RemoteSession> {
        return listOf(
            RemoteSession("session_math_tue_3_4", SEMESTER_REMOTE_ID, "course_math", 2, "slot_3_4", 1, 19, "ALL", 1),
            RemoteSession("session_android_mon_5_6", SEMESTER_REMOTE_ID, "course_android", 1, "slot_5_6", 1, 19, "EVEN", 1),
            RemoteSession("session_android_thu_7_8", SEMESTER_REMOTE_ID, "course_android", 4, "slot_7_8", 1, 19, "ODD", 1),
            RemoteSession("session_english_wed_1_2", SEMESTER_REMOTE_ID, "course_english", 3, "slot_1_2", 1, 19, "ALL", 1),
            RemoteSession("session_network_fri_3_4", SEMESTER_REMOTE_ID, "course_network", 5, "slot_3_4", 1, 19, "ALL", 1),
            RemoteSession("session_pe_fri_5_6", SEMESTER_REMOTE_ID, "course_pe", 5, "slot_5_6", 1, 19, "ALL", 1),
        )
    }
}
