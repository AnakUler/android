package com.xauusd.news.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.xauusd.news.NewsFeedResponse
import com.xauusd.news.NewsRepository
import com.xauusd.news.SetupState
import com.xauusd.news.WorkerHealth

@Composable
fun XauNewsApp(
    repository: NewsRepository,
    feed: NewsFeedResponse?,
    statusMessage: String,
    loading: Boolean,
    notificationsAllowed: Boolean,
    pushRegistered: Boolean,
    onRefresh: () -> Unit,
    onTestWorker: (String, (Result<WorkerHealth>) -> Unit) -> Unit,
    onConnectFirebase: ((String) -> Unit) -> Unit,
    onUnregister: ((String) -> Unit) -> Unit,
    onRequestNotifications: () -> Unit,
    onStatusRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val setupState = SetupState(
        workerUrl = repository.setting(NewsRepository.KEY_BACKEND_URL),
        registrationCode = repository.setting(NewsRepository.KEY_REGISTRATION_CODE),
        firebaseApiKey = repository.setting(NewsRepository.KEY_FIREBASE_API_KEY),
        firebaseAppId = repository.setting(NewsRepository.KEY_FIREBASE_APP_ID),
        firebaseProjectId = repository.setting(NewsRepository.KEY_FIREBASE_PROJECT_ID),
        firebaseSenderId = repository.setting(NewsRepository.KEY_FIREBASE_SENDER_ID),
        notificationsAllowed = notificationsAllowed,
        deviceRegistered = pushRegistered,
    )
    var screen by rememberSaveable {
        mutableStateOf(if (setupState.backendReady) AppScreen.HOME.name else AppScreen.SETUP.name)
    }
    var selectedNewsIndex by rememberSaveable { mutableStateOf(-1) }
    val selectedNews = feed?.items?.getOrNull(selectedNewsIndex)
    BackHandler(enabled = screen != AppScreen.HOME.name) {
        screen = when (AppScreen.valueOf(screen)) {
            AppScreen.GUIDE -> AppScreen.SETUP.name
            else -> AppScreen.HOME.name
        }
    }

    when (AppScreen.valueOf(screen)) {
        AppScreen.HOME -> HomeScreen(
            feed = feed,
            setupState = setupState,
            statusMessage = statusMessage,
            loading = loading,
            onRefresh = onRefresh,
            onOpenSetup = { screen = AppScreen.SETUP.name },
            onOpenGuide = { screen = AppScreen.GUIDE.name },
            onOpenNews = { index -> selectedNewsIndex = index; screen = AppScreen.DETAIL.name },
            onOpenUrl = onOpenUrl,
        )
        AppScreen.SETUP -> SetupScreen(
            repository = repository,
            setupState = setupState,
            statusMessage = statusMessage,
            notificationsAllowed = notificationsAllowed,
            pushRegistered = pushRegistered,
            onTestWorker = onTestWorker,
            onConnectFirebase = onConnectFirebase,
            onUnregister = onUnregister,
            onRequestNotifications = onRequestNotifications,
            onStatusRefresh = onStatusRefresh,
            onOpenGuide = { screen = AppScreen.GUIDE.name },
            onDone = { screen = AppScreen.HOME.name },
        )
        AppScreen.GUIDE -> SetupGuideScreen(
            onBack = { screen = AppScreen.SETUP.name },
            onOpenUrl = onOpenUrl,
        )
        AppScreen.DETAIL -> if (selectedNews != null) {
            NewsDetailScreen(
                item = selectedNews,
                onBack = { screen = AppScreen.HOME.name },
                onOpenUrl = onOpenUrl,
            )
        } else {
            HomeScreen(feed, setupState, statusMessage, loading, onRefresh,
                onOpenSetup = { screen = AppScreen.SETUP.name },
                onOpenGuide = { screen = AppScreen.GUIDE.name },
                onOpenNews = { index -> selectedNewsIndex = index; screen = AppScreen.DETAIL.name },
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

private enum class AppScreen { HOME, SETUP, GUIDE, DETAIL }

