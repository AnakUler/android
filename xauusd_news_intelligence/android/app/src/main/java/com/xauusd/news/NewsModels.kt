package com.xauusd.news

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class NewsItem(
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAt: String,
    val level: String,
    val reason: String,
    val bullish: String,
    val bearish: String,
    val zoneAvailable: Boolean,
    val zoneLower: Double?,
    val zoneUpper: Double?,
    val currentPrice: Double?,
    val sampleCount: Int,
    val coverageHours: Double?,
    val lowerReturnPct: Double?,
    val upperReturnPct: Double?,
    val zoneMessage: String,
) {
    companion object {
        fun fromJson(json: JSONObject): NewsItem {
            val scenario = json.optJSONObject("scenario")
            val available = scenario?.optBoolean("available", false) == true
            return NewsItem(
                title = json.optString("title", "Berita XAUUSD"),
                summary = json.optString("summary", "").nullSafe(),
                source = json.optString("source", "Sumber tidak diketahui").nullSafe(),
                url = json.optString("url", "").nullSafe(),
                publishedAt = json.optString("publishedAt", "").nullSafe(),
                level = json.optString("level", "NORMAL").uppercase(Locale.ROOT),
                reason = json.optString("reason", "Belum ada alasan klasifikasi."),
                bullish = json.optString("bullish", "Dampak kenaikan bergantung pada reaksi pasar."),
                bearish = json.optString("bearish", "Dampak penurunan bergantung pada reaksi pasar."),
                zoneAvailable = available,
                zoneLower = scenario?.takeIf { available && it.has("lower") && !it.isNull("lower") }?.optDouble("lower"),
                zoneUpper = scenario?.takeIf { available && it.has("upper") && !it.isNull("upper") }?.optDouble("upper"),
                currentPrice = scenario?.takeIf { it.has("currentPrice") && !it.isNull("currentPrice") }?.optDouble("currentPrice"),
                sampleCount = scenario?.optInt("sampleCount", 0) ?: 0,
                coverageHours = scenario?.takeIf { it.has("coverageHours") && !it.isNull("coverageHours") }?.optDouble("coverageHours"),
                lowerReturnPct = scenario?.takeIf { it.has("lowerReturnPct") && !it.isNull("lowerReturnPct") }?.optDouble("lowerReturnPct"),
                upperReturnPct = scenario?.takeIf { it.has("upperReturnPct") && !it.isNull("upperReturnPct") }?.optDouble("upperReturnPct"),
                zoneMessage = scenario?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: "Zona harga 4 jam belum tersedia karena data harga belum memadai atau sudah kedaluwarsa.",
            )
        }
    }
}

private fun String.nullSafe(): String = if (this == "null") "" else this

data class CalendarEvent(
    val id: String,
    val title: String,
    val source: String,
    val url: String,
    val scheduledAt: String,
    val impact: String,
    val timezone: String,
) {
    companion object {
        fun fromJson(json: JSONObject) = CalendarEvent(
            id = json.optString("id", ""),
            title = json.optString("title", "Rilis ekonomi"),
            source = json.optString("source", "Sumber resmi"),
            url = json.optString("url", ""),
            scheduledAt = json.optString("scheduledAt", ""),
            impact = json.optString("impact", "MEDIUM").uppercase(Locale.ROOT),
            timezone = json.optString("timezone", "UTC"),
        )
    }
}

data class NewsFeedResponse(
    val items: List<NewsItem>,
    val calendarEvents: List<CalendarEvent>,
    val lastUpdatedAt: String?,
    val status: JSONObject?,
) {
    companion object {
        fun fromJson(json: JSONObject): NewsFeedResponse = NewsFeedResponse(
            items = json.optJSONArray("items").toObjects(NewsItem::fromJson),
            calendarEvents = json.optJSONArray("calendarEvents").toObjects(CalendarEvent::fromJson),
            lastUpdatedAt = json.optString("lastUpdatedAt").takeUnless { it.isBlank() || it == "null" },
            status = json.optJSONObject("status"),
        )
    }
}

private fun <T> JSONArray?.toObjects(convert: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let { add(convert(it)) }
    }
}

object NewsDetailFormatter {
    fun format(item: NewsItem): String = buildString {
        appendLine("Klasifikasi: ${item.level}")
        appendLine("Alasan: ${item.reason}")
        appendLine()
        appendLine("JIKA EMAS NAIK")
        appendLine(item.bullish)
        appendLine()
        appendLine("JIKA EMAS TURUN")
        appendLine(item.bearish)
        appendLine()
        appendLine("SKENARIO ZONA 4 JAM")
        if (item.zoneAvailable && item.zoneLower != null && item.zoneUpper != null) {
            appendLine("Batas bawah historis: ${formatPrice(item.zoneLower)}")
            appendLine("Batas atas historis: ${formatPrice(item.zoneUpper)}")
            item.currentPrice?.let { appendLine("Harga acuan global: ${formatPrice(it)}") }
            appendLine("Jumlah sampel return 4 jam: ${item.sampleCount}")
            item.coverageHours?.let { appendLine("Cakupan data: ${String.format(Locale.US, "%.1f", it)} jam") }
            item.lowerReturnPct?.let { appendLine("Persentil bawah: ${formatPercent(it)}") }
            item.upperReturnPct?.let { appendLine("Persentil atas: ${formatPercent(it)}") }
            appendLine("Rentang empiris indikatif, bukan target harga atau kepastian arah.")
        } else {
            appendLine(item.zoneMessage)
        }
    }.trim()

    private fun formatPrice(value: Double) = "$" + String.format(Locale.US, "%.2f", value)
    private fun formatPercent(value: Double) = String.format(Locale.US, "%+.2f%%", value)
}

