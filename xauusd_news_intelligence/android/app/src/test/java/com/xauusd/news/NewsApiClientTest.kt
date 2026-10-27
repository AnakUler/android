package com.xauusd.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
}

