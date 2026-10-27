package com.xauusd.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStateTest {
    @Test
    fun backendReadyRequiresHttpsUrlAndRegistrationCode() {
        assertTrue(SetupState("https://gold.example.workers.dev", "device-code", "", "", "", "").backendReady)
        assertFalse(SetupState("http://gold.example.workers.dev", "device-code", "", "", "", "").backendReady)
        assertFalse(SetupState("https://", "device-code", "", "", "", "").backendReady)
        assertFalse(SetupState("https://user:secret@gold.example.workers.dev", "device-code", "", "", "", "").backendReady)
        assertFalse(SetupState("https://gold.example.workers.dev", "", "", "", "", "").backendReady)
    }

    @Test
    fun missingFirebaseFieldsAreReturnedInFormOrder() {
        val state = SetupState("", "", "key", "", "project", "")

        assertEquals(listOf("App ID", "Sender ID"), state.missingFirebaseFields())
        assertFalse(state.firebaseReady)
    }

    @Test
    fun pushReadyRequiresFirebaseBackendPermissionAndRegistration() {
        val state = SetupState(
            workerUrl = "https://gold.example.workers.dev",
            registrationCode = "device-code",
            firebaseApiKey = "key",
            firebaseAppId = "app-id",
            firebaseProjectId = "project",
            firebaseSenderId = "123456",
            notificationsAllowed = true,
            deviceRegistered = true,
        )

        assertTrue(state.backendReady)
        assertTrue(state.firebaseReady)
        assertTrue(state.pushReady)
        assertFalse(state.copy(notificationsAllowed = false).pushReady)
        assertFalse(state.copy(deviceRegistered = false).pushReady)
    }
}

