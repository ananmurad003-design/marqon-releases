package com.tharaa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Psychology
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.money

private data class NavTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    NavTab("home", "الرئيسية", Icons.Rounded.Home),
    NavTab("expenses", "المصاريف", Icons.AutoMirrored.Rounded.ReceiptLong),
    NavTab("invest", "الاستثمار", Icons.AutoMirrored.Rounded.TrendingUp),
    NavTab("plan", "خطتي", Icons.Rounded.Flag),
    NavTab("coach", "المدرب", Icons.Rounded.Psychology),
)

fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun TharaaRoot(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it, withDismissAction = true) } }
    var party by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.celebrations.collect { party = it } }
    // احتفال لما تنتقل لمرحلة جديدة
    var lastStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.loaded, state.phase) {
        if (!state.loaded) return@LaunchedEffect
        if (lastStep != 0 && state.phase.step > lastStep) vm.celebrate("وصلت المرحلة ${state.phase.step}: ${state.phase.title}!")
        lastStep = state.phase.step
    }

    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!state.settings.onboarded) {
        WelcomeScreen(onStart = { d, c, day, m, car -> vm.finishOnboarding(d, c, day, m, car) })
        return
    }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = tabs.any { it.route == route }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(state, vm, nav) }
            composable("expenses") { ExpensesScreen(state, vm, nav) }
            composable("invest") { InvestScreen(state, vm) }
            composable("plan") { PlanScreen(state, vm) }
            composable("coach") { CoachScreen(state, vm) }
            composable("income") { IncomeScreen(state, vm, onBack = { nav.popBackStack() }) }
            composable("expense") { ExpenseScreen(state, vm, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(state, vm, onBack = { nav.popBackStack() }) }
            composable("fund/{key}") { b ->
                FundScreen(Fund.of(b.arguments?.getString("key") ?: ""), state, vm, onBack = { nav.popBackStack() })
            }
        }
    }

    if (state.pendingRollover.isNotEmpty()) RolloverDialog(state, vm)
    party?.let { Celebration(it) { party = null } }
}

@Composable
private fun RolloverDialog(state: AppState, vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("📅 مراجعة نهاية الشهر") },
        text = {
            Column {
                Text("خلص الشهر! حسب خطتك، المال اللي ما انصرف ما بيضل نايم:")
                Spacer(Modifier.height(10.dp))
                state.pendingRollover.forEach { m ->
                    Text("${m.from.title} ← ${m.to.title}: ${money(m.amount)}", fontWeight = FontWeight.Bold)
                    Text(m.why, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = { Button(onClick = { vm.applyRollover() }) { Text("نفّذ الترحيل") } },
        dismissButton = { TextButton(onClick = { vm.skipRollover() }) { Text("تخطّي هذا الشهر") } },
    )
}

@Composable
private fun WelcomeScreen(onStart: (debt: Double, commitment: Double, day: Int, marriage: Double, car: Double) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var debt by remember { mutableStateOf("") }
    var commitment by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("1") }
    var marriage by remember { mutableStateOf("50000") }
    var car by remember { mutableStateOf("40000") }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            // مؤشر الخطوات
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(if (i <= step) Gold else MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            when (step) {
                0 -> {
                    Text("ثراء", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("كل ما يدخلك مال، بقسّمه على جرارك حسب قواعد أشهر كتب الثراء، وبتتغير الخطة مع تقدّمك.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    PhaseRoad(com.tharaa.app.domain.Phase.FOUNDATION, onDark = false)
                    Spacer(Modifier.height(24.dp))
                    Text("القاعدة الذهبية: 10% على الأقل من كل دخل للاستثمار، قبل أي مصروف.", style = MaterialTheme.typography.bodyLarge)
                }
                1 -> {
                    Text("خلّيني أعرف وضعك", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("اتركها فاضية إذا ما في. بتقدر تعدّل التفاصيل بعدين من «خطتي».", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    AmountField(debt, { debt = it }, "مجموع ديونك")
                    Spacer(Modifier.height(12.dp))
                    AmountField(commitment, { commitment = it }, "جمعية أو التزام شهري ثابت")
                    Spacer(Modifier.height(12.dp))
                    NumberField(day, { day = it }, "يوم الدفع بالشهر")
                }
                else -> {
                    Text("أهدافك الكبيرة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("هدف مكتوب بمبلغ واضح بيتحقق أسرع. عدّل المبالغ لتناسبك.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    AmountField(marriage, { marriage = it }, "الزواج")
                    Spacer(Modifier.height(12.dp))
                    AmountField(car, { car = it }, "السيارة")
                    Spacer(Modifier.height(12.dp))
                    Text("وبعدهم: أول 10,000 ₪ بمحفظة الاستثمار.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(52.dp)) { Text("رجوع") }
                Button(
                    onClick = {
                        if (step < 2) step++
                        else onStart(
                            com.tharaa.app.domain.parseAmount(debt) ?: 0.0,
                            com.tharaa.app.domain.parseAmount(commitment) ?: 0.0,
                            day.toIntOrNull() ?: 1,
                            com.tharaa.app.domain.parseAmount(marriage)?.takeIf { it > 0 } ?: 50_000.0,
                            com.tharaa.app.domain.parseAmount(car)?.takeIf { it > 0 } ?: 40_000.0,
                        )
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (step < 2) "التالي" else "يلا نبدأ", fontSize = 16.sp) }
            }
        }
    }
}
