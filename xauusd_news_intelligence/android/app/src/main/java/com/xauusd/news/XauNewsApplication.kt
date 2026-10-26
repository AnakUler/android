package com.xauusd.news

import android.app.Application

class XauNewsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase settings are entered once in the app. Re-create the default FirebaseApp
        // when the Android process starts so FCM can receive messages before MainActivity opens.
        runCatching { DeviceRegistration.refreshRegistration(this) }
    }
}
