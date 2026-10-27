package com.xauusd.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xauusd.news.NewsRepository
import com.xauusd.news.SetupState
import com.xauusd.news.WorkerHealth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    repository: NewsRepository,
    setupState: SetupState,
    statusMessage: String,
    notificationsAllowed: Boolean,
    pushRegistered: Boolean,
    onTestWorker: (String, (Result<WorkerHealth>) -> Unit) -> Unit,
    onConnectFirebase: ((String) -> Unit) -> Unit,
    onUnregister: ((String) -> Unit) -> Unit,
    onRequestNotifications: () -> Unit,
    onStatusRefresh: () -> Unit,
    onOpenGuide: () -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var workerUrl by rememberSaveable { mutableStateOf(repository.setting(NewsRepository.KEY_BACKEND_URL)) }
    var registrationCode by rememberSaveable { mutableStateOf(repository.setting(NewsRepository.KEY_REGISTRATION_CODE)) }
    var apiKey by rememberSaveable { mutableStateOf(repository.setting(NewsRepository.KEY_FIREBASE_API_KEY)) }
    var appId by rememberSaveable { mutableStateOf(repository.setting(NewsRepository.KEY_FIREBASE_APP_ID)) }
    var projectId by rememberSaveable { mutableStateOf(repository.setting(NewsRepository.KEY_FIREBASE_PROJECT_ID)) }
    var senderId by rememberSaveable { mutableStateOf(repository.setting(NewsRepository.KEY_FIREBASE_SENDER_ID)) }
    var workerResult by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Penyiapan aplikasi") }, navigationIcon = { TextButton(onClick = onDone) { Text("Tutup") } })
    }) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Langkah ${step + 1} dari 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(when (step) { 0 -> "Hubungkan Cloudflare Worker"; 1 -> "Tambahkan identitas Firebase"; else -> "Aktifkan peringatan" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            when (step) {
                0 -> {
                    Text("Worker mengambil berita dan kalender. Masukkan URL HTTPS dari Worker dan kode yang Anda atur sebagai DEVICE_REGISTRATION_CODE di Cloudflare.")
                    OutlinedTextField(workerUrl, { workerUrl = it }, label = { Text("URL Worker HTTPS") }, placeholder = { Text("https://nama-worker.workers.dev") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(registrationCode, { registrationCode = it }, label = { Text("Kode registrasi perangkat") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        workerResult = "Memeriksa koneksi…"
                        onTestWorker(workerUrl) { result ->
                            workerResult = result.fold(
                                { health -> if (health.registrationCodeConfigured) "Worker tersambung. Kode registrasi sudah disiapkan.${if (health.fcmConfigured) " Firebase server siap." else " Kredensial push belum disiapkan di Cloudflare."}" else "Worker merespons, tetapi DEVICE_REGISTRATION_CODE belum disimpan sebagai secret." },
                                { "Tidak dapat menghubungi Worker: ${it.message ?: "periksa URL dan koneksi internet"}" },
                            )
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Tes koneksi Worker") }
                    workerResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
                    Text("Jangan tempel token API Cloudflare ke sini. Aplikasi hanya perlu URL Worker dan kode registrasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                1 -> {
                    Text("Nilai ini berasal dari Firebase Project settings → General → Your apps. Nilai ini mengenali project Android dan bukan private key server.")
                    ConfigField("Firebase Web API Key", apiKey) { apiKey = it }
                    ConfigField("Firebase App ID", appId) { appId = it }
                    ConfigField("Firebase Project ID", projectId) { projectId = it }
                    ConfigField("Firebase Sender ID / Project number", senderId) { senderId = it }
                    Text("FCM service-account private key disimpan sebagai secret Cloudflare saja. Jangan masukkan ke aplikasi atau Git.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    StatusCard("Izin notifikasi", if (notificationsAllowed) "Notifikasi diizinkan" else "Notifikasi belum diizinkan")
                    Button(onClick = onRequestNotifications, enabled = !notificationsAllowed, modifier = Modifier.fillMaxWidth()) { Text(if (notificationsAllowed) "Izin sudah aktif" else "Izinkan notifikasi") }
                    val isRegistered = pushRegistered
                    StatusCard("Pendaftaran perangkat", if (isRegistered) "Perangkat sudah terhubung" else "Belum terhubung ke FCM")
                    Button(onClick = { onConnectFirebase { result -> workerResult = result } }, modifier = Modifier.fillMaxWidth()) { Text("Hubungkan Firebase dan daftarkan perangkat") }
                    if (isRegistered) TextButton(onClick = { onUnregister { result -> workerResult = result } }, modifier = Modifier.fillMaxWidth()) { Text("Hapus perangkat ini dari daftar push") }
                    if (statusMessage.isNotBlank()) Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                    workerResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
                    Text("Jika izin notifikasi ditolak, berita tetap bisa dibaca. Aktifkan izinnya lewat Pengaturan Android kapan saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Text("${if (setupState.backendReady) "✓" else "○"} Worker   ${if (setupState.firebaseReady) "✓" else "○"} Firebase   ${if (setupState.pushReady) "✓" else "○"} Push", Modifier.padding(14.dp), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) { Text("Buka panduan akun baru dan konfigurasi") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (step > 0) step-- else onDone() }) { Text(if (step > 0) "Kembali" else "Nanti saja") }
                Button(onClick = {
                    when (step) {
                        0 -> {
                            val changed = repository.setting(NewsRepository.KEY_BACKEND_URL) != workerUrl.trim() || repository.setting(NewsRepository.KEY_REGISTRATION_CODE) != registrationCode.trim()
                            repository.saveSetting(NewsRepository.KEY_BACKEND_URL, workerUrl)
                            repository.saveSetting(NewsRepository.KEY_REGISTRATION_CODE, registrationCode)
                            if (changed) { repository.saveSetting(NewsRepository.KEY_PUSH_REGISTERED, "false"); onStatusRefresh() }
                        }
                        1 -> {
                            val changed = repository.setting(NewsRepository.KEY_FIREBASE_API_KEY) != apiKey.trim() ||
                                repository.setting(NewsRepository.KEY_FIREBASE_APP_ID) != appId.trim() ||
                                repository.setting(NewsRepository.KEY_FIREBASE_PROJECT_ID) != projectId.trim() ||
                                repository.setting(NewsRepository.KEY_FIREBASE_SENDER_ID) != senderId.trim()
                            repository.saveSetting(NewsRepository.KEY_FIREBASE_API_KEY, apiKey)
                            repository.saveSetting(NewsRepository.KEY_FIREBASE_APP_ID, appId)
                            repository.saveSetting(NewsRepository.KEY_FIREBASE_PROJECT_ID, projectId)
                            repository.saveSetting(NewsRepository.KEY_FIREBASE_SENDER_ID, senderId)
                            if (changed) { repository.saveSetting(NewsRepository.KEY_PUSH_REGISTERED, "false"); onStatusRefresh() }
                        }
                    }
                    if (step < 2) step++ else onDone()
                }) { Text(if (step == 2) "Selesai" else "Lanjut") }
            }
        }
    }
}

@Composable
private fun ConfigField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

