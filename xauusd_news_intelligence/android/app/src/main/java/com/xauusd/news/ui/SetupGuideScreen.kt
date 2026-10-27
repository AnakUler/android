package com.xauusd.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Panduan mulai dari nol") }, navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } }) }) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Agar berita dan peringatan bekerja, siapkan Cloudflare Worker dan Firebase. Ikuti urutan ini.", style = MaterialTheme.typography.bodyLarge)
            GuideCard("1. Siapkan Cloudflare Worker", "Masuk ke Cloudflare, buat KV namespace bernama DATA, lalu deploy folder worker dari paket sumber proyek dengan Wrangler. Worker menyediakan URL HTTPS yang nanti dimasukkan ke aplikasi.")
            GuideCard("2. Simpan rahasia push di Cloudflare", "Atur DEVICE_REGISTRATION_CODE dengan kode acak yang Anda buat. Tambahkan FCM_PROJECT_ID, FCM_CLIENT_EMAIL, dan FCM_PRIVATE_KEY menggunakan perintah wrangler secret put. Rahasia ini hanya berada di Cloudflare, jangan tempel ke aplikasi, chat, atau Git.")
            GuideCard("3. Buat aplikasi Android Firebase", "Buat Firebase project lalu tambahkan Android app dengan package name persis com.xauusd.news. Aktifkan Firebase Cloud Messaging API pada project Google Cloud yang sama. Catat Web API Key, App ID, Project ID, dan Sender ID untuk dimasukkan pada langkah setup aplikasi.")
            GuideCard("4. Hubungkan aplikasi", "Masukkan URL Worker HTTPS dan kode registrasi yang sama dengan secret Cloudflare. Tes backend, lalu isi empat identitas Firebase. Izinkan notifikasi dan tekan Hubungkan Firebase. Berita tetap dapat dibaca walau push belum disiapkan.")
            GuideCard("5. Pahami peringatan", "Dampak tinggi/sedang menunjukkan relevansi berita, bukan arah yang pasti. Skenario naik dan turun adalah kondisi yang mungkin. Rentang 4 jam memakai sampel harga historis; itu bukan target atau jaminan harga.")
            Button(onClick = { onOpenUrl("https://console.firebase.google.com/") }, modifier = Modifier.fillMaxWidth()) { Text("Buka Firebase Console") }
            Button(onClick = { onOpenUrl("https://dash.cloudflare.com/") }, modifier = Modifier.fillMaxWidth()) { Text("Buka Cloudflare Dashboard") }
            Button(onClick = { onOpenUrl("https://github.com/AnakUler/android/tree/main/xauusd_news_intelligence") }, modifier = Modifier.fillMaxWidth()) { Text("Buka kode Worker dan panduan lengkap") }
            Text("Detail perintah deploy dan pengaturan secret tersedia di panduan setup proyek. Anda tidak perlu membagikan password, token Cloudflare, atau private key kepada siapa pun.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

