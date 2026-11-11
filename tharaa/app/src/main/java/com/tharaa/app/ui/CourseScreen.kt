package com.tharaa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Course
import com.tharaa.app.domain.CourseProgress

private fun progressOf(state: AppState) = CourseProgress(state.settings.courseDone, state.settings.courseLastDay)

/** قائمة المنهج داخل تبويب المدرب. */
@Composable
fun CourseList(state: AppState, onOpen: (Int) -> Unit, onSummaries: () -> Unit) {
    val p = progressOf(state)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepGreen)
                    .padding(18.dp),
            ) {
                Text("منهج الثراء، 30 يوم", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("درس واحد كل يوم: فكرة، أرقام، مهمة عملية، وسؤال.", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Progress(p.done.size / p.total.toFloat(), Gold)
                Spacer(Modifier.height(6.dp))
                Text("خلّصت ${p.done.size} من ${p.total}", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                val next = p.next
                when {
                    p.finished -> Text("🎓 خلّصت المنهج كامل. ارجع لدرس 30 كل سنة.", color = Gold, fontWeight = FontWeight.Bold)
                    next != null && p.todayOpen -> Button(
                        onClick = { onOpen(next) },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepGreen),
                    ) { Text("ابدأ درس اليوم: ${Course.of(next)?.title}", fontWeight = FontWeight.Bold) }
                    else -> Text("✅ خلّصت درس اليوم. الدرس الجاي بينفتح بكرة.", color = Gold, fontWeight = FontWeight.Bold)
                }
            }
        }
        Course.units.forEachIndexed { u, unitName ->
            item {
                Text(
                    "الوحدة ${u + 1}: $unitName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
            Course.days.filter { it.unit == u }.forEach { d ->
                item {
                    val open = p.isOpen(d.day)
                    val done = d.day in p.done
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = open) { onOpen(d.day) }
                            .background(if (open && !done) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${d.day}",
                            modifier = Modifier.width(28.dp),
                            fontWeight = FontWeight.Bold,
                            color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                d.title,
                                fontWeight = FontWeight.SemiBold,
                                color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                            Text("${d.minutes} دقايق، ${d.source}", fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Icon(
                            when {
                                done -> Icons.Rounded.CheckCircle
                                open -> Icons.Rounded.PlayCircle
                                else -> Icons.Rounded.Lock
                            },
                            contentDescription = null,
                            tint = when {
                                done -> Profit
                                open -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onSummaries, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("ملخصات سريعة لكتب الثراء")
            }
        }
    }
}

/** صفحة الدرس الكاملة. */
@Composable
fun LessonScreen(day: Int, state: AppState, vm: AppViewModel, onBack: () -> Unit, onNext: (Int) -> Unit) {
    val d = Course.of(day)
    if (d == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val p = progressOf(state)
    val done = day in p.done
    var picked by remember(day) { mutableStateOf<Int?>(null) }
    val correct = picked == d.quiz.answer

    Screen(title = "اليوم $day", onBack = onBack) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("الوحدة ${d.unit + 1}: ${Course.units[d.unit]}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("${d.source}، ${d.minutes} دقايق قراءة", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            Spacer(Modifier.height(20.dp))

            d.sections.forEach { s ->
                Text(s.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(s.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp))
                Spacer(Modifier.height(20.dp))
            }

            // مهمة اليوم
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(16.dp),
            ) {
                Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(Gold))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("مهمة اليوم", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(d.action, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp))
                }
            }
            Spacer(Modifier.height(24.dp))

            // السؤال
            Text("سؤال الفهم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(d.quiz.question, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            d.quiz.options.forEachIndexed { i, opt ->
                val isPicked = picked == i
                val border = when {
                    isPicked && i == d.quiz.answer -> Profit
                    isPicked -> Loss
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Text(
                    opt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(BorderStroke(if (isPicked) 2.dp else 1.dp, border), RoundedCornerShape(14.dp))
                        .clickable(enabled = !correct) { picked = i }
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (picked != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (correct) "✅ صح. " else "❌ مش هاي. جرّب مرة ثانية. ") + if (correct) d.quiz.why else "",
                    color = if (correct) Profit else Loss,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(24.dp))

            when {
                done -> {
                    Text("✅ خلّصت هالدرس", color = Profit, fontWeight = FontWeight.Bold)
                    val nextDay = p.next
                    if (nextDay != null && p.isOpen(nextDay) && nextDay != day) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { onNext(nextDay) }) { Text("الدرس الجاي") }
                    }
                }
                p.isOpen(day) -> Button(
                    onClick = { vm.completeLesson(day) },
                    enabled = correct,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (correct) "أنهيت درس اليوم" else "جاوب السؤال صح لتنهي الدرس") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SummariesScreen(onBack: () -> Unit) {
    Screen(title = "ملخصات سريعة", onBack = onBack) { pad ->
        LessonsList(Modifier.fillMaxSize().padding(pad))
    }
}
