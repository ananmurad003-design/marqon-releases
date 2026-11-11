package com.tharaa.app.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tharaa.app.MainActivity
import com.tharaa.app.R
import com.tharaa.app.TharaaApp
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.K
import com.tharaa.app.domain.Lessons
import com.tharaa.app.domain.money
import com.tharaa.app.domain.startOfDay
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Notifier {
    const val CHANNEL = "tharaa_reminders"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "تذكيرات ثراء", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تذكير تسجيل المصاريف ومراجعة الخطة"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, id: Int, title: String, text: String) {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            val mgr = NotificationManagerCompat.from(ctx)
            if (mgr.areNotificationsEnabled()) mgr.notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

object Scheduler {
    private const val REMINDER = "tharaa_daily_reminder"
    private const val BACKUP = "tharaa_auto_backup"

    fun scheduleReminder(ctx: Context, hour: Int, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(REMINDER)
            return
        }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(REMINDER, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun scheduleBackup(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(BACKUP, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** تذكير يومي: سجّل مصاريفك + نصيحة + تنبيه الصناديق القليلة + مراجعة أول الشهر. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = TharaaApp.instance.repo
        val s = repo.settingsNow()
        if (!s.remindersOn) return Result.success()
        val today = startOfDay(System.currentTimeMillis())
        val count = repo.todayExpenseCount(today)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val personal = repo.dao.balance(Fund.PERSONAL.key)

        val month = com.tharaa.app.domain.monthKey(System.currentTimeMillis())
        val paidCats = repo.dao.allTxns()
            .filter { it.type == com.tharaa.app.data.T.EXPENSE && com.tharaa.app.domain.monthKey(it.timestamp) == month }
            .mapNotNull { it.category }.toSet()
        val dueToday = s.commitments.filter {
            it.activeIn(month) && it.day == day && com.tharaa.app.domain.commitmentCategory(it.id) !in paidCats
        }
        val (title, text) = when {
            dueToday.isNotEmpty() -> "📅 موعد التزام اليوم" to dueToday.joinToString("، ") { "${it.name}: ${money(it.amount)}" } + ". ادفعه وسجّله من خطتي ← التزامات."
            day == 1 -> "📅 شهر جديد، بداية جديدة" to "افتح ثراء وراجع ترحيل نهاية الشهر. المتبقي من صناديقك بيستنّى يشتغل لصالحك."
            count == 0 -> "📝 سجّلت مصاريف اليوم؟" to "دقيقة وحدة بتفرق. ${Lessons.tipOfTheDay()}"
            personal in 0.01..50.0 -> "⚠️ مصروفك قرّب يخلص" to "باقي ${money(personal)} بمصروفك الشخصي. خفّف لحد الدخل الجاي 💪"
            else -> "💡 نصيحة اليوم" to Lessons.tipOfTheDay()
        }
        Notifier.show(applicationContext, 1001, title, text)
        return Result.success()
    }
}

/** نسخة احتياطية تلقائية يومية لملف اختاره المستخدم (مثلاً على Google Drive). */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = TharaaApp.instance.repo
        val uri = repo.settingsNow().autoBackupUri ?: return Result.success()
        return try {
            writeBackup(applicationContext, Uri.parse(uri), repo.exportJson())
            repo.setSetting(K.LAST_BACKUP, System.currentTimeMillis().toString())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun writeBackup(ctx: Context, uri: Uri, json: String) {
            val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("ما قدرت أفتح ملف النسخة")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
    }
}

