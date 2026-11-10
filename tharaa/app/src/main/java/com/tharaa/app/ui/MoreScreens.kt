package com.tharaa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tharaa.app.domain.Advisor
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.K
import com.tharaa.app.domain.Lessons
import com.tharaa.app.domain.dateTimeText
import com.tharaa.app.domain.money
import com.tharaa.app.domain.parseAmount
import com.tharaa.app.domain.qty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LessonsScreen() {
    var open by remember { mutableIntStateOf(-1) }
    Screen(title = "المعلم 🎓") { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                InfoCard(
                    "📖", "خلاصة كتب الثراء",
                    "كل قاعدة بالتطبيق جاية من واحد من هالدروس. اقرأ درس كل يوم، وطبّقه بنفس اليوم.",
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
            }
            itemsIndexed(Lessons.all) { i, l ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clickable { open = if (open == i) -1 else i },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.emoji, fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(l.source, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text(if (open == i) "▲" else "▼")
                        }
                        if (open == i) {
                            Spacer(Modifier.height(10.dp))
                            Text(l.body, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(10.dp))
                            InfoCard("✅", "طبّقها", l.action, container = MaterialTheme.colorScheme.secondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel, onBack: () -> Unit) {
    val s = state.settings
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val autoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.setAutoBackup(uri)
    }
    var confirmImport by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    var essText by remember { mutableStateOf(if (s.essentialsBudget > 0) qty(s.essentialsBudget) else "") }

    Screen(title = "الإعدادات ⚙️", onBack = onBack) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("خطة التقسيم")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !s.isCustom, onClick = { vm.setSetting(K.PLAN_MODE, "auto") }, label = { Text("🧠 المستشار (موصى به)") })
                FilterChip(selected = s.isCustom, onClick = { vm.setSetting(K.PLAN_MODE, "custom") }, label = { Text("✋ نسب مخصصة") })
            }
            if (!s.isCustom) {
                Text("المستشار بيغيّر النسب تلقائياً حسب مرحلتك (حالياً: ${state.phase.title}).", fontSize = 13.sp)
                state.phase.weights.entries.sortedByDescending { it.value }.forEach { (f, p) ->
                    Text("${f.emoji} ${f.title}: $p%", fontSize = 13.sp)
                }
            } else {
                CustomWeightsEditor(state, vm)
            }

            HorizontalDivider()
            SectionTitle("الأساسيات والطوارئ")
            AmountField(essText, { essText = it }, "ميزانية الأساسيات الشهرية (إيجار، فواتير، أكل)")
            Text("اتركها فاضية إذا ما عندك التزامات ثابتة حالياً. لما تحددها، بتتغطى أول شي من كل دخل.", fontSize = 12.sp)
            OutlinedButton(onClick = { vm.setSetting(K.ESSENTIALS_BUDGET, (parseAmount(essText) ?: 0.0).toString()) }) { Text("حفظ الميزانية") }

            Text("حجم صندوق الطوارئ: ${s.emergencyMonths} شهور (${money(state.emergencyTarget)})", fontWeight = FontWeight.Bold)
            Slider(
                value = s.emergencyMonths.toFloat(),
                onValueChange = { vm.setSetting(K.EMERGENCY_MONTHS, it.toInt().toString()) },
                valueRange = 3f..6f,
                steps = 2,
            )
            Text("محسوبة على مصاريفك الشهرية المتوقعة: ${money(state.monthlyBase)}. مع الدخل المتغير، الكتب بتنصح بـ 4 إلى 6.", fontSize = 12.sp)

            HorizontalDivider()
            SectionTitle("ترحيل نهاية الشهر")
            SwitchRow("المتبقي من الأساسيات ← الاستثمار", s.rolloverEssentials) { vm.setSetting(K.ROLL_ESS, it.toString()) }
            SwitchRow("المتبقي من مصروفك ← الطوارئ (ثم الاستثمار)", s.rolloverPersonal) { vm.setSetting(K.ROLL_PERSONAL, it.toString()) }

            HorizontalDivider()
            SectionTitle("التذكيرات")
            SwitchRow("تذكير يومي لتسجيل المصاريف + نصيحة", s.remindersOn) { vm.setSetting(K.REMINDERS, it.toString()) }
            if (s.remindersOn) {
                Text("الساعة: ${s.reminderHour}:00", fontWeight = FontWeight.Bold)
                Slider(
                    value = s.reminderHour.toFloat(),
                    onValueChange = { vm.setSetting(K.REMINDER_HOUR, it.toInt().toString()) },
                    valueRange = 8f..23f,
                    steps = 14,
                )
            }

            HorizontalDivider()
            SectionTitle("النسخ الاحتياطي والمزامنة ☁️")
            InfoCard(
                "☁️", "نسخ تلقائي يومي",
                if (s.autoBackupUri != null) "مفعّل. آخر نسخة: ${if (s.lastBackupAt > 0) dateTimeText(s.lastBackupAt) else "—"}"
                else "اختار مكان الملف (مثلاً Google Drive من قائمة الحفظ) والتطبيق بيحدّثه كل يوم تلقائياً. " +
                    "على جوال جديد: نزّل التطبيق واسترجع من نفس الملف.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.autoBackupUri == null) {
                        FilledTonalButton(onClick = { autoLauncher.launch("tharaa-backup.json") }) { Text("اختيار ملف النسخ") }
                    } else {
                        FilledTonalButton(onClick = { vm.backupNow() }) { Text("انسخ الآن") }
                        TextButton(onClick = { vm.disableAutoBackup() }) { Text("إيقاف") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("tharaa-$stamp.json") }) { Text("💾 تصدير نسخة") }
                OutlinedButton(onClick = { confirmImport = true }) { Text("📥 استرجاع نسخة") }
            }
            Text("كمان أندرويد بياخذ نسخة تلقائية من بيانات التطبيق لحساب Google تبعك (إذا النسخ الاحتياطي مفعّل بالجوال).", fontSize = 12.sp)

            HorizontalDivider()
            Text("ثراء · إصدار ${appVersion()}", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmImport) {
        ConfirmDialog(
            "استرجاع نسخة احتياطية",
            "رح تنستبدل كل بياناتك الحالية ببيانات الملف. متأكد؟",
            confirm = "اختار الملف",
            onConfirm = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
            onDismiss = { confirmImport = false },
        )
    }
}

@Composable
private fun appVersion(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CustomWeightsEditor(state: AppState, vm: AppViewModel) {
    val weights = remember { mutableStateMapOf<Fund, String>().apply { Fund.entries.forEach { put(it, (state.settings.customWeights[it] ?: 0).toString()) } } }
    val parsed = Fund.entries.associateWith { weights[it]?.let(::parseAmount)?.toInt() ?: 0 }
    val input = state.input
    val warnings = if (input != null) Advisor.reviewWeights(parsed, input) else emptyList()

    Text("حدد نسبة كل صندوق (المجموع 100%). المستشار بيراجع اختياراتك:", fontSize = 13.sp)
    Fund.entries.forEach { f ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${f.emoji} ${f.title}", modifier = Modifier.weight(1f))
            Text("ينصح: ${state.phase.weights[f] ?: 0}%", fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
            NumberField(weights[f] ?: "", { weights[f] = it }, "", modifier = Modifier.width(90.dp), suffix = "%")
        }
    }
    Text("المجموع: ${parsed.values.sum()}%", fontWeight = FontWeight.Bold)
    warnings.forEach { WarnBox(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = warnings.none { it.severe }, onClick = { vm.saveCustomWeights(parsed) }) { Text("حفظ النسب") }
        TextButton(onClick = { Fund.entries.forEach { weights[it] = (state.phase.weights[it] ?: 0).toString() } }) { Text("نسخ نسب المستشار") }
    }
}
