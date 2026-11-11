@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.tharaa.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Coach
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.Insight
import com.tharaa.app.domain.Investors
import com.tharaa.app.domain.K
import com.tharaa.app.domain.ModelPortfolio
import com.tharaa.app.domain.money
import com.tharaa.app.domain.one
import com.tharaa.app.domain.pct
import com.tharaa.app.domain.qty
import com.tharaa.app.domain.stockVerdict
import com.tharaa.app.domain.suggestPlan
import kotlin.math.floor

@Composable
fun CoachScreen(state: AppState, vm: AppViewModel, onLesson: (Int) -> Unit = {}, onSummaries: () -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    Screen(title = "المدرب") { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("تقريري") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الاستثمار الذكي") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("المنهج") })
            }
            when (tab) {
                0 -> ReportTab(state)
                1 -> SmartInvestTab(state, vm)
                else -> CourseList(state, onLesson, onSummaries)
            }
        }
    }
}

// ---------------- تقريري ----------------

@Composable
private fun ReportTab(state: AppState) {
    val metrics = remember(state) { Coach.metrics(state) }
    val insights = remember(state) { Coach.report(state) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (metrics != null) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(DeepGreen)
                        .padding(18.dp),
                ) {
                    Text("المدرب قرأ أرقامك لآخر 3 شهور", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Metric("بناء الثروة", pct(metrics.savingsRate), "من دخلك", Modifier.weight(1f))
                        Metric("درع الطوارئ", one(metrics.runwayMonths), "شهر", Modifier.weight(1f))
                        Metric("استثمار شهري", money(metrics.monthlyInvest), "بالمتوسط", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("لو كمّلت بنفس الوتيرة", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(metrics.in10y), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("  بعد 10 سنين", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                    Text("و ${money(metrics.in20y)} بعد 20 سنة (عائد متوقع 8%، مش مضمون)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
        insights.forEach { i -> item { InsightCard(i) } }
        item {
            Text(
                "المدرب بيحسب من بياناتك المسجلة فقط. كل ما سجّلت أدق، كل ما صارت قراءته أدق.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        Text(unit, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
    }
}

@Composable
private fun InsightCard(i: Insight) {
    val accent = when (i.level) {
        0 -> Profit
        2 -> Color(0xFFD08700)
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(12.dp))
        Text(i.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(i.title, fontWeight = FontWeight.Bold)
            Text(i.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------- الاستثمار الذكي ----------------

@Composable
private fun SmartInvestTab(state: AppState, vm: AppViewModel) {
    val halal = state.settings.halalOnly
    val cash = state.bal(Fund.INVESTMENT)
    val plan = remember(cash, state.phase, halal, state.portfolioValue) { suggestPlan(cash, state.phase, halal, state.portfolioValue) }
    val prices by vm.planPrices.collectAsStateWithLifecycle()
    val report by vm.stock.collectAsStateWithLifecycle()
    val loading by vm.stockLoading.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(plan.lines.map { it.symbol }) { vm.loadPlanPrices(plan.lines.map { it.symbol }) }

    fun read(sym: String) {
        symbol = sym
        vm.analyzeStock(sym)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "⚖️ هاي قراءات تعليمية من قواعد كبار المستثمرين، مش نصيحة استثمار مرخّصة. القرار وتحمّل المخاطرة إلك.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🕌 استثمار حلال فقط", fontWeight = FontWeight.Bold)
                    Text("الخطة بتقترح صناديق متوافقة مع الشريعة", fontSize = 12.sp)
                }
                Switch(checked = halal, onCheckedChange = { vm.setSetting(K.HALAL, it.toString()) })
            }
        }

        // خطتي لمالي
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🧭 خطتي لمالي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("بصندوق الاستثمار: ${money(cash)}", fontWeight = FontWeight.Bold, color = Fund.INVESTMENT.colorValue())
                    Text(plan.headline, style = MaterialTheme.typography.bodyMedium)
                    plan.lines.forEach { l ->
                        val p = prices[l.symbol]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { read(l.symbol) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${l.symbol}، ${l.name}", fontWeight = FontWeight.Bold)
                                Text(l.why, fontSize = 12.sp)
                                if (p != null && p > 0) {
                                    val n = l.amount / p
                                    Text(
                                        "السعر ${money(p)} → " + if (n >= 1) "حوالي ${floor(n).toInt()} سهم" else "جزء من سهم (${qty(n)})",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(l.amount), fontWeight = FontWeight.Bold)
                                Text("${l.share}%", fontSize = 12.sp)
                            }
                        }
                    }
                    plan.tips.forEach { Text("• $it", fontSize = 13.sp) }
                    Text("اضغط على أي رمز لتقرأه. ولما تشتري، سجّل الشراء من تبويب الاستثمار.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }

        // قارئ الأسهم
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔎 اقرأ سهم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = symbol,
                            onValueChange = { symbol = it },
                            label = { Text("الرمز: VOO، AAPL، TEVA.TA…") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { read(symbol) }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { read(symbol) }, enabled = !loading && symbol.isNotBlank()) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("اقرأ")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val quick = if (halal) listOf("SPUS", "HLAL", "UMMA") else listOf("VOO", "BRK-B", "AAPL", "KO", "QQQ", "TSLA")
                        quick.forEach { s -> AssistChip(onClick = { read(s) }, label = { Text(s) }) }
                    }
                    report?.let { r -> StockReportView(r, state) }
                }
            }
        }

        // محافظ الأغنياء
        item { SectionTitle("محافظ وفلسفات الأغنياء") }
        Investors.portfolios.filter { !halal || it.halal || it.holdings.isEmpty() }.forEach { p ->
            item {
                PortfolioCard(p, open = openId == p.id, onToggle = { openId = if (openId == p.id) null else p.id }, onSymbol = { read(it) })
            }
        }
    }
}

@Composable
private fun StockReportView(r: AppViewModel.StockReport, state: AppState) {
    val st = r.stats
    val verdict = remember(r, state.settings.halalOnly) {
        stockVerdict(r.symbol, st, state.settings.halalOnly, state.bal(Fund.INVESTMENT), state.portfolioValue)
    }
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("${r.symbol}${r.name?.let { "، $it" } ?: ""}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${qty(r.price)} ${r.currency}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            r.priceIls?.takeIf { r.currency != "ILS" }?.let { Text("  ≈ ${money(it)}", fontSize = 14.sp) }
        }
        Text(
            "سنة: ${if (st.change1y >= 0) "+" else ""}${pct(st.change1y)}   3 شهور: ${if (st.change3m >= 0) "+" else ""}${pct(st.change3m)}",
            color = if (st.change1y >= 0) Profit else Loss,
            fontWeight = FontWeight.Bold,
        )
        Sparkline(r.closes, if (st.change1y >= 0) Profit else Loss, Modifier.fillMaxWidth().height(90.dp))
        Row {
            StatCell("أعلى سنة", qty(st.high), Modifier.weight(1f))
            StatCell("أدنى سنة", qty(st.low), Modifier.weight(1f))
            StatCell("عن القمة", pct(st.fromHigh), Modifier.weight(1f))
        }
        Row {
            StatCell("متوسط 50 يوم", qty(st.sma50), Modifier.weight(1f))
            StatCell("متوسط 200 يوم", qty(st.sma200), Modifier.weight(1f))
            StatCell("التقلّب", pct(st.volatility), Modifier.weight(1f))
        }
        Text("قراءة المدرب", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        verdict.forEach { WarnBox(it) }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    if (values.size < 2) return
    Canvas(modifier) {
        val mn = values.min()
        val mx = values.max()
        val span = (mx - mn).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        // الرسوم المالية دايماً من اليسار لليمين (الأقدم ← الأحدث)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = (size.height * (1 - (v - mn) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.12f))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color, 3.5.dp.toPx(), Offset(size.width, (size.height * (1 - (values.last() - mn) / span)).toFloat()))
    }
}

@Composable
private fun PortfolioCard(p: ModelPortfolio, open: Boolean, onToggle: () -> Unit, onSymbol: (String) -> Unit) {
    val riskText = when (p.risk) {
        1 -> "مخاطرة منخفضة"
        2 -> "مخاطرة متوسطة"
        else -> "مخاطرة عالية"
    }
    val riskColor = when (p.risk) {
        1 -> Profit
        2 -> Color(0xFFD08700)
        else -> Loss
    }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onToggle)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, fontWeight = FontWeight.Bold)
                    Text(p.investor, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Pill(riskText, riskColor)
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                Text(p.quote, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(p.philosophy, style = MaterialTheme.typography.bodyMedium)
                if (p.holdings.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    p.holdings.forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSymbol(h.symbol) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(h.symbol, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, fontSize = 12.sp)
                                Progress(h.weight / 100f, Gold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${h.weight}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                if (p.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(p.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            } else {
                Text(p.quote, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
