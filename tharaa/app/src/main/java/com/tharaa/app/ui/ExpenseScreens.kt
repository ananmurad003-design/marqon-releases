@file:OptIn(ExperimentalLayoutApi::class)

package com.tharaa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tharaa.app.data.GoalKind
import com.tharaa.app.data.T
import com.tharaa.app.data.Txn
import com.tharaa.app.domain.Advisor
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Categories
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.addMonths
import com.tharaa.app.domain.dateText
import com.tharaa.app.domain.dateTimeText
import com.tharaa.app.domain.money
import com.tharaa.app.domain.moneySigned
import com.tharaa.app.domain.monthKey
import com.tharaa.app.domain.monthTitle
import com.tharaa.app.domain.parseAmount

private val categoryPalette = listOf(
    androidx.compose.ui.graphics.Color(0xFF1B4D3E), androidx.compose.ui.graphics.Color(0xFFC9A227),
    androidx.compose.ui.graphics.Color(0xFF4F7CAC), androidx.compose.ui.graphics.Color(0xFFD8693B),
    androidx.compose.ui.graphics.Color(0xFF7B5EA7), androidx.compose.ui.graphics.Color(0xFF3E9C8F),
    androidx.compose.ui.graphics.Color(0xFFB0475A), androidx.compose.ui.graphics.Color(0xFF8A9A5B),
)

private val spendableFunds = listOf(Fund.ESSENTIALS, Fund.PERSONAL, Fund.EDUCATION, Fund.GIVING, Fund.EMERGENCY, Fund.GOALS)

@Composable
fun ExpenseScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.all.first()) }
    var fund by remember { mutableStateOf(Fund.ESSENTIALS) }
    var fundTouched by remember { mutableStateOf(false) }
    var goalId by remember { mutableStateOf(state.goals.firstOrNull { it.goal.kind == GoalKind.SAVING }?.goal?.id) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val amount = parseAmount(amountText)

    // اختيار ذكي للصندوق: صندوق التصنيف، وإذا ما فيه رصيد كافي، مصروفك
    fun autoFund(cat: com.tharaa.app.domain.Category, a: Double?) {
        if (fundTouched) return
        val need = a ?: 0.0
        fund = if (state.bal(cat.fund) >= need && state.bal(cat.fund) > 0) cat.fund
        else if (cat.fund == Fund.ESSENTIALS && state.bal(Fund.PERSONAL) >= need) Fund.PERSONAL
        else cat.fund
    }

    val available = state.bal(fund)
    val goalBalance = state.goals.firstOrNull { it.goal.id == goalId }?.saved ?: 0.0
    val enough = amount != null && amount > 0 && amount <= (if (fund == Fund.GOALS) goalBalance else available) + 0.004

    Screen(title = "مصروف جديد", onBack = onBack) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AmountField(amountText, { amountText = it; autoFund(category, parseAmount(it)) }, "كم صرفت؟")
            SectionTitle("على شو؟")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Categories.all.forEach { c ->
                    FilterChip(
                        selected = category.key == c.key,
                        onClick = { category = c; autoFund(c, amount) },
                        label = { Text("${c.emoji} ${c.title}") },
                    )
                }
            }
            SectionTitle("من أي صندوق؟")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                spendableFunds.forEach { f ->
                    FilterChip(
                        selected = fund == f,
                        onClick = { fund = f; fundTouched = true },
                        leadingIcon = { FundIcon(f, 18.dp) }, label = { Text("${f.title} (${money(state.bal(f))})") },
                    )
                }
            }
            if (fund == Fund.GOALS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.goals.filter { it.goal.kind == GoalKind.SAVING }.forEach { g ->
                        FilterChip(
                            selected = goalId == g.goal.id,
                            onClick = { goalId = g.goal.id },
                            label = { Text("${g.goal.emoji} ${g.goal.name} (${money(g.saved)})") },
                        )
                    }
                }
            }
            Advisor.expenseWarning(fund)?.let { WarnBox(com.tharaa.app.domain.Warn(it)) }
            if (amount != null && amount > 0 && !enough) {
                WarnBox(
                    com.tharaa.app.domain.Warn(
                        "رصيد «${fund.title}» ما بيكفي (${money(if (fund == Fund.GOALS) goalBalance else available)}). " +
                            "اختار صندوق ثاني، أو سجّل دخل أول. الصرف من فلوس مش موجودة هو بداية الدين.",
                        severe = true,
                    ),
                )
            }
            TextInput(note, { note = it }, "ملاحظة (اختياري)")
            Button(
                onClick = {
                    saving = true
                    vm.addExpense(amount ?: 0.0, category.key, fund, note, if (fund == Fund.GOALS) goalId else null, onDone = onBack)
                },
                enabled = enough && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("سجّل المصروف", fontSize = 16.sp) }
        }
    }
}

@Composable
fun ExpensesScreen(state: AppState, vm: AppViewModel, nav: NavHostController) {
    var offset by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val now = System.currentTimeMillis()
    val month = monthKey(addMonths(now, offset))
    val prevMonth = monthKey(addMonths(now, offset - 1))

    val expenses = state.raw.txns.filter { it.type == T.EXPENSE && monthKey(it.timestamp) == month }
    val total = -expenses.sumOf { it.amount }
    val prevTotal = -state.raw.txns.filter { it.type == T.EXPENSE && monthKey(it.timestamp) == prevMonth }.sumOf { it.amount }
    val byCat = expenses.groupBy { if (it.category?.startsWith("commitment") == true) "commitment" else it.category }.mapValues { e -> -e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    val incomes = state.raw.incomes.filter { monthKey(it.timestamp) == month }

    Screen(title = "المصاريف والدخل") { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset-- }) { Text("→ السابق") }
                    Text(monthTitle(month), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { offset++ }, enabled = offset < 0) { Text("التالي ←") }
                }
            }
            item {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("المصاريف") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الدخل") })
                }
            }
            if (tab == 0) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("مجموع المصاريف", style = MaterialTheme.typography.bodyMedium)
                            Text(money(total), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            if (prevTotal > 0) {
                                val diff = total - prevTotal
                                Text(
                                    if (diff > 0) "▲ أكثر من الشهر الماضي بـ ${money(diff)}" else "▼ أقل من الشهر الماضي بـ ${money(-diff)} 👏",
                                    color = if (diff > 0) Loss else Profit,
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { nav.navigate("expense") }) { Text("＋ مصروف جديد") }
                        }
                    }
                }
                if (byCat.isNotEmpty()) {
                    item { SectionTitle("حسب التصنيف") }
                    item {
                        val palette = categoryPalette
                        val slices = byCat.mapIndexed { i, (key, v) -> Slice(Categories.of(key).title, v, palette[i % palette.size]) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Donut(slices, money(total), "${byCat.size} تصنيف")
                                Spacer(Modifier.height(14.dp))
                                byCat.forEachIndexed { i, (key, v) ->
                                    val c = Categories.of(key)
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.size(10.dp).background(palette[i % palette.size], androidx.compose.foundation.shape.CircleShape),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("${c.emoji} ${c.title}", modifier = Modifier.weight(1f))
                                        Text("${money(v)}، ${(v / total * 100).toInt()}%", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("السجل") }
                if (expenses.isEmpty()) item { Text("ما في مصاريف مسجلة لهذا الشهر.") }
                expenses.forEach { t ->
                    item {
                        TxnRow(t, title = Categories.of(t.category).let { "${it.emoji} ${it.title}" }) {
                            toDelete = Pair("حذف مصروف ${money(-t.amount)}؟ المبلغ بيرجع لصندوقه.", { vm.deleteTxn(t.id); Unit })
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("مجموع الدخل", style = MaterialTheme.typography.bodyMedium)
                            Text(money(incomes.sumOf { it.amount }), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Profit)
                            if (state.avgMonthlyIncome > 0) Text("متوسطك آخر 3 شهور: ${money(state.avgMonthlyIncome)}", fontSize = 13.sp)
                        }
                    }
                }
                if (incomes.isEmpty()) item { Text("ما في دخل مسجل لهذا الشهر.") }
                incomes.forEach { inc ->
                    item {
                        val parts = state.raw.txns.filter { it.incomeId == inc.id }
                            .groupBy { it.fund }.map { (k, v) -> "${Fund.of(k).emoji} ${money(v.sumOf { it.amount })}" }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${money(inc.amount)}  ${inc.source}", fontWeight = FontWeight.Bold)
                                    Text(dateText(inc.timestamp), fontSize = 12.sp)
                                    Text(parts.joinToString("  "), fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    toDelete = Pair("حذف دخل ${money(inc.amount)}؟ بينحذف تقسيمه من كل الصناديق.", { vm.deleteIncome(inc.id); Unit })
                                }) { Icon(Icons.Default.Delete, "حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { (text, action) ->
        ConfirmDialog("تأكيد الحذف", text, confirm = "احذف", onConfirm = action, onDismiss = { toDelete = null })
    }
}

@Composable
fun TxnRow(t: Txn, title: String, onDelete: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                val sub = listOfNotNull(dateTimeText(t.timestamp), Fund.of(t.fund).title, t.note.ifBlank { null })
                Text(sub.joinToString("، "), fontSize = 12.sp, maxLines = 2)
            }
            Text(moneySigned(t.amount), fontWeight = FontWeight.Bold, color = if (t.amount >= 0) Profit else Loss)
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف") }
        }
    }
}

private fun txnTitle(t: Txn): String = when (t.type) {
    T.ALLOC -> "💵 من دخل"
    T.EXPENSE -> Categories.of(t.category).let { "${it.emoji} ${it.title}" }
    T.TRANSFER -> "🔁 تحويل"
    T.DEBT_PAY -> "⛓️ سداد دين"
    T.BUY -> "🛒 شراء أصل"
    T.SELL -> "💰 بيع أصل"
    T.ROLLOVER -> "📅 ترحيل نهاية الشهر"
    else -> "✏️ تعديل"
}

@Composable
fun FundScreen(fund: Fund, state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    var showTransfer by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    val txns = state.raw.txns.filter { it.fund == fund.key }

    Screen(title = fund.title, onBack = onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FundIcon(fund, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("الرصيد", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(money(state.bal(fund)), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = fund.colorValue())
                        if (fund == Fund.EMERGENCY) {
                            Spacer(Modifier.height(6.dp))
                            Progress((state.bal(fund) / state.emergencyTarget.coerceAtLeast(1.0)).toFloat(), fund.colorValue())
                            Text("الهدف ${money(state.emergencyTarget)} = ${state.settings.emergencyMonths} شهور × ${money(state.monthlyBase)}", fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (fund != Fund.GOALS) Button(onClick = { showTransfer = true }) { Text("🔁 تحويل") }
                            if (fund != Fund.GOALS) OutlinedButton(onClick = { showAdjust = true }) { Text("✏️ تعديل الرصيد") }
                        }
                    }
                }
            }
            item { InfoCard("🎓", "شو هذا الصندوق؟", fund.about, container = MaterialTheme.colorScheme.primaryContainer) }
            item { SectionTitle("الحركات") }
            if (txns.isEmpty()) item { Text("ما في حركات بعد.") }
            txns.forEach { t ->
                item { TxnRow(t, txnTitle(t)) }
            }
        }
    }

    if (showTransfer) TransferDialog(fund, state, vm) { showTransfer = false }
    if (showAdjust) {
        var text by remember { mutableStateOf(com.tharaa.app.domain.qty(state.bal(fund))) }
        FormSheet(
            onDismissRequest = { showAdjust = false },
            title = { Text("تعديل رصيد ${fund.title}") },
            text = {
                Column {
                    Text("استخدمه بس لتصحيح خطأ أو لإدخال رصيد موجود عندك أصلاً قبل التطبيق.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    AmountField(text, { text = it }, "الرصيد الصحيح")
                }
            },
            confirmButton = {
                TextButton(onClick = { parseAmount(text)?.let { vm.adjust(fund, it) }; showAdjust = false }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAdjust = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun TransferDialog(from: Fund, state: AppState, vm: AppViewModel, onDismiss: () -> Unit) {
    var to by remember { mutableStateOf(if (from == Fund.INVESTMENT) Fund.EMERGENCY else Fund.INVESTMENT) }
    var text by remember { mutableStateOf("") }
    val amount = parseAmount(text)
    val ok = amount != null && amount > 0 && amount <= state.bal(from) + 0.004 && to != from
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("تحويل من ${from.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AmountField(text, { text = it }, "المبلغ (المتاح ${money(state.bal(from))})")
                Spacer(Modifier.height(8.dp))
                Text("إلى:")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fund.entries.filter { it != from }.forEach { f ->
                        FilterChip(selected = to == f, onClick = { to = f }, leadingIcon = { FundIcon(f, 18.dp) }, label = { Text("${f.title}") })
                    }
                }
                Advisor.transferWarning(from, to)?.let { WarnBox(com.tharaa.app.domain.Warn(it)) }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { vm.transfer(from, to, amount ?: 0.0, ""); onDismiss() }) { Text("حوّل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
