package com.tharaa.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ModelHolding(val symbol: String, val name: String, val weight: Int)

data class ModelPortfolio(
    val id: String,
    val investor: String,
    val emoji: String,
    val title: String,
    val philosophy: String,
    val quote: String,
    /** 1 منخفض، 2 متوسط، 3 مرتفع */
    val risk: Int,
    val halal: Boolean,
    val holdings: List<ModelHolding>,
    val note: String = "",
)

/**
 * محافظ وفلسفات أشهر المستثمرين. الأوزان تقريبية للتعليم، والمحافظ الحقيقية بتتغير
 * (تحقق من آخر إفصاحات 13F لبيركشاير مثلاً).
 */
object Investors {
    val broadFunds = setOf("VOO", "SPY", "IVV", "VT", "VTI", "VXUS", "QQQ", "BND", "SPUS", "HLAL", "UMMA", "BRK-B", "TLT", "IEF", "GLD", "DBC", "SHV")
    val halalFunds = setOf("SPUS", "HLAL", "UMMA")

    val portfolios = listOf(
        ModelPortfolio(
            "buffett_simple", "وارن بافت", "🎩", "نصيحة بافت لعامة الناس",
            "بافت كتب بوصيته: 90% من المال بصندوق مؤشر S&P 500 منخفض التكلفة، و10% سندات حكومية قصيرة. " +
                "بيقول إن أغلب الناس ما رح يغلبوا السوق، فالأذكى إنك تملك السوق كله وتصبر.",
            "«القاعدة الأولى: لا تخسر المال. القاعدة الثانية: لا تنسَ القاعدة الأولى.»",
            1, false,
            listOf(ModelHolding("VOO", "صندوق S&P 500", 90), ModelHolding("SHV", "سندات حكومية قصيرة", 10)),
        ),
        ModelPortfolio(
            "berkshire", "وارن بافت", "🏛️", "محفظة بيركشاير هاثاواي",
            "بافت بيشتري شركات ممتازة بسعر معقول وبيحتفظ فيها سنين طويلة: علامة تجارية قوية، أرباح ثابتة، وإدارة أمينة. " +
                "أسهل طريقة تملك محفظته: سهم بيركشاير نفسه (BRK-B).",
            "«الوقت صديق الشركة الممتازة، وعدو الشركة المتوسطة.»",
            2, false,
            listOf(
                ModelHolding("AAPL", "آبل", 27), ModelHolding("AXP", "أمريكان إكسبريس", 18),
                ModelHolding("BAC", "بنك أوف أمريكا", 12), ModelHolding("KO", "كوكاكولا", 11),
                ModelHolding("CVX", "شيفرون", 8), ModelHolding("MCO", "موديز", 6),
                ModelHolding("OXY", "أوكسيدنتال", 6), ModelHolding("CB", "تشب", 6),
                ModelHolding("KHC", "كرافت هاينز", 6),
            ),
            "أوزان تقريبية من الإفصاحات الأخيرة المعروفة، وبتتغير كل ربع سنة.",
        ),
        ModelPortfolio(
            "bogle", "جون بوجل", "📊", "محفظة الصناديق الثلاثة",
            "مؤسس فانغارد: لا تدوّر على الإبرة، اشتري كومة القش كلها. ثلاث صناديق بتغطي العالم كله بتكلفة شبه صفر.",
            "«لا تبحث عن الإبرة في كومة القش، فقط اشترِ كومة القش.»",
            1, false,
            listOf(ModelHolding("VTI", "السوق الأمريكي كله", 60), ModelHolding("VXUS", "الأسواق العالمية", 20), ModelHolding("BND", "سندات", 20)),
        ),
        ModelPortfolio(
            "dalio", "راي داليو", "🌦️", "محفظة كل الأجواء",
            "مؤسس أكبر صندوق تحوط بالعالم صمّم محفظة بتصمد بكل الظروف: نمو، ركود، تضخم. توازن بين الأسهم والسندات والذهب والسلع.",
            "«التنويع هو الوجبة المجانية الوحيدة في الاستثمار.»",
            1, false,
            listOf(
                ModelHolding("VTI", "أسهم", 30), ModelHolding("TLT", "سندات طويلة", 40),
                ModelHolding("IEF", "سندات متوسطة", 15), ModelHolding("GLD", "ذهب", 8), ModelHolding("DBC", "سلع", 7),
            ),
        ),
        ModelPortfolio(
            "halal", "الاستثمار الحلال", "🕌", "محفظة متوافقة مع الشريعة",
            "صناديق بتستبعد البنوك الربوية والكحول والقمار والشركات المثقلة بالديون، وبتعطيك تنويع واسع بنفس الوقت.",
            "«ما نقص مال من صدقة» والبركة بالمال الحلال.",
            2, true,
            listOf(ModelHolding("SPUS", "S&P 500 الشرعي", 50), ModelHolding("HLAL", "السوق الأمريكي الشرعي", 25), ModelHolding("UMMA", "العالم الإسلامي", 25)),
            "تأكد من فتوى جهة تثق فيها، وزكِّ أرباحك.",
        ),
        ModelPortfolio(
            "musk", "إيلون ماسك", "🚀", "فلسفة رهان المستقبل",
            "ماسك ما بيدير محفظة استثمار عامة: ثروته كلها تقريباً بشركاته (تسلا وسبيس إكس). الدرس منه: الثروة الكبيرة بتنبني " +
                "بالبناء والرهان على المستقبل. لو بدك تتعرض لهالفكرة: صناديق التكنولوجيا، وبحد أقصى 10% من محفظتك لأنها عالية المخاطر.",
            "«عندما يكون الشيء مهماً بما يكفي، افعله حتى لو كانت الاحتمالات ضدك.»",
            3, false,
            listOf(ModelHolding("QQQ", "أكبر 100 شركة تكنولوجيا", 60), ModelHolding("TSLA", "تسلا", 20), ModelHolding("NVDA", "إنفيديا", 20)),
            "تقلبات عالية جداً. مش أساس محفظة.",
        ),
        ModelPortfolio(
            "carnegie", "أندرو كارنيغي", "🏭", "سلة واحدة وراقبها",
            "كارنيغي صار أغنى رجل بزمانه من صناعة الحديد، مش من الأسهم. فلسفته: ركّز كل طاقتك ومالك بالمجال اللي بتفهمه أكثر من أي حد. " +
                "لإلك كفريلانسر: أقوى استثمار ممكن يكون مهارتك وشغلك الخاص، وبعدين الصناديق للباقي.",
            "«ضع كل بيضك في سلة واحدة، ثم راقب تلك السلة جيداً.»",
            3, false,
            emptyList(),
            "فلسفة بلا محفظة: استثمر بصندوق تطوير الذات وبمشروعك.",
        ),
    )

    fun withSymbol(symbol: String): List<ModelPortfolio> =
        portfolios.filter { p -> p.holdings.any { it.symbol.equals(symbol, ignoreCase = true) } }
}

data class PlanLine(val symbol: String, val name: String, val share: Int, val amount: Double, val why: String)

data class InvestPlan(val headline: String, val lines: List<PlanLine>, val tips: List<String>)

/** «خطتي لمالي»: كيف أقسّم نقد صندوق الاستثمار حسب مرحلتي (نواة + أقمار). */
fun suggestPlan(cash: Double, phase: Phase, halal: Boolean, portfolioValue: Double): InvestPlan {
    val core = if (halal) PlanLine("SPUS", "S&P 500 الشرعي", 0, 0.0, "النواة: مئات الشركات مرة وحدة، متوافقة مع الشريعة")
    else PlanLine("VOO", "صندوق S&P 500", 0, 0.0, "النواة: 500 أكبر شركة أمريكية. نصيحة بافت رقم 1")
    val quality = if (halal) PlanLine("UMMA", "صندوق العالم الإسلامي", 0, 0.0, "تنويع عالمي متوافق مع الشريعة")
    else PlanLine("BRK-B", "بيركشاير هاثاواي", 0, 0.0, "تملك محفظة بافت كلها بسهم واحد")
    val growth = if (halal) PlanLine("HLAL", "السوق الأمريكي الشرعي", 0, 0.0, "نمو إضافي متوافق مع الشريعة")
    else PlanLine("QQQ", "صندوق التكنولوجيا", 0, 0.0, "رهان المستقبل (فلسفة ماسك) بجرعة صغيرة")

    val (headline, split) = when (phase) {
        Phase.FOUNDATION, Phase.DEBT_FREE ->
            "أولويتك الحالية الديون والطوارئ. الـ10% بتروح كلها للنواة: بسيط وآمن." to listOf(core to 100)
        Phase.SECURITY ->
            "عندك أساس جيد. نواة قوية ولمسة من محفظة بافت." to listOf(core to 80, quality to 20)
        Phase.WEALTH ->
            "إنت بمرحلة بناء الثروة: نواة + جودة + جرعة نمو صغيرة." to listOf(core to 70, quality to 20, growth to 10)
    }
    val lines = split.map { (l, s) -> l.copy(share = s, amount = round2(cash * s / 100.0)) }
    val tips = mutableListOf<String>()
    if (cash < 200) tips += "المبلغ صغير؟ عادي. معظم الوسطاء بيسمحوا تشتري جزء من سهم. المهم تبدأ وتستمر كل شهر."
    tips += "اشتري على دفعات شهرية ثابتة (DCA): بتشتري أكثر لما السعر ينزل وأقل لما يطلع، وبترتاح من توقيت السوق."
    tips += "لا تحط بسهم فردي أكثر من 10% من محفظتك."
    if (portfolioValue <= 0) tips += "أول خطوة: افتح حساب عند وسيط موثوق مرخّص، وابدأ بالنواة فقط."
    return InvestPlan(headline, lines, tips)
}

data class StockStats(
    val price: Double,
    val change1y: Double,
    val change3m: Double,
    val high: Double,
    val low: Double,
    val fromHigh: Double,
    val sma50: Double,
    val sma200: Double,
    val volatility: Double,
    val maxDrawdown: Double,
)

/** قراءة رقمية لسعر السهم آخر سنة. */
fun analyzeCloses(closes: List<Double>, price: Double = closes.lastOrNull() ?: 0.0): StockStats? {
    if (closes.size < 20) return null
    fun pctChange(from: Double) = if (from > 0) (price - from) / from * 100 else 0.0
    val high = max(closes.max(), price)
    val low = min(closes.min(), price)
    val sma50 = closes.takeLast(50).average()
    val sma200 = closes.takeLast(200).average()
    val returns = closes.zipWithNext { a, b -> if (a > 0) (b - a) / a else 0.0 }
    val mean = returns.average()
    val sd = sqrt(returns.sumOf { (it - mean) * (it - mean) } / max(1, returns.size - 1))
    var peak = closes.first()
    var dd = 0.0
    for (c in closes) {
        peak = max(peak, c)
        if (peak > 0) dd = min(dd, (c - peak) / peak)
    }
    return StockStats(
        price = price,
        change1y = pctChange(closes.first()),
        change3m = pctChange(closes[max(0, closes.size - 63)]),
        high = high,
        low = low,
        fromHigh = if (high > 0) (price - high) / high * 100 else 0.0,
        sma50 = sma50,
        sma200 = sma200,
        volatility = sd * sqrt(252.0) * 100,
        maxDrawdown = dd * 100,
    )
}

/** قراءة المدرب للسهم: قواعد تعليمية من بافت وغراهام وبوجل. */
fun stockVerdict(symbol: String, st: StockStats, halalOnly: Boolean, investCash: Double, portfolioValue: Double): List<Warn> {
    val out = mutableListOf<Warn>()
    val sym = symbol.uppercase()
    val isFund = sym in Investors.broadFunds
    if (isFund) out += Warn("صندوق متنوع: مناسب كأساس لمحفظتك، ومخاطرته أقل من سهم واحد.", positive = true)

    when {
        st.price > st.sma200 && st.sma50 > st.sma200 -> out += Warn("الاتجاه صاعد: السعر فوق متوسط 200 يوم، والمتوسط القصير فوق الطويل.", positive = true)
        st.price < st.sma200 -> out += Warn("السعر تحت متوسط 200 يوم: الاتجاه ضعيف حالياً. إذا الشركة قوية ممكن تكون فرصة، بس ادخل على دفعات.")
        else -> out += Warn("الاتجاه محايد: السعر قريب من متوسطاته.")
    }
    when {
        st.fromHigh <= -30 -> out += Warn(
            "نازل ${pct(abs(st.fromHigh))} عن قمته السنوية. غراهام بيقول «السيد ماركت» متشائم، وهاي ممكن تكون فرصة " +
                "أو فخ. اعرف ليش نزل قبل ما تشتري.",
        )
        st.fromHigh > -5 -> out += Warn("قريب من أعلى سعر بالسنة. لا تطارد السعر: اشتري على دفعات بدل مرة وحدة.")
    }
    when {
        st.volatility > 45 -> out += Warn("تقلّب عالي جداً (${pct(st.volatility)} سنوياً): ممكن ينزل 50% بسرعة. خليه أقل من 5% من محفظتك.")
        st.volatility < 20 -> out += Warn("تقلّب منخفض نسبياً (${pct(st.volatility)}): مناسب للصبر الطويل.", positive = true)
    }
    if (st.maxDrawdown < -35) out += Warn("بآخر سنة نزل لحد ${pct(abs(st.maxDrawdown))} من قمته. اسأل حالك: بتقدر تتحمّل هالنزول بدون ما تبيع؟")

    val famous = Investors.withSymbol(sym)
    if (famous.isNotEmpty()) out += Warn("موجود بـ: ${famous.joinToString("، ") { "${it.title} (${it.investor})" }}.", positive = true)
    if (halalOnly && sym !in Investors.halalFunds) out += Warn("فعّلت «حلال فقط»: هذا الرمز مش من الصناديق الشرعية المعروفة. تحقق من توافقه قبل الشراء.")

    val total = portfolioValue + investCash
    val maxPos = if (isFund) investCash else min(investCash, total * 0.10)
    if (investCash > 0) {
        out += Warn(
            if (isFund) "بصندوق الاستثمار عندك ${money(investCash)}. ممكن يكون هذا الصندوق نواة محفظتك."
            else "أقصى مبلغ مقترح لهذا السهم: ${money(maxPos)} (10% من محفظتك كاملة) حتى ما تخاطر بكل شي.",
        )
    }
    return out
}
