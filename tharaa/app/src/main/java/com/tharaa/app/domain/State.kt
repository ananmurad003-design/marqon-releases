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
    val balances = Fund.entries.associateWith { f -> round2(raw.txns.filter { it.fund == f.key }.sumOf { it.amount }) }

    val debtInfos = raw.debts.map { d ->
        DebtInfo(d, -raw.txns.filter { it.type == T.DEBT_PAY && it.debtId == d.id }.sumOf { it.amount })
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
    val base = maxOf(avgExpenses, s.essentialsBudget, monthExpenses, incomeHint, 1000.0)

    val essFunded = raw.txns.filter {
        it.type == T.ALLOC && it.fund == Fund.ESSENTIALS.key && monthKey(it.timestamp) == thisMonth
    }.sumOf { it.amount }

    val portfolioForGoals = portfolioValue + (balances[Fund.INVESTMENT] ?: 0.0)
    val goalInfos = raw.goals.sortedWith(compareBy({ it.priority }, { it.id })).map { g ->
        val saved = if (g.kind == GoalKind.PORTFOLIO) portfolioForGoals
        else raw.txns.filter { it.goalId == g.id }.sumOf { it.amount }
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
        essentialsBudget = s.essentialsBudget,
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
        val ess = balances[Fund.ESSENTIALS] ?: 0.0
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
    )
}
