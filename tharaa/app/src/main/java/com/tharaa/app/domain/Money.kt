package com.tharaa.app.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private val moneyFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private val qtyFormat = DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US))

fun money(v: Double): String = moneyFormat.format(if (abs(v) < 0.005) 0.0 else v) + " ₪"

fun moneySigned(v: Double): String = (if (v > 0.004) "+" else "") + money(v)

fun qty(v: Double): String = qtyFormat.format(v)

fun pct(v: Double): String = String.format(Locale.US, "%.1f%%", v)

fun round2(v: Double): Double = round(v * 100) / 100

/** يقبل الأرقام العربية (١٢٣) والفارسية والغربية، ويتجاهل فواصل الآلاف. */
fun parseAmount(s: String): Double? {
    val sb = StringBuilder()
    for (c in s.trim()) {
        when (c) {
            in '0'..'9' -> sb.append(c)
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            '.', '٫' -> sb.append('.')
            ',', '٬', ' ' -> {}
            else -> return null
        }
    }
    val v = sb.toString().toDoubleOrNull() ?: return null
    return if (v.isFinite() && v >= 0) v else null
}

fun monthKey(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

fun addMonths(ts: Long, months: Int): Long =
    Calendar.getInstance().apply { timeInMillis = ts; add(Calendar.MONTH, months) }.timeInMillis

fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private val arabicMonths = listOf(
    "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
    "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول",
)

fun monthTitle(key: String): String {
    val parts = key.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
    return "${arabicMonths[(m - 1).coerceIn(0, 11)]} ${parts[0]}"
}

fun dateText(ts: Long): String = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date(ts))

fun dateTimeText(ts: Long): String = SimpleDateFormat("d/M  HH:mm", Locale.US).format(Date(ts))

fun one(v: Double): String = String.format(Locale.US, "%.1f", v)
