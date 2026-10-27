package com.xauusd.news

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.xauusd.news.ui.XauNewsApp
import com.xauusd.news.ui.XauNewsTheme
import org.json.JSONObject
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var repository: NewsRepository
    private var feed by mutableStateOf<NewsFeedResponse?>(null)
    private var loading by mutableStateOf(false)
    private var statusMessage by mutableStateOf("")
    private var notificationsAllowed by mutableStateOf(false)
    private var pushRegistered by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
        statusMessage = if (granted) "Izin notifikasi aktif. Peringatan hanya dikirim untuk berita berdampak sedang atau tinggi." else "Izin notifikasi ditolak. Berita tetap bisa dibaca; aktifkan izin lewat Pengaturan Android jika ingin menerima push."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NewsRepository(this)
        createNotificationChannel()
        updatePermissionState()
        syncPushRegistrationState()
        feed = repository.loadCached()
        setContent {
            XauNewsTheme {
                XauNewsApp(
                    repository = repository,
                    feed = feed,
                    statusMessage = statusMessage,
                    loading = loading,
                    notificationsAllowed = notificationsAllowed,
                    pushRegistered = pushRegistered,
                    onRefresh = ::refreshFeed,
                    onTestWorker = ::testWorker,
                    onConnectFirebase = ::connectFirebase,
                    onUnregister = ::unregisterDevice,
                    onRequestNotifications = ::requestNotifications,
                    onStatusRefresh = ::syncPushRegistrationState,
                    onOpenUrl = ::openUrl,
                )
            }
        }
        if (repository.setting(NewsRepository.KEY_BACKEND_URL).isNotBlank()) refreshFeed()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) { updatePermissionState(); syncPushRegistrationState() }
    }

    private fun syncPushRegistrationState() {
        pushRegistered = repository.setting(NewsRepository.KEY_PUSH_REGISTERED) == "true"
    }

    private fun updatePermissionState() {
        notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else statusMessage = "Izin notifikasi sudah aktif."
    }

    private fun refreshFeed() {
        if (loading) return
        val baseUrl = repository.setting(NewsRepository.KEY_BACKEND_URL)
        if (baseUrl.isBlank()) {
            statusMessage = "Tambahkan URL Worker untuk memuat berita. Anda tetap bisa melihat berita tersimpan."
            return
        }
        loading = true
        statusMessage = "Mengambil berita dan status data…"
        Thread {
            val result = NewsApiClient.fetchNews(baseUrl).mapCatching { raw ->
                NewsFeedResponse.fromJson(JSONObject(raw)).also { repository.saveFeed(raw) }
            }
            runOnUiThread {
                loading = false
                result.onSuccess { updated ->
                    feed = updated
                    statusMessage = feedSummary(updated)
                }.onFailure { error ->
                    statusMessage = "Berita belum dapat diperbarui: ${error.message ?: "periksa koneksi"}. Berita tersimpan tetap ditampilkan."
                }
            }
        }.start()
    }

    private fun testWorker(url: String, callback: (Result<WorkerHealth>) -> Unit) {
        statusMessage = "Memeriksa Worker…"
        Thread {
            val result = NewsApiClient.checkHealth(url)
            runOnUiThread {
                statusMessage = result.fold({ "Worker tersambung." }, { "Tes Worker gagal: ${it.message ?: "periksa URL dan koneksi"}" })
                callback(result)
            }
        }.start()
    }

    private fun connectFirebase(callback: (String) -> Unit) {
        statusMessage = "Menghubungkan Firebase dan mendaftarkan perangkat…"
        DeviceRegistration.connectFirebase(this) { result -> runOnUiThread {
            statusMessage = result
            if (result.startsWith("Perangkat berhasil didaftarkan")) {
                repository.saveSetting(NewsRepository.KEY_PUSH_REGISTERED, "true")
                pushRegistered = true
            }
            callback(result)
        } }
    }

    private fun unregisterDevice(callback: (String) -> Unit) {
        statusMessage = "Menghapus perangkat dari daftar push…"
        DeviceRegistration.unregister(this) { result -> runOnUiThread {
            statusMessage = result
            if (result.startsWith("Perangkat berhasil dihapus")) {
                repository.saveSetting(NewsRepository.KEY_PUSH_REGISTERED, "false")
                pushRegistered = false
            }
            callback(result)
        } }
    }

    private fun openUrl(value: String) {
        if (!value.startsWith("https://", ignoreCase = true)) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
            .onFailure { statusMessage = "Tidak ada aplikasi yang dapat membuka tautan ini." }
    }

    private fun feedSummary(response: NewsFeedResponse): String {
        val spot = response.status?.optJSONObject("spot")
        val price = if (spot?.optBoolean("available", false) == true) " • XAU global \$${String.format(Locale.US, "%.2f", spot.optDouble("currentPrice"))}" else ""
        val updated = response.lastUpdatedAt?.let {
            val time = runCatching { DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale("id", "ID")).withZone(ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrDefault(it)
            " • diperbarui $time"
        }.orEmpty()
        val warningCount = response.status?.optJSONArray("warnings")?.length() ?: 0
        val warning = if (warningCount > 0) " • $warningCount catatan sumber/data" else ""
        return "${response.items.size} berita • ${response.calendarEvents.size} jadwal$price$updated$warning"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                NewsFirebaseMessagingService.CHANNEL_ID,
                "Peringatan berita emas",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Peringatan berita XAUUSD berdampak sedang dan tinggi" })
        }
    }
}

