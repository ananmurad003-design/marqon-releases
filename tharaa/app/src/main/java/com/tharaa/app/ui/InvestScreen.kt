@file:OptIn(ExperimentalLayoutApi::class)

package com.tharaa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tharaa.app.data.AssetKind
import com.tharaa.app.data.Holding
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.dateTimeText
import com.tharaa.app.domain.money
import com.tharaa.app.domain.moneySigned
import com.tharaa.app.domain.parseAmount
import com.tharaa.app.domain.pct
import com.tharaa.app.domain.qty

@Composable
fun InvestScreen(state: AppState, vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle(initialValue = false)
    var buyFor by remember { mutableStateOf<Holding?>(null) }
    var showBuy by remember { mutableStateOf(false) }
    var sellFor by remember { mutableStateOf<Holding?>(null) }
    var valueFor by remember { mutableStateOf<Holding?>(null) }
    var deleteFor by remember { mutableStateOf<Holding?>(null) }

    // تحديث تلقائي للأسعار عند فتح الشاشة إذا آخر تحديث أقدم من 30 دقيقة
    LaunchedEffect(Unit) {
        val stale = state.raw.holdings.any { !it.symbol.isNullOrBlank() && System.currentTimeMillis() - it.updatedAt > 30 * 60_000 }
        if (stale) vm.refreshPrices()
    }

    val holdings = state.raw.holdings.filter { it.quantity > 0.0000001 }
    val pnl = state.portfolioValue - state.portfolioCost
    val pnlPct = if (state.portfolioCost > 0) pnl / state.portfolioCost * 100 else 0.0

    Screen(
        title = "الاستثمار",
        actions = {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { vm.refreshPrices() }) { Icon(Icons.Default.Refresh, "تحديث الأسعار") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DeepGreen)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("قيمة المحفظة", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                        Text(money(state.portfolioValue), color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${moneySigned(pnl)} (${pct(pnlPct)})",
                            color = if (pnl >= 0) androidx.compose.ui.graphics.Color(0xFF8FE3A8) else androidx.compose.ui.graphics.Color(0xFFFF9C9C),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("التكلفة ${money(state.portfolioCost)}، نقد جاهز للاستثمار ${money(state.bal(Fund.INVESTMENT))}", color = Gold, fontSize = 13.sp)
                    }
                }
            }
            item {
                Button(onClick = { buyFor = null; showBuy = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("🛒 شراء أصل من صندوق الاستثمار", fontSize = 16.sp)
                }
            }
            if (holdings.isEmpty()) {
                item {
                    InfoCard(
                        "🌱", "محفظتك فاضية لسا",
                        "ابدأ ولو بمبلغ صغير. صندوق مؤشر مثل VOO أو VT بيعطيك قطعة من مئات الشركات مرة وحدة. " +
                            "وتقدر تسجّل ذهب أو أي أصل يدوياً.",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            }
            holdings.forEach { h ->
                item {
                    HoldingCard(
                        h,
                        onBuy = { buyFor = h; showBuy = true },
                        onSell = { sellFor = h },
                        onValue = { valueFor = h },
                    )
                }
            }
            val closed = state.raw.holdings.filter { it.quantity <= 0.0000001 }
            if (closed.isNotEmpty()) {
                item { SectionTitle("أصول مبيعة") }
                closed.forEach { h ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${AssetKind.emoji(h.kind)} ${h.name}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { deleteFor = h }) { Text("إزالة") }
                        }
                    }
                }
            }
            item {
                InfoCard(
                    "🎩", "تذكير المستثمر الذكي",
                    "السوق بيطلع وبينزل. لا تبيع بخوف لما تشوف أحمر، ولا تشتري بطمع لما الكل متحمس. استثمر بانتظام وخلي الوقت يشتغل.",
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }

    if (showBuy) BuyDialog(state, buyFor, vm) { showBuy = false }
    sellFor?.let { h -> SellDialog(h, vm) { sellFor = null } }
    valueFor?.let { h ->
        var text by remember(h.id) { mutableStateOf(qty(h.value())) }
        FormSheet(
            onDismissRequest = { valueFor = null },
            title = { Text("قيمة ${h.name} الحالية") },
            text = { AmountField(text, { text = it }, "القيمة الإجمالية الآن") },
            confirmButton = { TextButton(onClick = { parseAmount(text)?.let { vm.setManualValue(h, it) }; valueFor = null }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { valueFor = null }) { Text("إلغاء") } },
        )
    }
    deleteFor?.let { h ->
        ConfirmDialog("إزالة ${h.name}", "الأصل مبيع بالكامل. إزالته من القائمة؟", confirm = "إزالة", onConfirm = { vm.deleteHolding(h.id) }, onDismiss = { deleteFor = null })
    }
}

@Composable
private fun HoldingCard(h: Holding, onBuy: () -> Unit, onSell: () -> Unit, onValue: () -> Unit) {
    val value = h.value()
    val pnl = value - h.costIls
    val pnlPct = if (h.costIls > 0) pnl / h.costIls * 100 else 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AssetKind.emoji(h.kind), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(listOfNotNull(h.symbol, "الكمية ${qty(h.quantity)}").joinToString("، "), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(value), fontWeight = FontWeight.Bold)
                    Pill("${moneySigned(pnl)} (${pct(pnlPct)})", if (pnl >= 0) Profit else Loss)
                }
            }
            Spacer(Modifier.height(6.dp))
            val priceLine = when {
                h.lastPriceIls != null -> "سعر الوحدة ${money(h.lastPriceIls)}" + (h.currency?.let { " (بالأصل $it)" } ?: "") +
                    (if (h.updatedAt > 0) "، ${dateTimeText(h.updatedAt)}" else "")
                h.symbol != null -> "ما تحدّث السعر بعد. اضغط 🔄 فوق"
                else -> "قيمة يدوية" + (if (h.updatedAt > 0) "، ${dateTimeText(h.updatedAt)}" else "")
            }
            Text(priceLine, fontSize = 12.sp)
            Text("التكلفة ${money(h.costIls)}", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onBuy) { Text("＋ شراء المزيد") }
                TextButton(onClick = onSell) { Text("بيع") }
                if (h.symbol == null) TextButton(onClick = onValue) { Text("تحديث القيمة") }
            }
        }
    }
}

@Composable
private fun BuyDialog(state: AppState, existing: Holding?, vm: AppViewModel, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(existing?.kind ?: AssetKind.STOCK) }
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var qtyText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    val cash = state.bal(Fund.INVESTMENT)
    val q = parseAmount(qtyText)
    val cost = parseAmount(costText)
    val enough = cost != null && cost <= cash + 0.004
    val ok = q != null && q > 0 && cost != null && cost > 0 && enough && (existing != null || name.isNotBlank() || symbol.isNotBlank())

    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "شراء المزيد من ${existing.name}" else "شراء أصل جديد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المتاح بصندوق الاستثمار: ${money(cash)}", fontWeight = FontWeight.Bold)
                if (existing == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(AssetKind.STOCK, AssetKind.GOLD, AssetKind.CRYPTO, AssetKind.OTHER).forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text("${AssetKind.emoji(k)} ${AssetKind.title(k)}") })
                        }
                    }
                    TextInput(symbol, { symbol = it }, "الرمز للسعر الحي (اختياري)")
                    Text(
                        when (kind) {
                            AssetKind.STOCK -> "أمثلة: VOO, SPY, AAPL, TEVA.TA (تل أبيب)"
                            AssetKind.GOLD -> "GC=F = سعر أونصة الذهب. الكمية بالأونصات (1 أونصة ≈ 31.1 غرام). أو اترك الرمز فاضي وحدّث القيمة يدوياً."
                            AssetKind.CRYPTO -> "أمثلة: BTC-USD, ETH-USD"
                            else -> "بدون رمز: بتحدّث قيمته يدوياً"
                        },
                        fontSize = 12.sp,
                    )
                    TextInput(name, { name = it }, "الاسم (مثلاً: صندوق S&P 500)")
                }
                NumberField(qtyText, { qtyText = it }, "الكمية (عدد الأسهم/الوحدات)")
                AmountField(costText, { costText = it }, "المبلغ اللي دفعته بالشيكل (مع العمولة)", isError = cost != null && !enough)
                if (cost != null && !enough) {
                    WarnBox(com.tharaa.app.domain.Warn("المبلغ أكبر من صندوق الاستثمار. لا تستثمر بفلوس الطوارئ أو المصروف، ولا بالدين أبداً.", severe = true))
                }
                if (q != null && q > 0 && cost != null && cost > 0) Text("متوسط سعر الوحدة: ${money(cost / q)}", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val finalName = name.ifBlank { symbol.uppercase() }
                vm.buy(existing, kind, if (existing != null) existing.symbol else symbol, if (existing != null) existing.name else finalName, q ?: 0.0, cost ?: 0.0)
                onDismiss()
            }) { Text("اشتري") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun SellDialog(h: Holding, vm: AppViewModel, onDismiss: () -> Unit) {
    var qtyText by remember { mutableStateOf(qty(h.quantity)) }
    var proceedsText by remember { mutableStateOf("") }
    val q = parseAmount(qtyText)
    val proceeds = parseAmount(proceedsText)
    val ok = q != null && q > 0 && q <= h.quantity + 0.0000001 && proceeds != null && proceeds >= 0
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("بيع ${h.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("عندك ${qty(h.quantity)}، القيمة الحالية ${money(h.value())}")
                NumberField(qtyText, { qtyText = it }, "الكمية المباعة")
                AmountField(proceedsText, { proceedsText = it }, "المبلغ اللي استلمته بالشيكل")
                WarnBox(com.tharaa.app.domain.Warn("قبل ما تبيع: هل السبب خطة واضحة، ولا خوف من نزول السوق؟ المال بيرجع لصندوق الاستثمار، مش للمصروف."))
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { vm.sell(h, q ?: 0.0, proceeds ?: 0.0); onDismiss() }) { Text("بيع") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
