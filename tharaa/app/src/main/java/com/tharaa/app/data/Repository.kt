package com.tharaa.app.data

import androidx.room.withTransaction
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.GoalInfo
import com.tharaa.app.domain.K
import com.tharaa.app.domain.RolloverMove
import com.tharaa.app.domain.Settings
import com.tharaa.app.domain.money
import com.tharaa.app.domain.monthKey
import com.tharaa.app.domain.round2
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min

@Serializable
data class BackupData(
    val app: String = "tharaa",
    val version: Int = 1,
    val exportedAt: Long,
    val txns: List<Txn>,
    val incomes: List<Income>,
    val debts: List<Debt>,
    val goals: List<Goal>,
    val holdings: List<Holding>,
    val settings: List<Setting>,
)

class Repository(private val db: AppDatabase) {
    val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    private fun now() = System.currentTimeMillis()

    suspend fun settingsNow(): Settings = Settings.from(dao.allSettings())

    suspend fun setSetting(key: String, value: String) = dao.putSetting(Setting(key, value))

    suspend fun setSettings(values: Map<String, String>) = db.withTransaction {
        values.forEach { (k, v) -> dao.putSetting(Setting(k, v)) }
    }

    // ---------------- الدخل ----------------

    suspend fun saveIncome(
        amount: Double,
        source: String,
        note: String,
        phase: String,
        lines: Map<Fund, Double>,
        goals: List<GoalInfo>,
    ): Long = db.withTransaction {
        val ts = now()
        val incomeId = dao.insertIncome(Income(amount = amount, source = source, note = note, phase = phase, timestamp = ts))
        val txns = mutableListOf<Txn>()
        lines.forEach { (fund, v) ->
            if (v <= 0.004) return@forEach
            if (fund == Fund.GOALS) {
                // توزيع حصة الأهداف بالترتيب: يمتلئ الهدف الأول ثم اللي بعده
                var left = v
                val open = goals.filter { it.goal.kind == GoalKind.SAVING && !it.goal.done && it.goal.target > 0 }
                for (g in open) {
                    if (left <= 0.004) break
                    val take = min(left, g.remaining)
                    if (take > 0.004) {
                        txns += Txn(type = T.ALLOC, fund = fund.key, amount = round2(take), timestamp = ts, incomeId = incomeId, goalId = g.goal.id, note = g.goal.name)
                        left -= take
                    }
                }
                if (left > 0.004) {
                    val g = open.firstOrNull() ?: goals.firstOrNull { it.goal.kind == GoalKind.SAVING }
                    txns += Txn(type = T.ALLOC, fund = fund.key, amount = round2(left), timestamp = ts, incomeId = incomeId, goalId = g?.goal?.id, note = g?.goal?.name ?: "")
                }
            } else {
                txns += Txn(type = T.ALLOC, fund = fund.key, amount = round2(v), timestamp = ts, incomeId = incomeId)
            }
        }
        dao.insertTxns(txns)
        incomeId
    }

    suspend fun deleteIncome(id: Long) = db.withTransaction {
        dao.deleteTxnsOfIncome(id)
        dao.deleteIncome(id)
    }

    // ---------------- المصاريف والتحويلات ----------------

    suspend fun addExpense(amount: Double, category: String, fund: Fund, note: String, goalId: Long?, timestamp: Long = now()) {
        dao.insertTxn(Txn(type = T.EXPENSE, fund = fund.key, amount = -amount, category = category, note = note, timestamp = timestamp, goalId = goalId))
    }

    suspend fun deleteTxn(id: Long) = dao.deleteTxn(id)

    suspend fun transfer(from: Fund, to: Fund, amount: Double, note: String, goalId: Long?) = db.withTransaction {
        val ts = now()
        val text = note.ifBlank { "تحويل ${from.title} ← ${to.title}" }
        dao.insertTxn(Txn(type = T.TRANSFER, fund = from.key, amount = -amount, note = text, timestamp = ts))
        dao.insertTxn(Txn(type = T.TRANSFER, fund = to.key, amount = amount, note = text, timestamp = ts, goalId = if (to == Fund.GOALS) goalId else null))
    }

    suspend fun applyRollover(moves: List<RolloverMove>, month: String, goals: List<GoalInfo>) = db.withTransaction {
        val ts = now()
        moves.forEach { m ->
            val note = "ترحيل نهاية الشهر: ${m.from.title} ← ${m.to.title}"
            dao.insertTxn(Txn(type = T.ROLLOVER, fund = m.from.key, amount = -m.amount, note = note, timestamp = ts))
            val goalId = if (m.to == Fund.GOALS) goals.firstOrNull()?.goal?.id else null
            dao.insertTxn(Txn(type = T.ROLLOVER, fund = m.to.key, amount = m.amount, note = note, timestamp = ts, goalId = goalId))
        }
        dao.putSetting(Setting(K.LAST_ROLLOVER, month))
    }

    suspend fun adjustBalance(fund: Fund, delta: Double, note: String) {
        dao.insertTxn(Txn(type = T.ADJUST, fund = fund.key, amount = delta, note = note.ifBlank { "تعديل رصيد" }, timestamp = now()))
    }

    // ---------------- الديون ----------------

    suspend fun addDebt(name: String, total: Double, rate: Double, note: String) =
        dao.insertDebt(Debt(name = name, total = total, interestRate = rate, note = note, createdAt = now()))

    suspend fun updateDebt(d: Debt) = dao.updateDebt(d)

    suspend fun deleteDebt(id: Long) = dao.deleteDebt(id)

    suspend fun payDebt(debt: Debt, amount: Double, from: Fund) {
        dao.insertTxn(Txn(type = T.DEBT_PAY, fund = from.key, amount = -amount, note = "سداد: ${debt.name}", timestamp = now(), debtId = debt.id))
    }

    // ---------------- الأهداف ----------------

    suspend fun addGoal(name: String, emoji: String, target: Double, kind: String) {
        val maxP = dao.allGoals().maxOfOrNull { it.priority } ?: 0
        dao.insertGoal(Goal(name = name, emoji = emoji, target = target, priority = maxP + 1, kind = kind, createdAt = now()))
    }

    suspend fun updateGoal(g: Goal) = dao.updateGoal(g)

    suspend fun deleteGoal(g: GoalInfo) = db.withTransaction {
        // المال المحجوز للهدف ما بيضيع: بيرجع للاستثمار
        if (g.goal.kind == GoalKind.SAVING && g.saved > 0.004) {
            val ts = now()
            val note = "حذف هدف: ${g.goal.name}"
            dao.insertTxn(Txn(type = T.TRANSFER, fund = Fund.GOALS.key, amount = -g.saved, note = note, timestamp = ts, goalId = g.goal.id))
            dao.insertTxn(Txn(type = T.TRANSFER, fund = Fund.INVESTMENT.key, amount = g.saved, note = note, timestamp = ts))
        }
        dao.detachGoal(g.goal.id)
        dao.deleteGoal(g.goal.id)
    }

    suspend fun moveGoal(goals: List<Goal>, goal: Goal, up: Boolean) = db.withTransaction {
        val list = goals.sortedWith(compareBy({ it.priority }, { it.id })).toMutableList()
        val i = list.indexOfFirst { it.id == goal.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return@withTransaction
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        list.forEachIndexed { idx, g -> if (g.priority != idx + 1) dao.updateGoal(g.copy(priority = idx + 1)) }
    }

    suspend fun createDefaultGoals() {
        if (dao.allGoals().isNotEmpty()) return
        val ts = now()
        dao.insertGoals(
            listOf(
                Goal(name = "الزواج", emoji = "💍", target = 50_000.0, priority = 1, createdAt = ts),
                Goal(name = "السيارة", emoji = "🚗", target = 40_000.0, priority = 2, createdAt = ts),
                Goal(name = "أول 10,000 ₪ بالمحفظة", emoji = "📈", target = 10_000.0, priority = 3, kind = GoalKind.PORTFOLIO, createdAt = ts),
            ),
        )
    }

    // ---------------- الاستثمار ----------------

    suspend fun buyAsset(existing: Holding?, kind: String, symbol: String?, name: String, quantity: Double, cost: Double): Long =
        db.withTransaction {
            val id = if (existing != null) {
                dao.updateHolding(
                    existing.copy(
                        quantity = existing.quantity + quantity,
                        costIls = existing.costIls + cost,
                        manualValueIls = existing.manualValueIls?.let { it + cost },
                    ),
                )
                existing.id
            } else {
                dao.insertHolding(Holding(kind = kind, symbol = symbol, name = name, quantity = quantity, costIls = cost))
            }
            dao.insertTxn(Txn(type = T.BUY, fund = Fund.INVESTMENT.key, amount = -cost, note = "شراء ${qtyText(quantity)} × $name", timestamp = now(), holdingId = id))
            id
        }

    suspend fun sellAsset(h: Holding, quantity: Double, proceeds: Double) = db.withTransaction {
        val q = min(quantity, h.quantity)
        val ratio = if (h.quantity > 0) q / h.quantity else 1.0
        val costPart = h.costIls * ratio
        val pnl = proceeds - costPart
        dao.updateHolding(
            h.copy(
                quantity = h.quantity - q,
                costIls = h.costIls - costPart,
                manualValueIls = h.manualValueIls?.let { it * (1 - ratio) },
            ),
        )
        val pnlText = if (pnl >= 0) "ربح ${money(pnl)}" else "خسارة ${money(-pnl)}"
        dao.insertTxn(Txn(type = T.SELL, fund = Fund.INVESTMENT.key, amount = proceeds, note = "بيع ${qtyText(q)} × ${h.name} ($pnlText)", timestamp = now(), holdingId = h.id))
    }

    suspend fun updateHolding(h: Holding) = dao.updateHolding(h)

    suspend fun deleteHolding(id: Long) = dao.deleteHolding(id)

    private fun qtyText(q: Double) = com.tharaa.app.domain.qty(q)

    // ---------------- النسخ الاحتياطي ----------------

    suspend fun exportJson(): String {
        val data = BackupData(
            exportedAt = now(),
            txns = dao.allTxns(),
            incomes = dao.allIncomes(),
            debts = dao.allDebts(),
            goals = dao.allGoals(),
            holdings = dao.allHoldings(),
            settings = dao.allSettings().filter { it.k != K.BACKUP_URI },
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    /** يستبدل كل البيانات بمحتوى النسخة. يرجع عدد الحركات المستوردة. */
    suspend fun importJson(text: String): Int {
        val data = json.decodeFromString(BackupData.serializer(), text)
        require(data.app == "tharaa") { "الملف مش نسخة احتياطية من ثراء" }
        db.withTransaction {
            val keepUri = dao.allSettings().firstOrNull { it.k == K.BACKUP_URI }
            dao.clearTxns(); dao.clearIncomes(); dao.clearDebts(); dao.clearGoals(); dao.clearHoldings(); dao.clearSettings()
            dao.insertIncomes(data.incomes)
            dao.insertDebts(data.debts)
            dao.insertGoals(data.goals)
            dao.insertHoldings(data.holdings)
            dao.insertTxns(data.txns)
            dao.insertSettings(data.settings.filter { it.k != K.BACKUP_URI } + listOfNotNull(keepUri))
        }
        return data.txns.size
    }

    suspend fun todayExpenseCount(dayStart: Long) = dao.expenseCountSince(dayStart)

    fun currentMonth() = monthKey(now())
}
