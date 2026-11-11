package com.tharaa.app.domain

data class CourseSection(val heading: String, val text: String)

data class Quiz(val question: String, val options: List<String>, val answer: Int, val why: String)

data class CourseDay(
    val day: Int,
    val unit: Int,
    val title: String,
    val source: String,
    val sections: List<CourseSection>,
    val action: String,
    val quiz: Quiz,
) {
    /** وقت القراءة التقريبي (حوالي 100 كلمة بالدقيقة مع التمعّن والأرقام). */
    val minutes: Int
        get() {
            val words = (sections.sumOf { it.text.split(' ').size + it.heading.split(' ').size } + action.split(' ').size + 40)
            return (words / 100).coerceAtLeast(3)
        }
}

/**
 * منهج الثراء: 30 يوم، درس كل يوم. كل درس: فكرة، ليش مهمة، مثال بالأرقام، أخطاء شائعة، تطبيق عليك،
 * ثم مهمة عملية وسؤال. المحتوى إعادة صياغة لأفكار كتب الثراء المعروفة، مش اقتباس حرفي.
 */
object Course {
    val units = listOf(
        "العقلية",
        "السيطرة على المال",
        "زيادة الدخل",
        "الاستثمار",
        "اللعبة الطويلة",
    )

    val days: List<CourseDay> by lazy {
        (CoursePart1.days + CoursePart2.days + CoursePart3.days).sortedBy { it.day }.map { d ->
            d.copy(
                sections = d.sections + listOfNotNull(
                    CourseExtras.stories[d.day]?.let { CourseSection("من الواقع", it) },
                    CourseExtras.takeaways[d.day]?.let { CourseSection("الخلاصة", it) },
                ),
            )
        }
    }

    fun of(day: Int): CourseDay? = days.firstOrNull { it.day == day }
}

internal fun lesson(
    day: Int,
    unit: Int,
    title: String,
    source: String,
    vararg sections: Pair<String, String>,
    action: String,
    q: String,
    options: List<String>,
    answer: Int,
    why: String,
) = CourseDay(day, unit, title, source, sections.map { CourseSection(it.first, it.second) }, action, Quiz(q, options, answer, why))

/** تقدّمك بالمنهج: درس جديد بينفتح كل يوم بعد ما تخلّص اللي قبله. */
data class CourseProgress(val done: Set<Int>, val lastDay: Long, val now: Long = System.currentTimeMillis()) {
    val total get() = Course.days.size
    val next: Int? get() = Course.days.map { it.day }.firstOrNull { it !in done }
    /** درس اليوم متاح إذا ما خلّصت درس اليوم أصلاً. */
    val todayOpen: Boolean get() = next != null && (done.isEmpty() || lastDay < startOfDay(now))
    fun isOpen(day: Int) = day in done || (day == next && todayOpen)
    val finished get() = done.size >= total
}
