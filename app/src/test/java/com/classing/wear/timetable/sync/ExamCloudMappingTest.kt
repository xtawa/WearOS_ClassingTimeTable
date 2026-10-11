package com.classing.wear.timetable.sync

import com.classing.client.exam.ExamJson
import com.classing.shared.exam.Exam
import com.classing.shared.sync.CloudSyncV2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExamCloudMappingTest {
    private val exam = Exam("e1", "Math exam", 1792112400000, 1792119600000, "Asia/Shanghai")
    private fun record(id: String = exam.id, payload: String = ExamJson.encode(exam).toString(), deleted: Boolean = false) = JSONObject()
        .put("id",id).put("payload",payload).put("deletedAt",if(deleted) 30 else JSONObject.NULL)
        .put("version",JSONObject().put("counter",30).put("deviceId","phone").put("changedAt",30))
    private fun document(exams: JSONArray) = JSONObject().put("records",JSONObject().put(CloudSyncV2.DOMAIN_TIMETABLE_EXAMS,exams))
    @Test fun examOnlyDocumentHasRevisionAndPreservesSourceZone() {
        val mapped = OfficialCloudTimetableMapper.map(document(JSONArray().put(record())))!!
        assertEquals(30L,mapped.revision)
        assertEquals(listOf(exam),mapped.payload.exams)
        assertEquals(0,mapped.lessonCount)
        assertFalse(mapped.payload.applyTimetable)
    }
    @Test fun tombstonesAndEmptyDomainClearExams() {
        assertEquals(emptyList<Exam>(),OfficialCloudTimetableMapper.map(document(JSONArray().put(record(deleted=true))))!!.payload.exams)
        assertEquals(emptyList<Exam>(),OfficialCloudTimetableMapper.map(document(JSONArray()))!!.payload.exams)
    }
    @Test fun missingDomainPreservesLegacyDataExceptDuringAccountSwitch() {
        val legacy=JSONObject().put("records",JSONObject().put(CloudSyncV2.DOMAIN_TIMETABLE_LESSONS,JSONArray()))
        assertNull(OfficialCloudTimetableMapper.map(legacy)!!.payload.exams)
        assertEquals(emptyList<Exam>(),OfficialCloudTimetableMapper.map(legacy,missingDomainsAreEmpty=true)!!.payload.exams)
    }
    @Test fun malformedExamOrMismatchedIdRejectsWholeSnapshot() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialCloudTimetableMapper.map(document(JSONArray().put(record()).put(record("bad",ExamJson.encode(exam).put("endAt",0).toString()))))
        }
        assertThrows(IllegalArgumentException::class.java) { OfficialCloudTimetableMapper.map(document(JSONArray().put(record(id="different")))) }
    }
}
