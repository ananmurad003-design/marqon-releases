package com.tharaa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tharaa.app.domain.Advisor
import com.tharaa.app.domain.Allocation
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.money
import com.tharaa.app.domain.parseAmount
import com.tharaa.app.domain.pct
import com.tharaa.app.domain.qty

@Composable
fun IncomeScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<Allocation?>(null) }
    val edits = remember { mutableStateMapOf<Fund, String>() }
    var showAll by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val amount = parseAmount(amountText)

    fun makePlan() {
        val a = amount ?: return
        if (a <= 0) return
        val p = vm.plan(a) ?: return
        plan = p
        edits.clear()
        p.lines.forEach { (f, v) -> edits[f] = qty(v) }
        showAll = false
    }

    Screen(title = "دخل جديد", onBack = onBack) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AmountField(
                value = amountText,
                onValueChange = { amountText = it; plan = null },
                label = "كم دخلك؟",
                isError = amountText.isNotBlank() && amount == null,
            )
            TextInput(source, { source = it }, "المصدر (اختياري): مشروع، عميل…")
            if (plan == null) {
                Button(
                    onClick = { makePlan() },
                    enabled = amount != null && amount > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("🧠 اقترح عليّ التقسيم", fontSize = 16.sp) }
                if (state.avgMonthlyIncome > 0) {
                    Text(
                        "متوسط دخلك الشهري: ${money(state.avgMonthlyIncome)}، دخلك هالشهر لحد الآن: ${money(state.monthIncome)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val p = plan
            val input = state.input
            if (p != null && input != null && amount != null) {
                val lines = Fund.entries.associateWith { parseAmount(edits[it] ?: "") ?: 0.0 }
                val warnings = Advisor.review(lines, p.lines, amount, input)
                val sum = lines.values.sum()

                InfoCard(
                    "🧭", "مرحلتك: ${p.phase.title}", p.phase.description,
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
                p.notes.forEach { n -> InfoCard("💡", "", n, container = MaterialTheme.colorScheme.secondaryContainer) }

                SectionTitle("خطة التقسيم")
                val visible = Fund.entries.filter { showAll || (p.lines[it] ?: 0.0) > 0 || (parseAmount(edits[it] ?: "") ?: 0.0) > 0 }
                visible.forEach { f ->
                    AllocationRow(
                        fund = f,
                        value = edits[f] ?: "",
                        percent = if (amount > 0) (parseAmount(edits[f] ?: "") ?: 0.0) / amount * 100 else 0.0,
                        reason = Advisor.reason(f, input),
                        onChange = { edits[f] = it },
                    )
                }
                if (!showAll) TextButton(onClick = { showAll = true }) { Text("إظهار باقي الصناديق") }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("المجموع: ${money(sum)} من ${money(amount)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { makePlan() }) { Text("↺ اقتراح المستشار") }
                }
                warnings.forEach { WarnBox(it) }

                TextInput(note, { note = it }, "ملاحظة (اختياري)")
                Button(
                    onClick = { saving = true; vm.saveIncome(amount, source, note, p, lines, onDone = onBack) },
                    enabled = !saving && warnings.none { it.severe },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("✅ اعتماد التقسيم", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun AllocationRow(fund: Fund, value: String, percent: Double, reason: String, onChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FundIcon(fund, 26.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(fund.title, fontWeight = FontWeight.Bold)
                    Text(pct(percent), color = fund.colorValue(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    suffix = { Text("₪") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(130.dp),
                )
            }
            Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
