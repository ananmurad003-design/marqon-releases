package com.tharaa.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** التزام شهري ثابت: جمعية، قسط، اشتراك، إيجار… بيتغطى من الدخل قبل أي تقسيم. */
@Serializable
data class Commitment(
    val id: Long,
    val name: String,
    val amount: Double,
    /** يوم الدفع بالشهر (1 إلى 28). */
    val day: Int = 1,
    val kind: String = CommitmentKind.JAMEYA,
    /** للجمعية: الشهر اللي بتقبض فيه (yyyy-MM). */
    val receiveMonth: String? = null,
    /** الشهر الأخير للالتزام (yyyy-MM)، أو null إذا مستمر. */
    val endMonth: String? = null,
    /** للجمعية: عدد المشتركين (المبلغ اللي بتقبضه = القسط × العدد). */
    val members: Int = 0,
) {
    fun activeIn(month: String) = endMonth.isNullOrBlank() || month <= endMonth
    val payout get() = if (members > 0) amount * members else 0.0
}

object CommitmentKind {
    const val JAMEYA = "JAMEYA"
    const val INSTALLMENT = "INSTALLMENT"
    const val SUBSCRIPTION = "SUBSCRIPTION"
    const val RENT = "RENT"
    const val OTHER = "OTHER"
    val all = listOf(JAMEYA, INSTALLMENT, RENT, SUBSCRIPTION, OTHER)

    fun title(k: String) = when (k) {
        JAMEYA -> "جمعية"
        INSTALLMENT -> "قسط"
        SUBSCRIPTION -> "اشتراك"
        RENT -> "إيجار"
        else -> "التزام آخر"
    }

    fun emoji(k: String) = when (k) {
        JAMEYA -> "🤝"
        INSTALLMENT -> "🧾"
        SUBSCRIPTION -> "🔁"
        RENT -> "🏠"
        else -> "📅"
    }
}

data class CommitmentInfo(val c: Commitment, val paidThisMonth: Double) {
    val isPaid get() = paidThisMonth >= c.amount - 0.5
}

fun commitmentCategory(id: Long) = "commitment:$id"

object CommitmentCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(Commitment.serializer())

    fun decode(text: String?): List<Commitment> =
        if (text.isNullOrBlank()) emptyList() else try { json.decodeFromString(ser, text) } catch (e: Exception) { emptyList() }

    fun encode(list: List<Commitment>): String = json.encodeToString(ser, list)
}
