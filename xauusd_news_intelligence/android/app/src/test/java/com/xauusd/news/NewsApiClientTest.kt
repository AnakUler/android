package com.xauusd.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewsApiClientTest {
    @Test
    fun rejectsHttpBackendBeforeSendingDeviceCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            NewsApiClient.normalizeBaseUrl("http://example.com")
        }
    }

    @Test
    fun rejectsBackendUrlsWithUserInfoOrFragments() {
        assertThrows(IllegalArgumentException::class.java) {
            NewsApiClient.normalizeBaseUrl("https://user:password@example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NewsApiClient.normalizeBaseUrl("https://example.com/#redirect")
        }
    }

    @Test
    fun acceptsHttpsWorkerUrlAndRemovesTrailingSlash() {
        assertEquals("https://xauusd.example.workers.dev", NewsApiClient.normalizeBaseUrl(" https://xauusd.example.workers.dev/ "))
    }

    @Test
    fun parsesWorkerHealthAndConfigurationFlags() {
        val health = NewsApiClient.parseHealth("""{"ok":true,"configured":{"registrationCode":true,"fcm":false}}""")
        assertEquals(true, health.ok)
        assertEquals(true, health.registrationCodeConfigured)
        assertEquals(false, health.fcmConfigured)
    }

    @Test
    fun rejectsHealthResponseFromUnexpectedService() {
        assertThrows(IllegalStateException::class.java) {
            NewsApiClient.parseHealth("""{"ok":false,"configured":{}}""")
        }
    }
}

