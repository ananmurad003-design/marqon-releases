@file:OptIn(ExperimentalLayoutApi::class)

package com.tharaa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tharaa.app.data.Debt
import com.tharaa.app.data.GoalKind
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.DebtInfo
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.GoalInfo
import com.tharaa.app.domain.K
import com.tharaa.app.domain.money
import com.tharaa.app.domain.parseAmount
import com.tharaa.app.domain.qty

@Composable
fun PlanScreen(state: AppState, vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(if (state.raw.debts.any() && state.debtRemaining > 0) 0 else 1) }
    Screen(title = "الديون والأهداف 🎯") { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("⛓️ الديون") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🎯 الأهداف") })
            }
            if (tab == 0) DebtsTab(state, vm) else GoalsTab(state, vm)
        }
    }
}

@Composable
private fun DebtsTab(state: AppState, vm: AppViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editDebt by remember { mutableStateOf<Debt?>(null) }
    var payFor by remember { mutableStateOf<DebtInfo?>(null) }
    var deleteFor by remember { mutableStateOf<DebtInfo?>(null) }
    val total = state.debts.sumOf { it.debt.total }
    val paid = state.debts.sumOf { it.paid }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("متبقي عليك", style = MaterialTheme.typography.bodyMedium)
                    Text(money(state.debtRemaining), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (state.debtRemaining > 0) Loss else Profit)
                    if (total > 0) {
                        Spacer(Modifier.height(6.dp))
                        Progress((paid / total).toFloat(), Profit)
                        Text("سدّدت ${money(paid)} من ${money(total)}", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("جاهز للسداد بصندوق الديون: ${money(state.bal(Fund.DEBT))}", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Column {
                Text("طريقة السداد", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.debtStrategy == "snowball",
                        onClick = { vm.setSetting(K.DEBT_STRATEGY, "snowball") },
                        label = { Text("❄️ كرة الثلج (الأصغر أولاً)") },
                    )
                    FilterChip(
                        selected = state.settings.debtStrategy == "avalanche",
                        onClick = { vm.setSetting(K.DEBT_STRATEGY, "avalanche") },
                        label = { Text("🏔️ الأعلى فائدة أولاً") },
                    )
                }
                Text(
                    if (state.settings.debtStrategy == "snowball") "انتصارات سريعة بتعطيك دافع تكمل (ديف رامزي)."
                    else "أوفر رياضياً إذا ديونك عليها فوائد مختلفة.",
                    fontSize = 12.sp,
                )
            }
        }
        item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة دين") } }
        if (state.debts.isEmpty()) {
            item { InfoCard("🕊️", "ما في ديون مسجّلة", "إذا عليك أي دين (لصاحب، للبنك، تقسيط) سجّله حتى يعطيه المستشار الأولوية الصح.") }
        }
        state.debts.forEachIndexed { index, d ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.debt.name, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    if (d.isPaid) Pill("تم السداد 🎉", Profit)
                                    else if (index == 0) Pill("الهدف الحالي", Gold)
                                }
                                Text(
                                    "متبقي ${money(d.remaining)} من ${money(d.debt.total)}" +
                                        (if (d.debt.interestRate > 0) " · فائدة ${qty(d.debt.interestRate)}%" else ""),
                                    fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = { editDebt = d.debt }) { Icon(Icons.Default.Edit, "تعديل") }
                            IconButton(onClick = { deleteFor = d }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                        Progress(d.progress, if (d.isPaid) Profit else Fund.DEBT.colorValue())
                        if (!d.isPaid) {
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = { payFor = d }) { Text("سدّد دفعة") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd || editDebt != null) DebtDialog(editDebt, vm) { showAdd = false; editDebt = null }
    payFor?.let { d -> PayDialog(d, state, vm) { payFor = null } }
    deleteFor?.let { d ->
        ConfirmDialog(
            "حذف ${d.debt.name}", "بينحذف الدين من القائمة. الدفعات اللي سدّدتها بتضل مسجلة بحركاتك.",
            confirm = "احذف", onConfirm = { vm.deleteDebt(d.debt.id) }, onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun DebtDialog(existing: Debt?, vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var total by remember { mutableStateOf(existing?.total?.let { qty(it) } ?: "") }
    var rate by remember { mutableStateOf(existing?.interestRate?.takeIf { it > 0 }?.let { qty(it) } ?: "") }
    val t = parseAmount(total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "دين جديد" else "تعديل الدين") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "لمين؟ (مثلاً: قرض البنك، أبو محمد)")
                AmountField(total, { total = it }, "المبلغ الكلي للدين")
                NumberField(rate, { rate = it }, "نسبة الفائدة السنوية (إن وجدت)", suffix = "%")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && t != null && t > 0, onClick = {
                val r = parseAmount(rate) ?: 0.0
                if (existing == null) vm.addDebt(name, t ?: 0.0, r, "")
                else vm.updateDebt(existing.copy(name = name.trim(), total = t ?: existing.total, interestRate = r))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PayDialog(d: DebtInfo, state: AppState, vm: AppViewModel, onDismiss: () -> Unit) {
    val debtCash = state.bal(Fund.DEBT)
    var from by remember { mutableStateOf(Fund.DEBT) }
    var text by remember { mutableStateOf(qty(minOf(d.remaining, debtCash).coerceAtLeast(0.0))) }
    val amount = parseAmount(text)
    val ok = amount != null && amount > 0 && amount <= state.bal(from) + 0.004 && amount <= d.remaining + 0.004
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سداد: ${d.debt.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("متبقي ${money(d.remaining)}")
                AmountField(text, { text = it }, "المبلغ")
                Text("من صندوق:")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Fund.DEBT, Fund.PERSONAL, Fund.EMERGENCY).forEach { f ->
                        FilterChip(selected = from == f, onClick = { from = f }, label = { Text("${f.emoji} ${f.title} (${money(state.bal(f))})") })
                    }
                }
                if (from == Fund.PERSONAL) WarnBox(com.tharaa.app.domain.Warn("قرار قوي! التضحية بالمصروف لتصفير الدين أسرع 💪", positive = true))
                if (from == Fund.EMERGENCY) WarnBox(com.tharaa.app.domain.Warn("انتبه: إذا فضّيت الطوارئ، أول مفاجأة بترجعك للدين. خلي على الأقل مصاريف شهر."))
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { vm.payDebt(d.debt, amount ?: 0.0, from, d.remaining); onDismiss() }) { Text("سدّد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun GoalsTab(state: AppState, vm: AppViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<GoalInfo?>(null) }
    var deleteFor by remember { mutableStateOf<GoalInfo?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            InfoCard(
                "🔥", "هدف محدد ومكتوب",
                "رتّب أهدافك حسب الأهمية. حصة «الأهداف» من كل دخل بتعبّي الهدف الأول، ولما يكتمل بتنتقل للي بعده.",
                container = MaterialTheme.colorScheme.primaryContainer,
            )
        }
        item { Text("مدخرات الأهداف: ${money(state.bal(Fund.GOALS))}", fontWeight = FontWeight.Bold) }
        item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ هدف جديد") } }
        state.goals.forEachIndexed { index, g ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.goal.emoji, fontSize = 26.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. ${g.goal.name}", fontWeight = FontWeight.Bold)
                                Text(
                                    if (g.goal.kind == GoalKind.PORTFOLIO) "يقاس بقيمة محفظتك + نقد الاستثمار" else "ادخار",
                                    fontSize = 11.sp,
                                )
                            }
                            IconButton(onClick = { vm.moveGoal(g.goal, up = true) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "أعلى") }
                            IconButton(onClick = { vm.moveGoal(g.goal, up = false) }, enabled = index < state.goals.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "أسفل") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Progress(g.progress, if (g.reached) Profit else Gold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${money(g.saved)} من ${money(g.goal.target)}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${(g.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = Gold)
                        }
                        if (g.reached && !g.goal.done) {
                            Spacer(Modifier.height(6.dp))
                            InfoCard(
                                "🎉", "حققت هدفك!", "مبروك! لما تصرف عليه، سجّل المصروف من صندوق الأهداف، وبعدها علّمه كمنجز.",
                                container = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                FilledTonalButton(onClick = { vm.updateGoal(g.goal.copy(done = true)) }) { Text("✅ علّمه كمنجز") }
                            }
                        }
                        if (g.goal.done) Pill("منجز ✅", Profit)
                        Row {
                            TextButton(onClick = { editFor = g }) { Text("تعديل") }
                            TextButton(onClick = { deleteFor = g }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd || editFor != null) GoalDialog(editFor, vm) { showAdd = false; editFor = null }
    deleteFor?.let { g ->
        ConfirmDialog(
            "حذف ${g.goal.name}",
            if (g.goal.kind == GoalKind.SAVING && g.saved > 0) "المدخرات (${money(g.saved)}) بترجع لصندوق الاستثمار." else "بينحذف الهدف.",
            confirm = "احذف", onConfirm = { vm.deleteGoal(g) }, onDismiss = { deleteFor = null },
        )
    }
}

private val goalEmojis = listOf("💍", "🚗", "🏠", "📈", "✈️", "🎓", "💻", "🕋", "👶", "🏪", "🎯")

@Composable
private fun GoalDialog(existing: GoalInfo?, vm: AppViewModel, onDismiss: () -> Unit) {
    val g = existing?.goal
    var name by remember { mutableStateOf(g?.name ?: "") }
    var emoji by remember { mutableStateOf(g?.emoji ?: "🎯") }
    var target by remember { mutableStateOf(g?.target?.let { qty(it) } ?: "") }
    var kind by remember { mutableStateOf(g?.kind ?: GoalKind.SAVING) }
    val t = parseAmount(target)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (g == null) "هدف جديد" else "تعديل الهدف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "اسم الهدف")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    goalEmojis.forEach { e -> FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) }) }
                }
                AmountField(target, { target = it }, "المبلغ المطلوب")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = kind == GoalKind.SAVING, onClick = { kind = GoalKind.SAVING }, label = { Text("💰 ادخار") })
                    FilterChip(selected = kind == GoalKind.PORTFOLIO, onClick = { kind = GoalKind.PORTFOLIO }, label = { Text("📈 حجم المحفظة") })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && t != null && t > 0, onClick = {
                if (g == null) vm.addGoal(name, emoji, t ?: 0.0, kind)
                else vm.updateGoal(g.copy(name = name.trim(), emoji = emoji, target = t ?: g.target, kind = kind, done = false))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
