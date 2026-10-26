package com.xauusd.news

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewsItemTest {
    @Test fun missingOptionalFieldsDoNotCrashParsing() {
        val item = NewsItem.fromJson(JSONObject().put("title", "Fed announcement"))
        assertEquals("Fed announcement", item.title)
        assertEquals("NORMAL", item.level)
        assertFalse(item.zoneAvailable)
        assertTrue(item.zoneMessage.isNotBlank())
    }

    @Test fun upcomingEconomicCalendarIsParsed() {
        val feed = NewsFeedResponse.fromJson(JSONObject()
            .put("calendarEvents", org.json.JSONArray().put(JSONObject()
                .put("id", "cpi-2026")
                .put("title", "Consumer Price Index")
                .put("source", "U.S. Bureau of Labor Statistics calendar")
                .put("url", "https://www.bls.gov/schedule/news_release/bls.ics")
                .put("scheduledAt", "2026-11-10T13:30:00.000Z")
                .put("impact", "HIGH")
                .put("timezone", "America/New_York"))))
        assertEquals(1, feed.calendarEvents.size)
        assertEquals("HIGH", feed.calendarEvents[0].impact)
    }

    @Test fun availableZoneIsParsedAndFormattedInIndonesian() {
        val json = JSONObject()
            .put("title", "US inflation data")
            .put("level", "HIGH")
            .put("publishedAt", "2026-10-26T10:00:00.000Z")
            .put("scenario", JSONObject().put("available", true).put("lower", 3950.25).put("upper", 4050.75).put("currentPrice", 4000.0).put("sampleCount", 100).put("coverageHours", 24.0).put("lowerReturnPct", -1.2).put("upperReturnPct", 1.3).put("uncertainty", "Rentang historis indikatif."))
        val item = NewsItem.fromJson(json)
        assertTrue(item.zoneAvailable)
        assertEquals(3950.25, item.zoneLower!!, 0.001)
        assertTrue(NewsDetailFormatter.format(item).contains("Batas bawah historis"))
        assertTrue(NewsDetailFormatter.format(item).contains("JIKA EMAS NAIK"))
        assertTrue(NewsDetailFormatter.format(item).contains("Jumlah sampel return 4 jam: 100"))
    }
}
