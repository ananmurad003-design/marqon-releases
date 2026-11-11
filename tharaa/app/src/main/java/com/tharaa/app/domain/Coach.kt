package com.tharaa.app.domain

import com.tharaa.app.data.GoalKind
import com.tharaa.app.data.T
import kotlin.math.ceil
import kotlin.math.max

/** level: 0 ممتاز، 1 معلومة، 2 انتبه */
data class Insight(val emoji: String, val title: String, val body: String, val level: Int = 1)

data class Metrics(
    val savingsRate: Double,
    val monthlyInvest: Double,
    val monthlyDebt: Double,
    val monthlyGoals: Double,
    val runwayMonths: Double,
    val fiNumber: Double,
    val fiProgress: Double,
    val in10y: Double,
    val in20y: Double,
)

object Coach {
    const val EXPECTED_RETURN = 0.08

    /** قيمة مستقبلية بعائد سنوي مركّب شهرياً مع إيداع شهري ثابت. */
    fun futureValue(start: Double, monthly: Double, years: Int, annual: Double = EXPECTED_RETURN): Double {
        val r = annual / 12
        var v = start
        repeat(years * 12) { v = v * (1 + r) + monthly }
        return v
    }

    private fun monthsTo(remaining: Double, monthly: Double): Int? =
        if (monthly <= 0.5 || remaining <= 0) null else ceil(remaining / monthly).toInt()

    private fun whenText(months: Int): String = when {
        months <= 1 -> "خلال شهر"
        months < 12 -> "خلال $months شهور تقريباً (${monthTitle(monthKey(addMonths(System.currentTimeMillis(), months)))})"
        else -> "خلال ${one(months / 12.0)} سنة تقريباً"
    }

    fun metrics(s: AppState, now: Long = System.currentTimeMillis()): Metrics? {
        val since = addMonths(now, -3)
        val incomes = s.raw.incomes.filter { it.timestamp >= since }
        if (incomes.isEmpty()) return null
        val first = incomes.minOf { it.timestamp }
        val months = ((now - first) / (30.0 * 86_400_000)).coerceIn(1.0, 3.0)
        val incomeSum = incomes.sumOf { it.amount }
        val alloc = s.raw.txns.filter { it.type == T.ALLOC && it.timestamp >= since }.groupBy { Fund.of(it.fund) }
            .mapValues { e -> e.value.sumOf { it.amount } }
        fun a(f: Fund) = alloc[f] ?: 0.0
        val saved = a(Fund.INVESTMENT) + a(Fund.EMERGENCY) + a(Fund.GOALS) + a(Fund.DEBT)
        val monthlyInvest = a(Fund.INVESTMENT) / months
        val wealthNow = s.portfolioValue + s.bal(Fund.INVESTMENT)
        val fi = s.monthlyBase * 12 * 25
        return Metrics(
            savingsRate = if (incomeSum > 0) saved / incomeSum * 100 else 0.0,
            monthlyInvest = monthlyInvest,
            monthlyDebt = a(Fund.DEBT) / months,
            monthlyGoals = a(Fund.GOALS) / months,
            runwayMonths = if (s.monthlyBase > 0) s.bal(Fund.EMERGENCY) / s.monthlyBase else 0.0,
            fiNumber = fi,
            fiProgress = if (fi > 0) wealthNow / fi * 100 else 0.0,
            in10y = futureValue(wealthNow, monthlyInvest, 10),
            in20y = futureValue(wealthNow, monthlyInvest, 20),
        )
    }

    /** تقرير المدرب: بيقرأ أرقامك وبيعطيك أهم الملاحظات مرتّبة. */
    fun report(s: AppState, now: Long = System.currentTimeMillis()): List<Insight> {
        val m = metrics(s, now) ?: return listOf(
            Insight("📝", "سجّل دخلك أول", "أول ما تسجّل دخل، بقرأ أرقامك وبعطيك تقرير كامل: نسبة ادخارك، متى بتخلص ديونك، وكيف رح تكبر ثروتك."),
        )
        val out = mutableListOf<Insight>()

        out += when {
            m.savingsRate >= 30 -> Insight("🏆", "نسبة بناء الثروة ${pct(m.savingsRate)}", "ممتاز! أكثر من 30% من دخلك بيبني مستقبلك (استثمار، طوارئ، أهداف، سداد ديون). هيك بيعملوا اللي بيوصلوا.", 0)
            m.savingsRate >= 20 -> Insight("👍", "نسبة بناء الثروة ${pct(m.savingsRate)}", "جيد. الهدف التالي 30%: كل زيادة بالدخل خلّي نصها يروح للاستثمار.", 1)
            else -> Insight("⚠️", "نسبة بناء الثروة ${pct(m.savingsRate)}", "أقل من 20% من دخلك بيبني مستقبلك. راجع التقسيم ولا تقلل من نسب المستشار.", 2)
        }

        // الطوارئ
        val runway = m.runwayMonths
        out += if (runway >= s.settings.emergencyMonths) {
            Insight("🛡️", "درعك مكتمل", "صندوق الطوارئ بيغطي ${one(runway)} شهور. أي زيادة منه بتروح للاستثمار.", 0)
        } else {
            Insight("🛡️", "درعك بيكفي ${one(runway)} شهر", "الهدف ${s.settings.emergencyMonths} شهور (${money(s.emergencyTarget)}). مع دخل متغير، هذا أهم شي بعد الديون.", if (runway < 1) 2 else 1)
        }

        // الديون
        if (s.debtRemaining > 0.5) {
            val left = max(0.0, s.debtRemaining - s.bal(Fund.DEBT))
            val mm = monthsTo(left, m.monthlyDebt)
            out += if (mm != null) Insight("⛓️", "بتتحرر من ديونك ${whenText(mm)}", "بوتيرتك الحالية (${money(m.monthlyDebt)} بالشهر). كل دخل إضافي بيقرّب الموعد.", 1)
            else Insight("⛓️", "ديونك ما بتنقص", "عليك ${money(s.debtRemaining)} وما في سداد منتظم. سجّل دخلك واعتمد خطة المستشار.", 2)
        }

        // الهدف الأول
        s.goals.firstOrNull { it.goal.kind == GoalKind.SAVING && !it.goal.done && !it.reached }?.let { g ->
            val mm = monthsTo(g.remaining, m.monthlyGoals)
            out += if (mm != null) Insight(g.goal.emoji, "«${g.goal.name}» ${whenText(mm)}", "باقي ${money(g.remaining)}، وبتوفرله ${money(m.monthlyGoals)} بالشهر.", 1)
            else Insight(g.goal.emoji, "«${g.goal.name}» لسا ما بلش", "حصة الأهداف بتبدأ بعد الديون وأول درع طوارئ. كل خطوة بوقتها.", 1)
        }

        // الفائدة المركبة
        if (m.monthlyInvest > 0) {
            out += Insight(
                "🌱", "إذا كمّلت هيك: ${money(m.in10y)} بعد 10 سنين",
                "باستثمار ${money(m.monthlyInvest)} شهرياً وعائد متوقع 8% (متوسط تاريخي، مش مضمون). وبعد 20 سنة: ${money(m.in20y)}. السر هو الاستمرار.",
                0,
            )
        }

        // رقم الحرية المالية
        out += Insight(
            "🗽", "رقم حريتك المالية: ${money(m.fiNumber)}",
            "قاعدة الـ4%: لما يصير عندك 25 ضعف مصاريفك السنوية مستثمرة، بتقدر تعيش من أرباحها. وصلت ${pct(m.fiProgress)} من الطريق.",
            1,
        )

        // نقد نايم
        val idle = s.bal(Fund.INVESTMENT)
        if (idle > max(500.0, m.monthlyInvest * 1.5)) {
            out += Insight("😴", "${money(idle)} نايمين بصندوق الاستثمار", "المال اللي ما بيشتري أصول بيخسر قيمته مع التضخم. شوف «خطتي لمالي» بتبويب الاستثمار الذكي.", 2)
        }

        // تركّز وخسارة المحفظة
        val holdings = s.raw.holdings.filter { it.quantity > 0 }
        if (s.portfolioValue > 0) {
            holdings.maxByOrNull { it.value() }?.let { h ->
                val share = h.value() / s.portfolioValue * 100
                if (holdings.size > 1 && share > 40 && h.symbol?.uppercase() !in Investors.broadFunds) {
                    out += Insight("🥚", "${h.name} = ${pct(share)} من محفظتك", "تركيز عالي بسهم واحد. التنويع بيحميك: قوّي النواة (صندوق مؤشر).", 2)
                }
            }
            val pnl = (s.portfolioValue - s.portfolioCost) / s.portfolioCost.coerceAtLeast(1.0) * 100
            if (pnl < -15) out += Insight("🎩", "محفظتك نازلة ${pct(-pnl)}", "طبيعي بالسوق. غراهام: السوق على المدى القصير آلة تصويت، وعلى الطويل آلة وزن. لا تبيع بخوف.", 1)
        }

        // الدخل المتقلب
        val monthly = s.raw.incomes.groupBy { monthKey(it.timestamp) }.mapValues { e -> e.value.sumOf { it.amount } }.filterKeys { it != s.currentMonth }
        if (monthly.size >= 2) {
            val hi = monthly.values.max()
            val lo = monthly.values.min()
            if (lo > 0 && hi / lo > 1.8 && s.settings.emergencyMonths < 6) {
                out += Insight("🎢", "دخلك متقلب (من ${money(lo)} لـ ${money(hi)})", "مع دخل زي هيك، الأفضل ترفع الطوارئ لـ 6 شهور من الإعدادات، وتحسب مصاريفك على أضعف شهر.", 2)
            }
        }

        // الالتزامات
        if (s.commitmentsTotal > 0 && s.avgMonthlyIncome > 0) {
            val r = s.commitmentsTotal / s.avgMonthlyIncome * 100
            if (r > 30) out += Insight("📅", "التزاماتك ${pct(r)} من دخلك", "أكثر من 30% التزامات ثابتة بيخنق الخطة. لا تدخل بالتزام جديد قبل ما يخلص واحد.", 2)
        }

        // المصاريف
        if (s.avgMonthlyExpenses > 0 && s.monthExpenses > s.avgMonthlyExpenses * 1.15) {
            out += Insight("🔍", "مصاريفك فوق المعتاد هالشهر", "${money(s.monthExpenses)} مقابل متوسط ${money(s.avgMonthlyExpenses)}. افتح المصاريف وشوف التصنيف اللي طلع.", 2)
        }

        return out.sortedByDescending { it.level }
    }
}
