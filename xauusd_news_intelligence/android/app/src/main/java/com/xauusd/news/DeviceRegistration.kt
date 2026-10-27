package com.xauusd.news

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.Executors

object DeviceRegistration {
    private val executor = Executors.newSingleThreadExecutor()

    fun initializeFirebase(context: Context): FirebaseApp? {
        val repository = NewsRepository(context)
        val apiKey = repository.setting(NewsRepository.KEY_FIREBASE_API_KEY)
        val appId = repository.setting(NewsRepository.KEY_FIREBASE_APP_ID)
        val projectId = repository.setting(NewsRepository.KEY_FIREBASE_PROJECT_ID)
        val senderId = repository.setting(NewsRepository.KEY_FIREBASE_SENDER_ID)
        if (listOf(apiKey, appId, projectId, senderId).any { it.isBlank() }) return null
        val existing = FirebaseApp.getApps(context).firstOrNull()
        if (existing != null && existing.options.apiKey == apiKey && existing.options.applicationId == appId && existing.options.projectId == projectId && existing.options.gcmSenderId == senderId) return existing
        existing?.delete()
        return FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApiKey(apiKey).setApplicationId(appId).setProjectId(projectId).setGcmSenderId(senderId).build())
    }

    fun connectFirebase(context: Context, callback: (String) -> Unit) {
        val repository = NewsRepository(context)
        val app = try { initializeFirebase(context) } catch (_: Exception) { null }
        if (app == null) {
            callback("Isi API key, App ID, Project ID, dan Sender ID Firebase yang valid terlebih dahulu.")
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                executor.execute {
                    val baseUrl = repository.setting(NewsRepository.KEY_BACKEND_URL)
                    val code = repository.setting(NewsRepository.KEY_REGISTRATION_CODE)
                    val result = if (baseUrl.isBlank() || code.isBlank()) Result.failure(IllegalStateException("Isi URL backend dan kode registrasi.")) else NewsApiClient.registerDevice(baseUrl, code, token)
                    callback(result.getOrElse { "Firebase aktif, tetapi registrasi backend gagal: ${it.message ?: "kesalahan tidak diketahui"}" })
                }
            }
            .addOnFailureListener { callback("Gagal memperoleh token Firebase. Periksa konfigurasi dan koneksi.") }
    }

    fun refreshRegistration(context: Context) {
        val app = try { initializeFirebase(context) } catch (_: Exception) { null } ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> registerRefreshedToken(context, token) }
    }

    fun unregister(context: Context, callback: (String) -> Unit) {
        val repository = NewsRepository(context)
        val app = try { initializeFirebase(context) } catch (_: Exception) { null }
        if (app == null) { callback("Firebase belum dikonfigurasi; tidak dapat mengambil token perangkat."); return }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            executor.execute {
                val baseUrl = repository.setting(NewsRepository.KEY_BACKEND_URL)
                val code = repository.setting(NewsRepository.KEY_REGISTRATION_CODE)
                val result = if (baseUrl.isBlank() || code.isBlank()) Result.failure(IllegalStateException("URL backend dan kode registrasi wajib diisi.")) else NewsApiClient.unregisterDevice(baseUrl, code, token)
                callback(result.getOrElse { "Gagal menghapus perangkat: ${it.message ?: "kesalahan tidak diketahui"}" })
            }
        }.addOnFailureListener { callback("Gagal mengambil token Firebase untuk menghapus perangkat.") }
    }

    fun registerRefreshedToken(context: Context, token: String) {
        val repository = NewsRepository(context)
        val baseUrl = repository.setting(NewsRepository.KEY_BACKEND_URL)
        val code = repository.setting(NewsRepository.KEY_REGISTRATION_CODE)
        if (baseUrl.isBlank() || code.isBlank()) return
        executor.execute { NewsApiClient.registerDevice(baseUrl, code, token) }
    }
}

