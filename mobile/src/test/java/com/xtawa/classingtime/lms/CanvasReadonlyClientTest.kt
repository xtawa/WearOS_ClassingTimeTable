package com.xtawa.classingtime.lms

import com.classing.client.lms.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CanvasReadonlyClientTest {
    private val from = LocalDate.parse("2026-10-11")
    @Test fun syntheticSchoolOneSeparatesHomeworkFromClassMeetings() = runBlocking {
        val client = CanvasReadonlyClient { url, token ->
            assertEquals("authorized-test-token", token)
            when { url.encodedPath.endsWith("courses") -> CanvasPage(JSONArray("""[{"id":1,"name":"Math"}]"""))
                url.queryParameter("type") == "assignment" -> CanvasPage(JSONArray("""[{"id":"assignment_1","title":"Essay","start_at":"2026-10-12T23:59:00Z","context_code":"course_1"}]"""))
                else -> { assertEquals("course_1",url.queryParameter("context_codes[]")); CanvasPage(JSONArray("""[{"id":10,"title":"Lecture","context_code":"course_1","start_at":"2026-10-12T09:00:00-04:00","end_at":"2026-10-12T10:00:00-04:00","location_name":"A1"}]""")) }
            }
        }
        val data=client.load("https://school.example", "authorized-test-token", from, from.plusDays(7))
        assertEquals(1,data.meetings.size); assertEquals(1,data.assignments.size); assertEquals("2026-10-12T13:00:00Z",data.meetings.single().start.toString())
    }
    @Test fun syntheticSchoolTwoFollowsPaginationAndTenContextBatches() = runBlocking {
        val counts = mutableListOf<Int>(); var courses = 0
        val client = CanvasReadonlyClient { url, _ ->
            if(url.encodedPath.endsWith("courses")) { courses++
                if(courses==1) CanvasPage(JSONArray((1..10).joinToString(prefix="[",postfix="]") { "{\"id\":$it,\"name\":\"C$it\"}" }), "<https://school.example/api/v1/courses?page=2>; rel=\"next\"")
                else CanvasPage(JSONArray("""[{"id":11,"name":"Lab"}]"""))
            } else { counts+=url.queryParameterValues("context_codes[]").size; CanvasPage(JSONArray()) }
        }
        assertEquals(11,client.load("https://school.example","test",from,from.plusDays(7)).courses.size)
        assertEquals(listOf(10,10,1,1),counts)
    }
    @Test fun syntheticSchoolThreeKeepsUndatedAssignmentsAndWarnsOnAllDay() = runBlocking {
        val client=CanvasReadonlyClient { url,_ -> when {
            url.encodedPath.endsWith("courses") -> CanvasPage(JSONArray("""[{"id":1,"name":"English"}]"""))
            url.queryParameter("type")=="assignment" -> CanvasPage(JSONArray("""[{"id":"assignment_1","title":"Reading","start_at":null}]"""))
            else -> CanvasPage(JSONArray("""[{"id":1,"title":"Holiday","all_day":true},{"id":2,"workflow_state":"deleted"}]"""))
        } }
        val data=client.load("https://school.example","test",from,from.plusDays(7))
        assertTrue(data.meetings.isEmpty());assertEquals(1,data.warnings.size);assertNull(data.assignments.single().due)
    }
    @Test fun crossOriginPaginationNeverReceivesToken() {
        var calls=0
        val client=CanvasReadonlyClient { _,_->calls++;CanvasPage(JSONArray(),"<https://evil.example/api/v1/courses>; rel=\"next\"") }
        assertThrows(IllegalArgumentException::class.java) { runBlocking{client.load("https://school.example","test",from,from)} };assertEquals(1,calls)
    }
    @Test fun revokedAuthorizationIsAnErrorWithNoPartialSnapshot() {
        val client=CanvasReadonlyClient { _,_->throw IOException("Canvas permission denied") }
        assertThrows(IOException::class.java) { runBlocking{client.load("https://school.example","test",from,from)} }
    }
}
