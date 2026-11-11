package com.tharaa.app.domain

import com.tharaa.app.data.Debt
import com.tharaa.app.data.Goal
import com.tharaa.app.data.GoalKind
import com.tharaa.app.data.Holding
import com.tharaa.app.data.Income
import com.tharaa.app.data.Setting
import com.tharaa.app.data.T
import com.tharaa.app.data.Txn
import kotlin.math.max

object K {
    const val PLAN_MODE = "planMode"
    const val ESSENTIALS_BUDGET = "essentialsBudget"
    const val EMERGENCY_MONTHS = "emergencyMonths"
    const val DEBT_STRATEGY = "debtStrategy"
    const val ROLL_ESS = "rolloverEssentials"
    const val ROLL_PERSONAL = "rolloverPersonal"
    const val REMINDERS = "remindersOn"
    const val REMINDER_HOUR = "reminderHour"
    const val BACKUP_URI = "autoBackupUri"
    const val LAST_BACKUP = "lastBackupAt"
    const val LAST_ROLLOVER = "lastRollover"
    const val ONBOARDED = "onboarded"
    const val NO_SPEND = "noSpendDays"
    const val COMMITMENTS = "commitments"
    const val HALAL = "halalOnly"
    fun weight(f: Fund) = "w_${f.key}"
}

data class Settings(
    val planMode: String = "auto",
    val customWeights: Map<Fund, Int> = Phase.FOUNDATION.weights,
    val essentialsBudget: Double = 0.0,
    val emergencyMonths: Int = 4,
    val debtStrategy: String = "snowball",
    val rolloverEssentials: Boolean = true,
    val rolloverPersonal: Boolean = true,
    val remindersOn: Boolean = true,
    val reminderHour: Int = 21,
    val autoBackupUri: String? = null,
    val lastBackupAt: Long = 0,
    val lastRollover: String? = null,
    val onboarded: Boolean = false,
    val noSpendDays: Set<Long> = emptySet(),
    val commitments: List<Commitment> = emptyList(),
    val halalOnly: Boolean = false,
) {
    val isCustom get() = planMode == "custom"

    companion object {
        fun from(list: List<Setting>): Settings {
            val m = list.associate { it.k to it.v }
            val d = Settings()
            val hasWeights = Fund.entries.any { m.containsKey(K.weight(it)) }
            val weights = if (hasWeights) {
                Fund.entries.associateWith { m[K.weight(it)]?.toIntOrNull() ?: 0 }.filterValues { it > 0 }
            } else d.customWeights
            return Settings(
                planMode = m[K.PLAN_MODE] ?: d.planMode,
                customWeights = weights,
                essentialsBudget = m[K.ESSENTIALS_BUDGET]?.toDoubleOrNull() ?: d.essentialsBudget,
                emergencyMonths = m[K.EMERGENCY_MONTHS]?.toIntOrNull() ?: d.emergencyMonths,
                debtStrategy = m[K.DEBT_STRATEGY] ?: d.debtStrategy,
                rolloverEssentials = m[K.ROLL_ESS]?.toBooleanStrictOrNull() ?: d.rolloverEssentials,
                rolloverPersonal = m[K.ROLL_PERSONAL]?.toBooleanStrictOrNull() ?: d.rolloverPersonal,
                remindersOn = m[K.REMINDERS]?.toBooleanStrictOrNull() ?: d.remindersOn,
                reminderHour = m[K.REMINDER_HOUR]?.toIntOrNull() ?: d.reminderHour,
                autoBackupUri = m[K.BACKUP_URI]?.takeIf { it.isNotBlank() },
                lastBackupAt = m[K.LAST_BACKUP]?.toLongOrNull() ?: 0,
                lastRollover = m[K.LAST_ROLLOVER],
                onboarded = m[K.ONBOARDED]?.toBooleanStrictOrNull() ?: false,
                commitments = CommitmentCodec.decode(m[K.COMMITMENTS]),
                halalOnly = m[K.HALAL]?.toBooleanStrictOrNull() ?: false,
                noSpendDays = m[K.NO_SPEND].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet(),
            )
        }
    }
}

data class DebtInfo(val debt: Debt, val paid: Double) {
    val remaining get() = max(0.0, debt.total - paid)
    val progress: Float get() = if (debt.total <= 0) 1f else (paid / debt.total).toFloat().coerceIn(0f, 1f)
    val isPaid get() = remaining < 0.5
}

data class GoalInfo(val goal: Goal, val saved: Double) {
    val progress: Float get() = if (goal.target <= 0) 0f else (saved / goal.target).toFloat().coerceIn(0f, 1f)
    val remaining get() = max(0.0, goal.target - saved)
    val reached get() = goal.target > 0 && saved >= goal.target - 0.5
}

data class RolloverMove(val from: Fund, val to: Fund, val amount: Double, val why: String)

data class RawData(
    val txns: List<Txn> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val holdings: List<Holding> = emptyList(),
)

data class AppState(
    val loaded: Boolean = false,
    val raw: RawData = RawData(),
    val settings: Settings = Settings(),
    val balances: Map<Fund, Double> = emptyMap(),
    val debts: List<DebtInfo> = emptyList(),
    val goals: List<GoalInfo> = emptyList(),
    val debtRemaining: Double = 0.0,
    val nextDebt: DebtInfo? = null,
    val portfolioValue: Double = 0.0,
    val portfolioCost: Double = 0.0,
    val cashTotal: Double = 0.0,
    val netWorth: Double = 0.0,
    val monthlyBase: Double = 0.0,
    val avgMonthlyIncome: Double = 0.0,
    val avgMonthlyExpenses: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpenses: Double = 0.0,
    val emergencyTarget: Double = 0.0,
    val phase: Phase = Phase.FOUNDATION,
    val input: AdvisorInput? = null,
    val pendingRollover: List<RolloverMove> = emptyList(),
    val currentMonth: String = "",
    /** عدد الأيام المتتالية اللي سجّلت فيها شي (مصروف، دخل، أو «ما صرفت»). */
    val streak: Int = 0,
    val loggedToday: Boolean = false,
    /** كل اللي دخل كل صندوق هالشهر، لحساب امتلاء الجرة. */
    val monthInflow: Map<Fund, Double> = emptyMap(),
    /** الالتزامات الشهرية الفعّالة هالشهر وقديش انسدّ منها. */
    val commitments: List<CommitmentInfo> = emptyList(),
    val commitmentsTotal: Double = 0.0,
) {
    fun bal(f: Fund) = balances[f] ?: 0.0
}

/** ترتيب الديون حسب الاستراتيجية: كرة الثلج (الأصغر أولاً) أو الانهيار الجليدي (الأعلى فائدة). */
fun orderDebts(list: List<DebtInfo>, strategy: String): List<DebtInfo> {
    val open = list.filter { !it.isPaid }
    val sorted = if (strategy == "avalanche") {
        open.sortedWith(compareByDescending<DebtInfo> { it.debt.interestRate }.thenBy { it.remaining })
    } else {
        open.sortedBy { it.remaining }
    }
    return sorted + list.filter { it.isPaid }
}

fun derive(raw: RawData, settingsList: List<Setting>, now: Long = System.currentTimeMillis()): AppState {
    val s = Settings.from(settingsList)
    val byFund = raw.txns.groupBy { it.fund }
    val balances = Fund.entries.associateWith { f -> round2(byFund[f.key].orEmpty().sumOf { it.amount }) }
    val paidByDebt = raw.txns.filter { it.type == T.DEBT_PAY && it.debtId != null }.groupBy { it.debtId }
    val savedByGoal = raw.txns.filter { it.goalId != null }.groupBy { it.goalId }

    val debtInfos = raw.debts.map { d ->
        DebtInfo(d, -paidByDebt[d.id].orEmpty().sumOf { it.amount })
    }
    val orderedDebts = orderDebts(debtInfos, s.debtStrategy)
    val debtRemaining = debtInfos.sumOf { it.remaining }

    val portfolioValue = raw.holdings.sumOf { it.value() }
    val portfolioCost = raw.holdings.sumOf { it.costIls }

    val thisMonth = monthKey(now)
    val prevMonths = (1..3).map { monthKey(addMonths(now, -it)) }.toSet()

    val expenses = raw.txns.filter { it.type == T.EXPENSE }
    val monthExpenses = -expenses.filter { monthKey(it.timestamp) == thisMonth }.sumOf { it.amount }
    val activeMonths = (raw.incomes.map { monthKey(it.timestamp) } + expenses.map { monthKey(it.timestamp) })
        .filter { it in prevMonths }.toSet().size
    val avgExpenses = if (activeMonths > 0) {
        -expenses.filter { monthKey(it.timestamp) in prevMonths }.sumOf { it.amount } / activeMonths
    } else 0.0

    val prevIncomes = raw.incomes.filter { monthKey(it.timestamp) in prevMonths }
    val incomeMonths = prevIncomes.map { monthKey(it.timestamp) }.toSet().size
    val avgIncome = if (incomeMonths > 0) prevIncomes.sumOf { it.amount } / incomeMonths else 0.0
    val monthIncome = raw.incomes.filter { monthKey(it.timestamp) == thisMonth }.sumOf { it.amount }

    // تقدير المصاريف الشهرية: أعلى قيمة بين المصاريف الفعلية والميزانية ونص متوسط الدخل، وبحد أدنى 1000 ₪
    // (لأن التسجيل بالبداية بيكون ناقص، وما بدنا هدف طوارئ صغير بشكل وهمي)
    val incomeHint = if (avgIncome > 0) avgIncome * 0.5 else monthIncome * 0.5
    // الالتزامات الشهرية (جمعية، أقساط…) بتنضاف للأساسيات وبتتغطى أول شي من كل دخل
    val activeCommitments = s.commitments.filter { it.activeIn(thisMonth) }
    val commitmentsTotal = activeCommitments.sumOf { it.amount }
    val commitmentInfos = activeCommitments.map { c ->
        val cat = commitmentCategory(c.id)
        CommitmentInfo(c, -expenses.filter { it.category == cat && monthKey(it.timestamp) == thisMonth }.sumOf { it.amount })
    }
    val essentialsTotal = s.essentialsBudget + commitmentsTotal
    val base = maxOf(avgExpenses, essentialsTotal, monthExpenses, incomeHint, 1000.0)

    val essFunded = raw.txns.filter {
        it.type == T.ALLOC && it.fund == Fund.ESSENTIALS.key && monthKey(it.timestamp) == thisMonth
    }.sumOf { it.amount }

    val portfolioForGoals = portfolioValue + (balances[Fund.INVESTMENT] ?: 0.0)
    val goalInfos = raw.goals.sortedWith(compareBy({ it.priority }, { it.id })).map { g ->
        val saved = if (g.kind == GoalKind.PORTFOLIO) portfolioForGoals
        else savedByGoal[g.id].orEmpty().sumOf { it.amount }
        GoalInfo(g, saved)
    }
    val savingOpen = goalInfos.filter { it.goal.kind == GoalKind.SAVING && !it.goal.done && it.goal.target > 0 }
    val goalsRoom = savingOpen.sumOf { it.remaining }
    val topGoal = savingOpen.firstOrNull { !it.reached }

    val input = AdvisorInput(
        balances = balances,
        debtRemaining = debtRemaining,
        monthlyBase = base,
        emergencyMonths = s.emergencyMonths,
        essentialsBudget = essentialsTotal,
        essentialsFundedThisMonth = essFunded,
        avgMonthlyIncome = avgIncome,
        monthIncomeSoFar = monthIncome,
        goalsRoom = goalsRoom,
        topGoalName = topGoal?.goal?.name,
        nextDebtName = orderedDebts.firstOrNull { !it.isPaid }?.debt?.name,
        customWeights = if (s.isCustom) s.customWeights else null,
    )
    val emergencyTarget = Advisor.emergencyTarget(input)

    // مراجعة نهاية الشهر: المتبقي من الأساسيات ← الاستثمار، والمتبقي من المصروف ← الطوارئ
    val rollover = mutableListOf<RolloverMove>()
    val last = s.lastRollover
    if (last != null && last < thisMonth) {
        // نحجز من الأساسيات قيمة الالتزامات اللي ما انسدّت الشهر الماضي
        val prevMonth = monthKey(addMonths(now, -1))
        val unpaidReserve = s.commitments.filter { it.activeIn(prevMonth) }.sumOf { c ->
            val cat = commitmentCategory(c.id)
            val paid = -expenses.filter { it.category == cat && monthKey(it.timestamp) == prevMonth }.sumOf { it.amount }
            max(0.0, c.amount - paid)
        }
        val ess = max(0.0, (balances[Fund.ESSENTIALS] ?: 0.0) - unpaidReserve)
        if (s.rolloverEssentials && ess >= 1) {
            rollover += RolloverMove(Fund.ESSENTIALS, Fund.INVESTMENT, ess, "المتبقي من الأساسيات بيتحول لأصول بدل ما يضيع")
        }
        val personal = balances[Fund.PERSONAL] ?: 0.0
        if (s.rolloverPersonal && personal >= 1) {
            val em = balances[Fund.EMERGENCY] ?: 0.0
            val room = max(0.0, emergencyTarget - em)
            val toEm = minOf(personal, room)
            if (toEm >= 1) rollover += RolloverMove(Fund.PERSONAL, Fund.EMERGENCY, round2(toEm), "المصروف اللي ما انصرف بيقوّي درعك")
            val restP = personal - toEm
            if (restP >= 1) rollover += RolloverMove(Fund.PERSONAL, Fund.INVESTMENT, round2(restP), "الطوارئ مكتملة، فالباقي للاستثمار")
        }
    }

    // السلسلة: أيام متتالية فيها تسجيل، بتضل حيّة إذا آخر تسجيل كان مبارح
    val loggedDays = (expenses.map { startOfDay(it.timestamp) } + raw.incomes.map { startOfDay(it.timestamp) } + s.noSpendDays).toSet()
    val today = startOfDay(now)
    val loggedToday = today in loggedDays
    var day = if (loggedToday) today else startOfDay(today - 12 * 3_600_000L)
    var streak = 0
    while (day in loggedDays) {
        streak++
        day = startOfDay(day - 12 * 3_600_000L)
    }
    val monthInflow = Fund.entries.associateWith { f ->
        byFund[f.key].orEmpty().filter { it.amount > 0 && monthKey(it.timestamp) == thisMonth }.sumOf { it.amount }
    }

    val cash = balances.values.sum()
    return AppState(
        loaded = true,
        raw = raw,
        settings = s,
        balances = balances,
        debts = orderedDebts,
        goals = goalInfos,
        debtRemaining = debtRemaining,
        nextDebt = orderedDebts.firstOrNull { !it.isPaid },
        portfolioValue = portfolioValue,
        portfolioCost = portfolioCost,
        cashTotal = cash,
        netWorth = cash + portfolioValue - debtRemaining,
        monthlyBase = base,
        avgMonthlyIncome = avgIncome,
        avgMonthlyExpenses = avgExpenses,
        monthIncome = monthIncome,
        monthExpenses = monthExpenses,
        emergencyTarget = emergencyTarget,
        phase = Advisor.phaseOf(input),
        input = input,
        pendingRollover = rollover,
        currentMonth = thisMonth,
        streak = streak,
        loggedToday = loggedToday,
        monthInflow = monthInflow,
        commitments = commitmentInfos,
        commitmentsTotal = commitmentsTotal,
    )
}
