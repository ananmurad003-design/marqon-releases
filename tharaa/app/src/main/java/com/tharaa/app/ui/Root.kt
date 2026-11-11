package com.tharaa.app.ui

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

private data class NavTab(val route: String, val label: String, val emoji: String)

private val tabs = listOf(
    NavTab("home", "الرئيسية", "🏠"),
    NavTab("expenses", "المصاريف", "🧾"),
    NavTab("invest", "الاستثمار", "📈"),
    NavTab("plan", "الديون والأهداف", "🎯"),
    NavTab("coach", "المدرب", "🧠"),
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
        WelcomeScreen(onStart = { vm.finishOnboarding() })
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
                        icon = { Text(t.emoji, fontSize = 20.sp) },
                        label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
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
                    Text("${m.from.emoji} ${m.from.title} ← ${m.to.emoji} ${m.to.title}: ${money(m.amount)}", fontWeight = FontWeight.Bold)
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
private fun WelcomeScreen(onStart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("👑", fontSize = 64.sp)
            Text("أهلاً بك في ثراء", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "أنا مستشارك ومعلمك المالي. كل ما يدخلك مال، سجّله وأنا بقسّمه على صناديقك " +
                    "حسب أفكار أشهر كتب الثراء، وبتغيّر الخطة كل ما تتقدم.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            listOf(
                "1️⃣" to "التأسيس: طوارئ مبدئي + هجوم على الديون",
                "2️⃣" to "التحرر من الديون بطريقة كرة الثلج",
                "3️⃣" to "درع الأمان: طوارئ 4 شهور + أهدافك",
                "4️⃣" to "بناء الثروة: استثمار وأصول",
            ).forEach { (n, t) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t)
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoCard(
                "🏛️", "القاعدة الذهبية",
                "«ادفع لنفسك أولاً»: 10% على الأقل من كل دخل للاستثمار، مهما كانت مرحلتك.",
                container = MaterialTheme.colorScheme.secondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "جهّزتلك أهدافك: الزواج 💍، السيارة 🚗، وأول 10,000 ₪ بالمحفظة 📈. بتقدر تعدّل المبالغ من شاشة الأهداف.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("يلا نبدأ الطريق 🚀", fontSize = 17.sp)
            }
        }
    }
}
