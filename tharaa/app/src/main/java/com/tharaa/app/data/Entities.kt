package com.tharaa.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** أنواع الحركات المالية. كل رصيد صندوق = مجموع حركاته (دفتر أستاذ)، لذلك الأرصدة لا تختل أبداً. */
object T {
    const val ALLOC = "ALLOC"          // جزء من دخل دخل لصندوق
    const val EXPENSE = "EXPENSE"      // مصروف
    const val TRANSFER = "TRANSFER"    // تحويل بين صندوقين
    const val DEBT_PAY = "DEBT_PAY"    // سداد دين
    const val BUY = "BUY"              // شراء أصل
    const val SELL = "SELL"            // بيع أصل
    const val ROLLOVER = "ROLLOVER"    // ترحيل نهاية الشهر
    const val ADJUST = "ADJUST"        // تعديل يدوي للرصيد
}

@Serializable
@Entity(tableName = "transactions")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val fund: String,
    val amount: Double,
    val category: String? = null,
    val note: String = "",
    val timestamp: Long,
    val incomeId: Long? = null,
    val goalId: Long? = null,
    val debtId: Long? = null,
    val holdingId: Long? = null,
)

@Serializable
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val source: String = "",
    val note: String = "",
    val phase: String = "",
    val timestamp: Long,
)

@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val total: Double,
    val interestRate: Double = 0.0,
    val note: String = "",
    val createdAt: Long,
)

object GoalKind {
    const val SAVING = "SAVING"       // هدف ادخار (زواج، سيارة)
    const val PORTFOLIO = "PORTFOLIO" // هدف حجم المحفظة الاستثمارية
}

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val target: Double,
    val priority: Int,
    val kind: String = GoalKind.SAVING,
    val done: Boolean = false,
    val createdAt: Long,
)

object AssetKind {
    const val STOCK = "STOCK"
    const val GOLD = "GOLD"
    const val CRYPTO = "CRYPTO"
    const val OTHER = "OTHER"

    fun title(k: String) = when (k) {
        STOCK -> "أسهم وصناديق"
        GOLD -> "ذهب"
        CRYPTO -> "عملات رقمية"
        else -> "أصل آخر"
    }

    fun emoji(k: String) = when (k) {
        STOCK -> "📊"
        GOLD -> "🥇"
        CRYPTO -> "🪙"
        else -> "🏷️"
    }
}

@Serializable
@Entity(tableName = "holdings")
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = AssetKind.STOCK,
    val symbol: String? = null,
    val name: String,
    val quantity: Double,
    val costIls: Double,
    val lastPriceIls: Double? = null,
    val manualValueIls: Double? = null,
    val currency: String? = null,
    val updatedAt: Long = 0,
) {
    fun value(): Double = lastPriceIls?.let { it * quantity } ?: manualValueIls ?: costIls
}

@Serializable
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val k: String,
    val v: String,
)
