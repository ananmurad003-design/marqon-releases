package com.tharaa.app.work

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Quote(val price: Double, val currency: String, val name: String?)

data class History(val quote: Quote, val closes: List<Double>)

/**
 * أسعار حية من Yahoo Finance (بدون مفتاح). أمثلة رموز:
 * AAPL, VOO, SPY, TEVA.TA (بورصة تل أبيب)، GC=F (ذهب للأونصة)، BTC-USD.
 */
object PriceService {
    private fun fetch(symbol: String): Quote? {
        val enc = URLEncoder.encode(symbol.trim().uppercase(), "UTF-8")
        val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$enc?range=1d&interval=1d")
        val c = url.openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Tharaa")
            if (c.responseCode != 200) return null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) return null
            Quote(
                price = price,
                currency = meta.optString("currency", "USD").ifBlank { "USD" },
                name = meta.optString("longName").ifBlank { meta.optString("shortName") }.ifBlank { null },
            )
        } catch (e: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }

    suspend fun quote(symbol: String): Quote? = withContext(Dispatchers.IO) { fetch(symbol) }

    /** أسعار الإغلاق اليومية لآخر سنة (لقراءة السهم والرسم). */
    suspend fun history(symbol: String): History? = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(symbol.trim().uppercase(), "UTF-8")
        val c = URL("https://query1.finance.yahoo.com/v8/finance/chart/$enc?range=1y&interval=1d").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Tharaa")
            if (c.responseCode != 200) return@withContext null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val meta = result.getJSONObject("meta")
            val arr = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
            val closes = (0 until arr.length()).filter { !arr.isNull(it) }.map { arr.getDouble(it) }
            val price = meta.optDouble("regularMarketPrice", closes.lastOrNull() ?: Double.NaN)
            if (price.isNaN()) return@withContext null
            History(
                Quote(
                    price = price,
                    currency = meta.optString("currency", "USD").ifBlank { "USD" },
                    name = meta.optString("longName").ifBlank { meta.optString("shortName") }.ifBlank { null },
                ),
                closes,
            )
        } catch (e: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }

    /** يحوّل السعر للشيكل. ILA = أغورة (بورصة تل أبيب). */
    suspend fun toIls(price: Double, currency: String, cache: MutableMap<String, Double>): Double? {
        // عملات بالوحدة الصغرى: ILA أغورة، GBp/GBX بنس، ZAc سنت جنوب أفريقي
        val (cur, p) = when (currency) {
            "ILA" -> "ILS" to price / 100.0
            "GBp", "GBX" -> "GBP" to price / 100.0
            "ZAc" -> "ZAR" to price / 100.0
            else -> currency.uppercase() to price
        }
        return when (cur) {
            "ILS" -> p
            else -> {
                val rate = cache[cur] ?: quote("${cur}ILS=X")?.price?.also { cache[cur] = it }
                rate?.let { p * it }
            }
        }
    }
}
