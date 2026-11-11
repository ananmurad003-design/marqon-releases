package com.tharaa.app

import com.tharaa.app.domain.Course
import com.tharaa.app.domain.CourseProgress
import com.tharaa.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test fun thirtyCompleteLessons() {
        assertEquals((1..30).toList(), Course.days.map { it.day })
        Course.days.forEach { d ->
            assertTrue("day ${d.day} sections", d.sections.size >= 7)
            assertTrue("day ${d.day} answer", d.quiz.answer in d.quiz.options.indices)
            assertTrue("day ${d.day} unit", d.unit in Course.units.indices)
            assertTrue("day ${d.day} long enough: ${d.minutes}", d.minutes >= 3)
        }
        println("minutes: " + Course.days.map { it.minutes })
    }

    @Test fun oneLessonPerDay() {
        val now = System.currentTimeMillis()
        val fresh = CourseProgress(emptySet(), 0, now)
        assertTrue(fresh.isOpen(1))
        assertFalse(fresh.isOpen(2))
        val today = CourseProgress(setOf(1), startOfDay(now), now)
        assertFalse(today.todayOpen)
        assertTrue(today.isOpen(1))
        val tomorrow = CourseProgress(setOf(1), startOfDay(now) - 86_400_000L, now)
        assertTrue(tomorrow.isOpen(2))
    }
}
