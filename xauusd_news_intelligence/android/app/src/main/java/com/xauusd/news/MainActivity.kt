package com.xauusd.news

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private lateinit var repository: NewsRepository
    private lateinit var root: LinearLayout
    private var feed: NewsFeedResponse? = null
    private var loading = false
    private var selectedFilter = "ALL"
    private var statusLine = "Belum tersambung ke backend."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NewsRepository(this)
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        feed = repository.loadCached()
        showFeed()
        if (repository.setting(NewsRepository.KEY_BACKEND_URL).isNotBlank()) refreshFeed()
    }

    private fun showFeed() {
        root = baseLayout()
        root.addView(text("XAUUSD NEWS INTELLIGENCE", 22f, Color.WHITE, true).apply { setPadding(0, 0, 0, 8) })
        root.addView(text("Berita emas global • Analisis bahasa Indonesia • Skenario 4 jam", 13f, 0xFFCBD5E1.toInt()))
        val actions = horizontal()
        actions.addView(button(if (loading) "Memuat…" else "Perbarui berita") { refreshFeed() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(button("Pengaturan") { showSettings() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)
        root.addView(text(statusLine, 13f, 0xFFB7C4D3.toInt()).apply { setPadding(0, 8, 0, 4) })
        root.addView(text("Push hanya untuk MEDIUM/HIGH. Analisis bersifat kondisional, bukan sinyal trading pasti.", 12f, 0xFFB7C4D3.toInt()).apply { setPadding(0, 0, 0, 8) })
        val filters = horizontal()
        listOf("ALL" to "Semua", "HIGH" to "High", "MEDIUM" to "Medium", "NORMAL" to "Normal").forEach { (key, label) ->
            filters.addView(button(label) { selectedFilter = key; showFeed() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(filters)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val events = feed?.calendarEvents.orEmpty()
        list.addView(text("KALENDER EKONOMI AS — 14 HARI", 16f, 0xFFFFD88A.toInt(), true).apply { setPadding(0, dp(8), 0, dp(8)) })
        if (events.isEmpty()) {
            list.addView(panel("Jadwal rilis resmi belum tersedia", "Kalender BLS akan tampil setelah backend berhasil mengambil jadwal ekonomi."))
        } else {
            events.take(8).forEach { event ->
                val color = if (event.impact == "HIGH") 0xFFFFC2C2.toInt() else 0xFFFFD88A.toInt()
                val card = panel("[${event.impact}] ${event.title}", "Waktu lokal perangkat: ${formatLocalTime(event.scheduledAt)}\nSumber: ${event.source}", color)
                if (event.url.startsWith("https://") || event.url.startsWith("http://")) card.setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url))) } }
                list.addView(card)
            }
        }
        list.addView(text("BERITA TERKINI", 16f, 0xFFFFD88A.toInt(), true).apply { setPadding(0, dp(12), 0, dp(8)) })
        val allItems = feed?.items.orEmpty()
        val items = if (selectedFilter == "ALL") allItems else allItems.filter { it.level == selectedFilter }
        if (allItems.isEmpty()) {
            list.addView(panel("Belum ada berita tersimpan", "Atur URL backend di Pengaturan. Setelah Worker berjalan, berita akan diperiksa setiap 10 menit."))
        } else if (items.isEmpty()) {
            list.addView(panel("Tidak ada berita untuk filter ini", "Pilih Semua atau kategori dampak lain untuk melihat berita."))
        } else {
            items.forEach { item ->
                val color = when (item.level) { "HIGH" -> 0xFFFFC2C2.toInt(); "MEDIUM" -> 0xFFFFD88A.toInt(); else -> 0xFFB7C4D3.toInt() }
                val card = panel("[${item.level}] ${item.title}", "${item.source} • ${formatTime(item.publishedAt)}\n\n${item.reason}\n\nKetuk untuk analisis naik/turun dan zona harga.", color)
                card.setOnClickListener { showDetail(item) }
                list.addView(card)
            }
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showDetail(item: NewsItem) {
        root = baseLayout()
        root.addView(button("← Kembali ke daftar") { showFeed() })
        root.addView(text(item.title, 21f, Color.WHITE, true).apply { setPadding(0, 12, 0, 12) })
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(panel("Analisis berita", NewsDetailFormatter.format(item)))
        if (item.summary.isNotBlank()) content.addView(panel("Ringkasan sumber", item.summary))
        content.addView(button("Buka sumber asli") {
            if (item.url.startsWith("https://") || item.url.startsWith("http://")) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showSettings() {
        root = baseLayout()
        root.addView(button("← Kembali ke daftar") { showFeed() })
        root.addView(text("Pengaturan koneksi", 21f, Color.WHITE, true))
        root.addView(text("Backend berjalan di Cloudflare Worker. Firebase harus dibuat pada akun Anda; aplikasi tidak membutuhkan langganan AI berbayar.", 14f, 0xFFCBD5E1.toInt()))
        root.addView(text(statusLine, 13f, 0xFFFFD88A.toInt()).apply { setPadding(0, dp(8), 0, dp(8)) })
        val fields = linkedMapOf(
            NewsRepository.KEY_BACKEND_URL to "URL backend HTTPS (https://nama.workers.dev)",
            NewsRepository.KEY_REGISTRATION_CODE to "Kode registrasi perangkat dari pengaturan backend",
            NewsRepository.KEY_FIREBASE_API_KEY to "Firebase Web API Key",
            NewsRepository.KEY_FIREBASE_APP_ID to "Firebase App ID",
            NewsRepository.KEY_FIREBASE_PROJECT_ID to "Firebase Project ID",
            NewsRepository.KEY_FIREBASE_SENDER_ID to "Firebase Sender ID"
        )
        val editors = mutableMapOf<String, EditText>()
        fields.forEach { (key, hint) ->
            root.addView(text(hint, 12f, 0xFFCBD5E1.toInt()).apply { setPadding(0, dp(8), 0, 0) })
            val editor = EditText(this).apply {
                this.hint = hint
                setText(repository.setting(key))
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9AA8B8.toInt())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setSingleLine(true)
                if (key == NewsRepository.KEY_REGISTRATION_CODE) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            editors[key] = editor
            root.addView(editor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        root.addView(button("Simpan pengaturan") {
            editors.forEach { (key, editor) -> repository.saveSetting(key, editor.text.toString()) }
            hideKeyboard()
            statusLine = "Pengaturan tersimpan. Tekan Hubungkan Firebase untuk mengaktifkan push."
            showSettings()
        })
        root.addView(button("Hubungkan Firebase dan daftarkan perangkat") {
            editors.forEach { (key, editor) -> repository.saveSetting(key, editor.text.toString()) }
            val backend = repository.setting(NewsRepository.KEY_BACKEND_URL)
            if (!backend.startsWith("https://", true)) {
                statusLine = "URL backend harus HTTPS."
                showSettings()
            } else {
                root.addView(ProgressBar(this).apply { isIndeterminate = true })
                DeviceRegistration.connectFirebase(this) { result -> runOnUiThread {
                    statusLine = result
                    showSettings()
                } }
            }
        })
        root.addView(button("Hapus perangkat ini dari daftar push") {
            DeviceRegistration.unregister(this) { result -> runOnUiThread {
                statusLine = result
                showSettings()
            } }
        })
        root.addView(text("Kredensial server FCM tidak dimasukkan ke aplikasi. Simpan FCM private key hanya sebagai Cloudflare Worker secret.", 13f, 0xFFFFD88A.toInt()).apply { setPadding(0, dp(12), 0, 0) })
        val scroll = ScrollView(this)
        // Reparent the settings controls into a scrollable container for smaller screens.
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(20)); setBackgroundColor(0xFF18202B.toInt()) }
        while (root.childCount > 0) {
            val child = root.getChildAt(0)
            root.removeViewAt(0)
            body.addView(child)
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun refreshFeed() {
        if (loading) return
        val baseUrl = repository.setting(NewsRepository.KEY_BACKEND_URL).trim().trimEnd('/')
        if (!baseUrl.startsWith("https://", true)) {
            statusLine = "Backend belum diatur. Buka Pengaturan dan isi URL Worker HTTPS."
            showFeed()
            return
        }
        loading = true
        statusLine = "Mengambil berita dan status data…"
        showFeed()
        Thread {
            try {
                val connection = java.net.URL("$baseUrl/api/news").openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException("Server merespons HTTP $code")
                val parsed = NewsFeedResponse.fromJson(JSONObject(raw))
                repository.saveFeed(raw)
                runOnUiThread {
                    feed = parsed
                    loading = false
                    statusLine = statusSummary(parsed)
                    showFeed()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    loading = false
                    statusLine = "Gagal memperbarui berita: ${error.message ?: "periksa koneksi"}. Menampilkan cache jika tersedia."
                    showFeed()
                }
            }
        }.start()
    }

    private fun statusSummary(response: NewsFeedResponse): String {
        val spot = response.status?.optJSONObject("spot")
        val price = if (spot?.optBoolean("available", false) == true) " • XAU global: \$${String.format(Locale.US, "%.2f", spot.optDouble("currentPrice"))}" else " • Harga global belum tersedia"
        val priceAsOf = spot?.optString("asOf")?.takeIf { it.isNotBlank() && it != "null" }?.let { " (harga as of ${formatTime(it)})" }.orEmpty()
        val time = response.lastUpdatedAt?.let { " • Update ${formatTime(it)}$priceAsOf" } ?: priceAsOf
        val calendarCount = response.calendarEvents.size
        val warnings = response.status?.optJSONArray("warnings")?.let { array -> if (array.length() > 0) " • Ada peringatan sumber/data" else "" }.orEmpty()
        return "${response.items.size} berita • $calendarCount jadwal ekonomi$price$time$warnings"
    }

    private fun baseLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(16))
        setBackgroundColor(0xFF18202B.toInt())
    }

    private fun horizontal(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(4), 0, dp(4))
    }
    private fun panel(title: String, body: String, accent: Int = 0xFFCBD5E1.toInt()): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(0xFF243140.toInt())
        val heading = text(title, 16f, accent, true)
        val description = text(body, 14f, 0xFFE5EAF0.toInt())
        addView(heading)
        addView(description)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(10)
        layoutParams = params
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun hideKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(root.windowToken, 0) }

    private fun formatLocalTime(value: String): String = runCatching {
        val date = Date.from(Instant.parse(value))
        SimpleDateFormat("EEE, dd MMM • HH:mm", Locale("id", "ID")).format(date) + " (waktu perangkat)"
    }.getOrElse { value }

    private fun formatTime(value: String): String = runCatching {
        val date = Date.from(Instant.parse(value))
        SimpleDateFormat("dd MMM yyyy, HH:mm 'UTC'", Locale("id", "ID")).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
    }.getOrElse { value }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(NewsFirebaseMessagingService.CHANNEL_ID, "Peringatan berita emas", NotificationManager.IMPORTANCE_HIGH).apply { description = "Peringatan berita XAUUSD MEDIUM dan HIGH" })
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            statusLine = "Izin notifikasi diperlukan untuk menerima push MEDIUM/HIGH."
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    @Deprecated("Uses the platform permission callback for broad Android compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            statusLine = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                "Izin notifikasi aktif. Push hanya dikirim untuk berita MEDIUM/HIGH."
            } else {
                "Izin notifikasi ditolak; push tidak akan tampil sampai izin diaktifkan di pengaturan Android."
            }
            showFeed()
        }
    }

    companion object { private const val REQUEST_NOTIFICATIONS = 6012 }
}
