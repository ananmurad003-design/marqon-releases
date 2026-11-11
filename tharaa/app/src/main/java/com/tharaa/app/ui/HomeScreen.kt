package com.tharaa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.Lessons
import com.tharaa.app.domain.Phase
import com.tharaa.app.domain.money
import com.tharaa.app.domain.monthTitle
import com.tharaa.app.data.GoalKind

data class Advice(val emoji: String, val title: String, val body: String, val action: String? = null, val route: String? = null)

/** نصائح المستشار حسب وضعك الحالي. */
fun adviceFor(s: AppState): List<Advice> {
    val out = mutableListOf<Advice>()
    val debtCash = s.bal(Fund.DEBT)
    val next = s.nextDebt
    if (s.raw.incomes.none { com.tharaa.app.domain.monthKey(it.timestamp) == s.currentMonth }) {
        out += Advice("💵", "سجّل أول دخل لهذا الشهر", "كل دفعة بتوصلك سجّلها فوراً قبل ما تصرف منها، وأنا بقسّمها.", "دخل جديد", "income")
    }
    if (next != null && debtCash >= next.remaining - 0.5) {
        out += Advice("🎉", "بتقدر تخلّص «${next.debt.name}» الآن!", "صندوق الديون فيه ${money(debtCash)} والدين المتبقي ${money(next.remaining)}. سدّده واكسب أول انتصار.", "سدّد الآن", "plan")
    } else if (next != null && debtCash >= 1) {
        out += Advice("⛓️", "عندك ${money(debtCash)} لسداد الديون", "حسب طريقتك، ابدأ بـ «${next.debt.name}» (متبقي ${money(next.remaining)}). كل دفعة بتقرّبك من الحرية.", "سدّد", "plan")
    } else if (next == null && debtCash >= 1) {
        out += Advice("🔁", "ما عليك ديون والصندوق فيه ${money(debtCash)}", "حوّل المبلغ للاستثمار حتى يشتغل لصالحك.", "افتح الصندوق", "fund/${Fund.DEBT.key}")
    }
    val inv = s.bal(Fund.INVESTMENT)
    if (inv >= 300 && s.raw.holdings.none { it.quantity > 0 }) {
        out += Advice("🌱", "عندك ${money(inv)} جاهزة للاستثمار", "المال بصندوق الاستثمار لازم يشتري أصول. اقرأ درس «صناديق المؤشرات» وابدأ ولو بمبلغ صغير.", "الاستثمار", "invest")
    }
    if (s.avgMonthlyExpenses > 0 && s.monthExpenses > s.avgMonthlyExpenses * 1.1) {
        out += Advice("⚠️", "مصاريفك هالشهر فوق المعتاد", "صرفت ${money(s.monthExpenses)} مقابل متوسط ${money(s.avgMonthlyExpenses)}. راجع التصنيفات وشوف وين التسريب.", "المصاريف", "expenses")
    }
    val personal = s.bal(Fund.PERSONAL)
    if (personal in 0.01..50.0) {
        out += Advice("🪫", "مصروفك قرّب يخلص", "باقي ${money(personal)}. لا تسحب من الصناديق الثانية، خفّف لحد الدخل الجاي.")
    }
    if (s.raw.debts.isEmpty() && s.phase == Phase.FOUNDATION) {
        out += Advice("📋", "عليك ديون؟ سجّلها", "حتى أعطيها الأولوية الصح وأعملك خطة سداد.", "الديون", "plan")
    }
    return out
}

@Composable
fun HomeScreen(state: AppState, vm: AppViewModel, nav: NavHostController) {
    Screen(
        title = "ثراء",
        actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "الإعدادات") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeroCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { nav.navigate("income") },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("＋ دخل جديد", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { nav.navigate("expense") },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("－ مصروف", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (!state.loggedToday) {
                item {
                    TextButton(onClick = { vm.markNoSpend() }, modifier = Modifier.fillMaxWidth()) {
                        Text("ما صرفت شي اليوم 🙌  (بتحافظ على سلسلتك)")
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("جرارك", Modifier.weight(1f))
                    Text("الامتلاء = اللي باقي من دخل الشهر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
            Fund.entries.chunked(4).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { f -> JarTile(f, state, Modifier.weight(1f)) { nav.navigate("fund/${f.key}") } }
                    }
                }
            }
            adviceFor(state).forEach { a ->
                item {
                    AdviceCard(a) {
                        val r = a.route ?: return@AdviceCard
                        if (r in listOf("plan", "invest", "expenses")) nav.goTab(r) else nav.navigate(r)
                    }
                }
            }
            val goals = state.goals.filter { !it.goal.done }
            if (goals.isNotEmpty()) {
                item { SectionTitle("أهدافك") }
                item {
                    Card(Modifier.fillMaxWidth().clickable { nav.goTab("plan") }, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            goals.forEach { g ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(g.goal.emoji, fontSize = 20.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(g.goal.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("${(g.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Progress(g.progress, Gold)
                                    Text(
                                        "${money(g.saved)} من ${money(g.goal.target)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { TipCard(Lessons.tipOfTheDay()) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HeroCard(s: AppState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(DeepGreen)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صافي ثروتك", color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            StreakChip(s.streak)
        }
        Text(money(s.netWorth), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(
            "نقد ${money(s.cashTotal)}   محفظة ${money(s.portfolioValue)}   ديون ${money(s.debtRemaining)}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text("مرحلتك ${s.phase.step} من 4: ${s.phase.title}", color = Gold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        PhaseRoad(s.phase)
        Spacer(Modifier.height(10.dp))
        Text(s.phase.description, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp)
        if (s.settings.isCustom) {
            Spacer(Modifier.height(6.dp))
            Text("✋ تستخدم نسب مخصصة", color = Gold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${monthTitle(s.currentMonth)}: دخلك ${money(s.monthIncome)}، مصاريفك ${money(s.monthExpenses)}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
    }
}

/** امتلاء الجرة: الطوارئ نسبة للهدف، الديون نسبة للدين المتبقي، والباقي نسبة لما دخلها هالشهر. */
fun fillOf(f: Fund, s: AppState): Float {
    val bal = s.bal(f)
    if (bal <= 0) return 0f
    val ratio = when (f) {
        Fund.EMERGENCY -> bal / s.emergencyTarget.coerceAtLeast(1.0)
        Fund.DEBT -> if (s.debtRemaining > 0) bal / s.debtRemaining else 1.0
        Fund.GOALS -> {
            val targets = s.goals.filter { it.goal.kind == GoalKind.SAVING && !it.goal.done }.sumOf { it.goal.target }
            if (targets > 0) bal / targets else 1.0
        }
        else -> {
            val inflow = s.monthInflow[f] ?: 0.0
            if (inflow > 0) bal / inflow else 1.0
        }
    }
    return ratio.toFloat().coerceIn(0.04f, 1f)
}

@Composable
private fun JarTile(f: Fund, s: AppState, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Jar(fillOf(f, s), f.colorValue(), Modifier.width(54.dp).height(66.dp))
            Text(f.emoji, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(f.title, fontSize = 11.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(money(s.bal(f)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = f.colorValue(), maxLines = 1)
    }
}

@Composable
private fun AdviceCard(a: Advice, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a.emoji, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontWeight = FontWeight.Bold)
            Text(a.body, style = MaterialTheme.typography.bodySmall)
        }
        if (a.action != null && a.route != null) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(a.action, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun TipCard(tip: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Gold),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("نصيحة اليوم", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
            Text(tip, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
