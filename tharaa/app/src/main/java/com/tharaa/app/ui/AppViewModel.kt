package com.tharaa.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tharaa.app.TharaaApp
import com.tharaa.app.data.Debt
import com.tharaa.app.data.Goal
import com.tharaa.app.data.Holding
import com.tharaa.app.data.RawData
import com.tharaa.app.domain.Advisor
import com.tharaa.app.domain.Allocation
import com.tharaa.app.domain.AppState
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.GoalInfo
import com.tharaa.app.domain.K
import com.tharaa.app.domain.derive
import com.tharaa.app.domain.money
import com.tharaa.app.work.BackupWorker
import com.tharaa.app.work.PriceService
import com.tharaa.app.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TharaaApp).repo
    private val dao = repo.dao

    val state: StateFlow<AppState> = combine(
        dao.txns(), dao.incomes(), dao.debts(), dao.goals(), dao.holdings(),
    ) { t, i, d, g, h -> RawData(t, i, d, g, h) }
        .combine(dao.settings()) { raw, s -> derive(raw, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _busy = MutableSharedFlow<Boolean>(replay = 1)
    val busy: SharedFlow<Boolean> = _busy

    private fun say(text: String) { _messages.tryEmit(text) }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            say("صار خطأ: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------- البداية ----------

    fun finishOnboarding() = launch {
        repo.createDefaultGoals()
        repo.setSettings(mapOf(K.ONBOARDED to "true", K.LAST_ROLLOVER to repo.currentMonth()))
    }

    private var rolloverRunning = false

    fun applyRollover() {
        if (rolloverRunning) return
        rolloverRunning = true
        viewModelScope.launch {
            try {
                val s = state.value
                if (s.pendingRollover.isNotEmpty()) {
                    repo.applyRollover(s.pendingRollover, s.currentMonth, s.goals)
                    say("تم ترحيل نهاية الشهر ✅ مالك صار يشتغل لصالحك")
                }
            } catch (e: Exception) {
                say("صار خطأ: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                rolloverRunning = false
            }
        }
    }

    fun skipRollover() = launch { repo.setSetting(K.LAST_ROLLOVER, state.value.currentMonth) }

    // ---------- الدخل ----------

    fun plan(amount: Double): Allocation? = state.value.input?.let { Advisor.plan(amount, it) }

    fun saveIncome(amount: Double, source: String, note: String, plan: Allocation, lines: Map<Fund, Double>, onDone: () -> Unit) = launch {
        repo.saveIncome(amount, source.trim(), note.trim(), plan.phase.name, lines, state.value.goals)
        say("تم تقسيم ${money(amount)} على صناديقك 💰")
        onDone()
    }

    fun deleteIncome(id: Long) = launch {
        repo.deleteIncome(id)
        say("انحذف الدخل وتقسيمه")
    }

    // ---------- المصاريف ----------

    fun addExpense(amount: Double, category: String, fund: Fund, note: String, goalId: Long?, onDone: () -> Unit) = launch {
        repo.addExpense(amount, category, fund, note.trim(), goalId)
        val left = state.value.bal(fund) - amount
        say(if (left < 50 && fund == Fund.PERSONAL) "انسجل ✅ انتبه: باقي ${money(left)} بمصروفك" else "انسجل المصروف ✅")
        onDone()
    }

    fun deleteTxn(id: Long) = launch { repo.deleteTxn(id); say("انحذف") }

    fun transfer(from: Fund, to: Fund, amount: Double, note: String) = launch {
        val goalId = state.value.goals.firstOrNull { !it.reached && it.goal.kind == "SAVING" }?.goal?.id
        repo.transfer(from, to, amount, note, goalId)
        say("تم تحويل ${money(amount)} ✅")
    }

    fun adjust(fund: Fund, newBalance: Double) = launch {
        val delta = newBalance - state.value.bal(fund)
        if (kotlin.math.abs(delta) > 0.004) repo.adjustBalance(fund, delta, "تعديل يدوي للرصيد")
        say("تم تعديل الرصيد")
    }

    // ---------- الديون ----------

    fun addDebt(name: String, total: Double, rate: Double, note: String) = launch {
        repo.addDebt(name.trim(), total, rate, note.trim())
        say("انضاف الدين. يلا نصفّره 💪")
    }

    fun updateDebt(d: Debt) = launch { repo.updateDebt(d) }

    fun deleteDebt(id: Long) = launch { repo.deleteDebt(id) }

    fun payDebt(d: Debt, amount: Double, from: Fund, remainingBefore: Double) = launch {
        repo.payDebt(d, amount, from)
        say(if (amount >= remainingBefore - 0.5) "🎉 مبروك! خلّصت دين «${d.name}» بالكامل" else "تم السداد ✅ باقي ${money(remainingBefore - amount)}")
    }

    // ---------- الأهداف ----------

    fun addGoal(name: String, emoji: String, target: Double, kind: String) = launch { repo.addGoal(name.trim(), emoji, target, kind) }

    fun updateGoal(g: Goal) = launch { repo.updateGoal(g) }

    fun deleteGoal(g: GoalInfo) = launch {
        repo.deleteGoal(g)
        say(if (g.saved > 0 && g.goal.kind == "SAVING") "انحذف الهدف ورجع ${money(g.saved)} للاستثمار" else "انحذف الهدف")
    }

    fun moveGoal(g: Goal, up: Boolean) = launch { repo.moveGoal(state.value.raw.goals, g, up) }

    // ---------- الاستثمار ----------

    fun buy(existing: Holding?, kind: String, symbol: String?, name: String, quantity: Double, cost: Double) = launch {
        val id = repo.buyAsset(existing, kind, symbol?.trim()?.uppercase()?.ifBlank { null }, name.trim(), quantity, cost)
        say("مبروك! اشتريت أصل جديد 🌱")
        if (!symbol.isNullOrBlank()) refreshPrices(onlyId = id)
    }

    fun sell(h: Holding, quantity: Double, proceeds: Double) = launch {
        repo.sellAsset(h, quantity, proceeds)
        say("تم البيع، والمبلغ رجع لصندوق الاستثمار")
    }

    fun setManualValue(h: Holding, value: Double) = launch {
        repo.updateHolding(h.copy(manualValueIls = value, lastPriceIls = if (h.symbol == null) null else h.lastPriceIls, updatedAt = System.currentTimeMillis()))
    }

    fun deleteHolding(id: Long) = launch { repo.deleteHolding(id) }

    fun refreshPrices(onlyId: Long? = null) = launch {
        _busy.emit(true)
        try {
            val list = dao.allHoldings().filter { !it.symbol.isNullOrBlank() && (onlyId == null || it.id == onlyId) }
            if (list.isEmpty()) {
                if (onlyId == null) say("ما في أصول برموز لتحديثها")
                return@launch
            }
            val rates = mutableMapOf<String, Double>()
            var ok = 0
            val failed = mutableListOf<String>()
            for (h in list) {
                val q = PriceService.quote(h.symbol!!)
                val ils = q?.let { PriceService.toIls(it.price, it.currency, rates) }
                if (q != null && ils != null) {
                    repo.updateHolding(h.copy(lastPriceIls = ils, currency = q.currency, updatedAt = System.currentTimeMillis()))
                    ok++
                } else failed += h.symbol ?: "?"
            }
            say(if (failed.isEmpty()) "تحدّثت الأسعار ✅" else "تحدّث $ok، وتعذّر: ${failed.joinToString()} (تأكد من الرمز والإنترنت)")
        } finally {
            _busy.emit(false)
        }
    }

    // ---------- الإعدادات ----------

    fun setSetting(key: String, value: String) = launch {
        repo.setSetting(key, value)
        if (key == K.REMINDERS || key == K.REMINDER_HOUR) {
            val s = repo.settingsNow()
            Scheduler.scheduleReminder(getApplication(), s.reminderHour, s.remindersOn)
        }
    }

    fun saveCustomWeights(w: Map<Fund, Int>) = launch {
        repo.setSettings(Fund.entries.associate { K.weight(it) to (w[it] ?: 0).toString() } + (K.PLAN_MODE to "custom"))
        say("انحفظت نسبك. المستشار رح يضل ينبّهك إذا في أفضل")
    }

    // ---------- النسخ الاحتياطي ----------

    fun exportTo(uri: Uri) = launch {
        val ctx = getApplication<Application>()
        val json = repo.exportJson()
        withContext(Dispatchers.IO) { BackupWorker.writeBackup(ctx, uri, json) }
        say("انحفظت النسخة الاحتياطية ✅")
    }

    fun importFrom(uri: Uri) = launch {
        val ctx = getApplication<Application>()
        val text = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("ما قدرت أقرأ الملف")
        }
        val n = repo.importJson(text)
        say("تم استرجاع البيانات ($n حركة) ✅")
    }

    fun setAutoBackup(uri: Uri) = launch {
        val ctx = getApplication<Application>()
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val json = repo.exportJson()
        withContext(Dispatchers.IO) { BackupWorker.writeBackup(ctx, uri, json) }
        repo.setSettings(mapOf(K.BACKUP_URI to uri.toString(), K.LAST_BACKUP to System.currentTimeMillis().toString()))
        say("تفعّل النسخ التلقائي اليومي ✅")
    }

    fun disableAutoBackup() = launch { repo.setSetting(K.BACKUP_URI, "") }

    fun backupNow() = launch {
        val uri = state.value.settings.autoBackupUri ?: return@launch say("ما في ملف نسخ تلقائي")
        val json = repo.exportJson()
        withContext(Dispatchers.IO) { BackupWorker.writeBackup(getApplication(), Uri.parse(uri), json) }
        repo.setSetting(K.LAST_BACKUP, System.currentTimeMillis().toString())
        say("تم النسخ ✅")
    }
}
