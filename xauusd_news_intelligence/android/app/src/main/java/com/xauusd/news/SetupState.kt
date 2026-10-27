package com.xauusd.news

data class SetupState(
    val workerUrl: String,
    val registrationCode: String,
    val firebaseApiKey: String,
    val firebaseAppId: String,
    val firebaseProjectId: String,
    val firebaseSenderId: String,
    val notificationsAllowed: Boolean = false,
    val deviceRegistered: Boolean = false,
) {
    val backendReady: Boolean
        get() = runCatching { NewsApiClient.normalizeBaseUrl(workerUrl) }.isSuccess && registrationCode.isNotBlank()

    val firebaseReady: Boolean
        get() = missingFirebaseFields().isEmpty()

    val pushReady: Boolean
        get() = backendReady && firebaseReady && notificationsAllowed && deviceRegistered

    fun missingFirebaseFields(): List<String> = buildList {
        if (firebaseApiKey.isBlank()) add("Web API Key")
        if (firebaseAppId.isBlank()) add("App ID")
        if (firebaseProjectId.isBlank()) add("Project ID")
        if (firebaseSenderId.isBlank()) add("Sender ID")
    }
}

