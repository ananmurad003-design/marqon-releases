package com.tharaa.app.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * مراحل الطريق للثراء. التطبيق يحدد مرحلتك تلقائياً من وضعك ويغيّر نسب التقسيم معها.
 * مزيج من: خطوات ديف رامزي، «ادفع لنفسك أولاً» (أغنى رجل في بابل، 10% كحد أدنى دائماً)،
 * وجرار هارف إيكر (الاستمتاع، التعليم، العطاء).
 */
enum class Phase(
    val step: Int,
    val title: String,
    val description: String,
    val weights: Map<Fund, Int>,
) {
    FOUNDATION(
        1, "التأسيس",
        "نبني صندوق طوارئ مبدئي (مصاريف شهر) ونهاجم الديون بنفس الوقت. الاستثمار ما بيوقف، ولو 10%.",
        mapOf(
            Fund.DEBT to 40, Fund.EMERGENCY to 20, Fund.INVESTMENT to 10,
            Fund.EDUCATION to 5, Fund.PERSONAL to 20, Fund.GIVING to 5,
        ),
    ),
    DEBT_FREE(
        2, "التحرر من الديون",
        "صار عندك درع صغير. الآن الجزء الأكبر لتصفير الديون بطريقة كرة الثلج، الدين الأصغر أولاً.",
        mapOf(
            Fund.DEBT to 55, Fund.EMERGENCY to 5, Fund.INVESTMENT to 10,
            Fund.EDUCATION to 5, Fund.PERSONAL to 20, Fund.GIVING to 5,
        ),
    ),
    SECURITY(
        3, "درع الأمان",
        "ما عليك ديون! نكمّل صندوق الطوارئ للهدف ونبدأ نبني أهدافك ونرفع الاستثمار.",
        mapOf(
            Fund.EMERGENCY to 30, Fund.INVESTMENT to 20, Fund.GOALS to 20,
            Fund.EDUCATION to 5, Fund.PERSONAL to 20, Fund.GIVING to 5,
        ),
    ),
    WEALTH(
        4, "بناء الثروة",
        "محمي وبلا ديون. كل قوتك الآن للاستثمار وتحقيق أهدافك الكبيرة. هون بتبدأ الفائدة المركبة تشتغل لصالحك.",
        mapOf(
            Fund.INVESTMENT to 40, Fund.GOALS to 30,
            Fund.EDUCATION to 5, Fund.PERSONAL to 20, Fund.GIVING to 5,
        ),
    );

    companion object {
        fun of(name: String?): Phase? = entries.firstOrNull { it.name == name }
    }
}

data class AdvisorInput(
    val balances: Map<Fund, Double>,
    val debtRemaining: Double,
    /** المصاريف الشهرية المتوقعة (أساس حساب الطوارئ). */
    val monthlyBase: Double,
    val emergencyMonths: Int,
    val essentialsBudget: Double,
    val essentialsFundedThisMonth: Double,
    /** متوسط الدخل الشهري لآخر 3 شهور (0 = غير معروف بعد). */
    val avgMonthlyIncome: Double,
    val monthIncomeSoFar: Double,
    /** المبلغ المتبقي لاكتمال أهداف الادخار. */
    val goalsRoom: Double,
    val topGoalName: String?,
    val nextDebtName: String?,
    /** نسب المستخدم المخصصة، أو null للوضع التلقائي (المستشار). */
    val customWeights: Map<Fund, Int>?,
) {
    fun bal(f: Fund) = balances[f] ?: 0.0
}

data class Allocation(
    val amount: Double,
    val phase: Phase,
    val lines: Map<Fund, Double>,
    val notes: List<String>,
)

data class Warn(val text: String, val severe: Boolean = false, val positive: Boolean = false)

object Advisor {
    const val MIN_INVEST_RATIO = 0.10
    private const val ESSENTIALS_MAX_RATIO = 0.6

    fun emergencyTarget(i: AdvisorInput) = i.monthlyBase * i.emergencyMonths

    fun phaseOf(i: AdvisorInput): Phase {
        val em = i.bal(Fund.EMERGENCY)
        return when {
            i.debtRemaining > 0.5 && em < i.monthlyBase -> Phase.FOUNDATION
            i.debtRemaining > 0.5 -> Phase.DEBT_FREE
            em < emergencyTarget(i) - 0.5 -> Phase.SECURITY
            else -> Phase.WEALTH
        }
    }

    fun plan(amount: Double, i: AdvisorInput): Allocation {
        val phase = phaseOf(i)
        val notes = mutableListOf<String>()
        val out = Fund.entries.associateWith { 0.0 }.toMutableMap()
        fun add(f: Fund, v: Double) { out[f] = (out[f] ?: 0.0) + v }
        if (amount <= 0) return Allocation(amount, phase, out, notes)

        // 1) الأساسيات أولاً: نغطي الالتزامات الشهرية (بحد أقصى 60% من كل دخل)
        val need = max(0.0, i.essentialsBudget - i.essentialsFundedThisMonth)
        val ess = min(need, amount * ESSENTIALS_MAX_RATIO)
        if (ess > 0) {
            add(Fund.ESSENTIALS, ess)
            notes += "غطّينا ${money(ess)} من التزاماتك الشهرية أولاً، والباقي تقسّم حسب الخطة."
        }
        if (need - ess >= 1) notes += "باقي للأساسيات هذا الشهر ${money(need - ess)}، بتكتمل من الدخل الجاي."
        val rest = amount - ess

        // 2) التقسيم حسب نسب المرحلة (أو النسب المخصصة)
        val w = (i.customWeights ?: phase.weights).toMutableMap()
        if (i.customWeights == null && i.goalsRoom <= 0.5 && (w[Fund.GOALS] ?: 0) > 0) {
            w[Fund.INVESTMENT] = (w[Fund.INVESTMENT] ?: 0) + (w[Fund.GOALS] ?: 0)
            w[Fund.GOALS] = 0
        }
        val wSum = w.values.sum().toDouble()
        if (wSum > 0) w.forEach { (f, p) -> add(f, rest * p / wSum) } else add(Fund.PERSONAL, rest)

        // 3) قاعدة «الدخل الزائد»: لما تدخل فوق متوسطك، نص زيادة المصروف بيروح للاستثمار
        if (i.avgMonthlyIncome > 0) {
            val excess = min(amount, i.monthIncomeSoFar + amount - i.avgMonthlyIncome)
            val personal = out[Fund.PERSONAL] ?: 0.0
            if (excess > 0 && personal > 0) {
                val move = personal * (excess / amount) * 0.5
                if (move >= 1) {
                    add(Fund.PERSONAL, -move)
                    add(Fund.INVESTMENT, move)
                    notes += "دخلك هذا الشهر تجاوز متوسطك (${money(i.avgMonthlyIncome)}) 👏 نقلنا ${money(move)} " +
                        "من مصروفك للاستثمار حتى ما تكبر مصاريفك مع كل زيادة (فخ تضخم نمط الحياة)."
                }
            }
        }

        // 4) سقوف: ما نحط أكثر من حاجة الصندوق، والزيادة تروح للخطوة التالية
        val emRoom = max(0.0, emergencyTarget(i) - i.bal(Fund.EMERGENCY))
        val emOver = (out[Fund.EMERGENCY] ?: 0.0) - emRoom
        if (emOver > 0.5) {
            out[Fund.EMERGENCY] = emRoom
            add(Fund.DEBT, emOver)
            notes += "صندوق الطوارئ وصل هدفه 🛡️ الزيادة (${money(emOver)}) راحت للخطوة الأهم بعده."
        }
        val debtRoom = max(0.0, i.debtRemaining - i.bal(Fund.DEBT))
        val debtOver = (out[Fund.DEBT] ?: 0.0) - debtRoom
        if (debtOver > 0.5) {
            out[Fund.DEBT] = debtRoom
            add(Fund.INVESTMENT, debtOver)
            if (debtRoom > 0.5) notes += "هذا الدخل بيكفي يغطي كل ديونك المتبقية! 🎉 الزيادة راحت للاستثمار."
        }
        val goalsRoom = max(0.0, i.goalsRoom)
        val goalOver = (out[Fund.GOALS] ?: 0.0) - goalsRoom
        if (goalOver > 0.5) {
            out[Fund.GOALS] = goalsRoom
            add(Fund.INVESTMENT, goalOver)
        }

        // 5) تقريب لأعداد صحيحة، والكسور تروح لمصروفك
        var used = 0.0
        for (f in Fund.entries) if (f != Fund.PERSONAL) {
            val v = floor(out[f] ?: 0.0)
            out[f] = v
            used += v
        }
        out[Fund.PERSONAL] = round2(amount - used)
        return Allocation(amount, phase, out, notes)
    }

    /** ليش هالمبلغ لهالصندوق؟ (المستشار يشرح كل سطر) */
    fun reason(f: Fund, i: AdvisorInput): String = when (f) {
        Fund.ESSENTIALS ->
            if (i.essentialsBudget > 0) "التزاماتك الشهرية (${money(i.essentialsBudget)}) تتغطى أول شي"
            else "ما حددت ميزانية أساسيات، تقدر تحددها من الإعدادات"
        Fund.EMERGENCY -> "رصيدك ${money(i.bal(Fund.EMERGENCY))} من هدف ${money(emergencyTarget(i))} (${i.emergencyMonths} شهور مصاريف)"
        Fund.DEBT ->
            if (i.debtRemaining > 0.5) "متبقي عليك ${money(i.debtRemaining)}" + (i.nextDebtName?.let { ". الهدف الحالي: $it" } ?: "")
            else "ما عليك ديون، الحمد لله 🎉"
        Fund.INVESTMENT -> "ادفع لنفسك أولاً: مال بيشتري أصول بتجيبلك دخل، وما بينصرف أبداً"
        Fund.GOALS -> i.topGoalName?.let { "بيروح لهدفك الأهم حالياً: $it" } ?: "أضف أهدافك (زواج، سيارة…) من شاشة الخطة"
        Fund.EDUCATION -> "أفضل استثمار هو بنفسك: مهارة جديدة = دخل أعلى"
        Fund.PERSONAL -> "اصرفه بلا ذنب! الاستمتاع جزء من الخطة حتى تكمل عليها"
        Fund.GIVING -> "العطاء بيبني عقلية الوفرة، وما نقص مال من صدقة"
    }

    /** مراجعة تعديلات المستخدم على التقسيم: المستشار بيحذّرك أو بيمدحك. */
    fun review(lines: Map<Fund, Double>, rec: Map<Fund, Double>, amount: Double, i: AdvisorInput): List<Warn> {
        val out = mutableListOf<Warn>()
        val sum = lines.values.sum()
        if (lines.values.any { it < 0 }) out += Warn("ما بصير مبلغ سالب.", severe = true)
        if (kotlin.math.abs(sum - amount) > 0.01) {
            val diff = amount - sum
            out += Warn(
                if (diff > 0) "باقي ${money(diff)} ما تقسّموا. وزّعهم على صندوق (المجموع لازم يساوي ${money(amount)})."
                else "المجموع (${money(sum)}) أكبر من المبلغ بـ ${money(-diff)}.",
                severe = true,
            )
        }
        fun v(f: Fund) = lines[f] ?: 0.0
        fun r(f: Fund) = rec[f] ?: 0.0
        val changed = Fund.entries.any { kotlin.math.abs(v(it) - r(it)) > 0.5 }
        if (!changed) return out

        if (v(Fund.INVESTMENT) < amount * MIN_INVEST_RATIO - 0.5) {
            out += Warn("أقل من 10% للاستثمار بيخالف أهم قاعدة بـ«أغنى رجل في بابل»: جزء من كل دخل إلك إنت وحدك، قبل أي حدا.")
        } else if (v(Fund.INVESTMENT) > r(Fund.INVESTMENT) + 0.5) {
            out += Warn("ممتاز! رفع الاستثمار بيسرّع حريتك المالية 💪", positive = true)
        }
        if (i.debtRemaining > 0.5 && v(Fund.DEBT) < r(Fund.DEBT) * 0.8) {
            out += Warn("تقليل حصة الديون بيطوّل مدة السداد. كل شهر زيادة بالدين = ضغط وفوايد، والدين أول عدو للثروة.")
        }
        if (r(Fund.EMERGENCY) > 0 && v(Fund.EMERGENCY) < r(Fund.EMERGENCY) * 0.8) {
            out += Warn("صندوق الطوارئ هو اللي بيمنعك ترجع تستدين عند أول مفاجأة. حاول ما تقلل منه.")
        }
        if (v(Fund.PERSONAL) > r(Fund.PERSONAL) * 1.3 && v(Fund.PERSONAL) > amount * 0.25) {
            out += Warn("رفع المصروف هو أسرع طريق لبقاء الثروة حلم. «المليونير المجاور» بيعيش أقل من دخله بكثير.")
        }
        if (v(Fund.DEBT) > max(0.0, i.debtRemaining - i.bal(Fund.DEBT)) + 1) {
            out += Warn("المبلغ أكبر من الدين المتبقي، الزيادة رح تضل بالصندوق بلا فايدة. خليها للاستثمار.")
        }
        if (v(Fund.GOALS) > max(0.0, i.goalsRoom) + 1) {
            out += Warn("المبلغ أكبر من المتبقي لأهدافك. الزيادة أفضل بالاستثمار.")
        }
        if (r(Fund.EDUCATION) > 0 && v(Fund.EDUCATION) <= 0) {
            out += Warn("إلغاء تطوير الذات توفير صغير بخسارة كبيرة: المهارة هي أسرع طريق لرفع دخلك كفريلانسر.")
        }
        return out
    }

    /** مراجعة النسب المخصصة بالإعدادات. */
    fun reviewWeights(w: Map<Fund, Int>, i: AdvisorInput): List<Warn> {
        val out = mutableListOf<Warn>()
        val total = w.values.sum()
        if (total != 100) out += Warn("مجموع النسب لازم يكون 100% (حالياً $total%).", severe = true)
        val phase = phaseOf(i)
        fun v(f: Fund) = w[f] ?: 0
        fun r(f: Fund) = phase.weights[f] ?: 0
        if (v(Fund.INVESTMENT) < 10) out += Warn("الاستثمار أقل من 10%: «ادفع لنفسك أولاً» هي القاعدة رقم واحد عند كل كتب الثراء.")
        if (i.debtRemaining > 0.5 && v(Fund.DEBT) < r(Fund.DEBT)) {
            out += Warn("عليك ديون والمستشار بينصح بـ ${r(Fund.DEBT)}% على الأقل للسداد بمرحلتك الحالية.")
        }
        if (v(Fund.EMERGENCY) < r(Fund.EMERGENCY)) {
            out += Warn("صندوق الطوارئ مش مكتمل، المستشار بينصح بـ ${r(Fund.EMERGENCY)}% بمرحلتك الحالية.")
        }
        if (v(Fund.PERSONAL) > 30) out += Warn("مصروف فوق 30% بيبطّئ وصولك لأهدافك بشكل كبير.")
        if (v(Fund.PERSONAL) == 0) out += Warn("صفر مصروف شخصي خطة ما بتعيش. اترك لنفسك شي تستمتع فيه حتى تكمل.")
        if (v(Fund.INVESTMENT) >= 20 && total == 100) out += Warn("استثمار ${v(Fund.INVESTMENT)}% رقم قوي 💪", positive = true)
        out += Warn("ملاحظة: النسب المخصصة ثابتة، بينما الوضع التلقائي بيتغير مع تقدّمك بالمراحل.")
        return out
    }

    /** تحذير قبل التحويل بين الصناديق. */
    fun transferWarning(from: Fund, to: Fund): String? = when {
        from == Fund.INVESTMENT -> "مال الاستثمار ما بينصرف، بس بيستثمر. سحبه يعني إنك بتاكل البذور بدل ما تزرعها 🌱"
        from == Fund.EMERGENCY && to != Fund.INVESTMENT -> "هل هي طوارئ حقيقية؟ إذا لا، خلي الدرع كامل."
        from == Fund.DEBT && to != Fund.INVESTMENT -> "هذا المال مخصص لتصفير ديونك، تأخيره بيطوّل الطريق."
        to == Fund.PERSONAL -> "تحويل للمصروف بيبطّئ أهدافك. متأكد؟"
        else -> null
    }

    fun expenseWarning(fund: Fund): String? = when (fund) {
        Fund.EMERGENCY -> "صرف من الطوارئ: تأكد إنها طوارئ حقيقية (مرض، عطل، ضرورة)، مش رغبة."
        Fund.GOALS -> "صرف من مدخرات الأهداف: هل هذا جزء من الهدف نفسه؟"
        else -> null
    }
}
