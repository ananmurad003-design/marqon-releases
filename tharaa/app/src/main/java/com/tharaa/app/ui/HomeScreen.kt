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
        title = "ثراء 👑",
        actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "الإعدادات") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NetWorthCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nav.navigate("income") }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text("＋ دخل جديد", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { nav.navigate("expense") }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text("－ مصروف", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { PhaseCard(state) }
            adviceFor(state).forEach { a ->
                item {
                    InfoCard(a.emoji, a.title, a.body, container = MaterialTheme.colorScheme.secondaryContainer) {
                        if (a.action != null && a.route != null) {
                            FilledTonalButton(onClick = { if (a.route in listOf("plan", "invest", "expenses")) nav.goTab(a.route) else nav.navigate(a.route) }) { Text(a.action) }
                        }
                    }
                }
            }
            item { SectionTitle("صناديقك") }
            Fund.entries.chunked(2).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { f -> FundTile(f, state, Modifier.weight(1f)) { nav.navigate("fund/${f.key}") } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            val goals = state.goals.filter { !it.goal.done }
            if (goals.isNotEmpty()) {
                item { SectionTitle("أهدافك") }
                goals.forEach { g ->
                    item {
                        Card(Modifier.fillMaxWidth().clickable { nav.goTab("plan") }) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${g.goal.emoji} ${g.goal.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("${(g.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = Gold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Progress(g.progress, Gold)
                                Spacer(Modifier.height(4.dp))
                                Text("${money(g.saved)} من ${money(g.goal.target)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                InfoCard("💡", "نصيحة اليوم", Lessons.tipOfTheDay(), container = MaterialTheme.colorScheme.primaryContainer)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun NetWorthCard(s: AppState) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(DeepGreen, Color(0xFF1F6B4A))))
            .padding(20.dp),
    ) {
        Column {
            Text("صافي ثروتك", color = Color.White.copy(alpha = 0.8f))
            Text(money(s.netWorth), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row {
                Stat("نقد بالصناديق", money(s.cashTotal), Modifier.weight(1f))
                Stat("المحفظة", money(s.portfolioValue), Modifier.weight(1f))
                Stat("الديون", money(s.debtRemaining), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${monthTitle(s.currentMonth)}: دخل ${money(s.monthIncome)} · مصاريف ${money(s.monthExpenses)}",
                color = Gold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun PhaseCard(s: AppState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("مرحلتك ${s.phase.step} من 4", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(s.phase.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Phase.entries.forEach { p ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (p.step <= s.phase.step) Gold else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(s.phase.description, style = MaterialTheme.typography.bodyMedium)
            if (s.settings.isCustom) {
                Spacer(Modifier.height(6.dp))
                Pill("تستخدم نسب مخصصة", MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun FundTile(f: Fund, s: AppState, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(f.title, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(money(s.bal(f)), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = f.colorValue())
            when (f) {
                Fund.EMERGENCY -> {
                    Spacer(Modifier.height(4.dp))
                    Progress((s.bal(f) / s.emergencyTarget.coerceAtLeast(1.0)).toFloat(), f.colorValue())
                    Text("الهدف ${money(s.emergencyTarget)}", fontSize = 11.sp)
                }
                Fund.DEBT -> if (s.debtRemaining > 0) Text("متبقي ديون ${money(s.debtRemaining)}", fontSize = 11.sp)
                else -> {}
            }
        }
    }
}
