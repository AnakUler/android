package com.xauusd.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xauusd.news.CalendarEvent
import com.xauusd.news.NewsFeedResponse
import com.xauusd.news.NewsItem
import com.xauusd.news.SetupState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    feed: NewsFeedResponse?,
    setupState: SetupState,
    statusMessage: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenNews: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    val allNews = feed?.items.orEmpty()
    val filtered = if (selectedFilter == "ALL") allNews else allNews.filter { it.level.equals(selectedFilter, true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Emas & Berita"); Text("XAUUSD • ringkasan bahasa Indonesia", style = MaterialTheme.typography.labelMedium) } },
                actions = { TextButton(onClick = onOpenSetup) { Text("Pengaturan") } },
            )
        },
    ) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ReadinessCard(setupState, statusMessage, loading, onRefresh, onOpenSetup, onOpenGuide)
            SectionHeading("Kalender ekonomi")
            val events = feed?.calendarEvents.orEmpty()
            if (events.isEmpty()) EmptyCard("Jadwal belum tersedia", "Jadwal akan muncul setelah Worker berhasil mengambil data kalender.")
            else events.take(5).forEach { event -> CalendarCard(event, onOpenUrl) }

            SectionHeading("Berita terbaru")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Semua", "HIGH" to "Tinggi", "MEDIUM" to "Sedang", "NORMAL" to "Rutin").forEach { (key, label) ->
                    FilterChip(selected = selectedFilter == key, onClick = { selectedFilter = key }, label = { Text(label) })
                }
            }
            when {
                allNews.isEmpty() -> EmptyCard("Belum ada berita", "Pastikan Worker sudah aktif, lalu tarik untuk memperbarui berita.")
                filtered.isEmpty() -> EmptyCard("Tidak ada berita di kategori ini", "Pilih kategori lain untuk melihat berita.")
                else -> filtered.forEach { item -> NewsCard(item) { onOpenNews(allNews.indexOf(item)) } }
            }
            Text("Analisis bersifat kondisional dan informatif, bukan kepastian arah atau perintah transaksi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReadinessCard(
    state: SetupState,
    statusMessage: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status aplikasi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            StatusLine("Cloudflare Worker", state.backendReady, if (state.backendReady) "URL dan kode registrasi tersimpan" else "Belum disiapkan")
            StatusLine("Firebase", state.firebaseReady, if (state.firebaseReady) "Konfigurasi tersimpan" else "Perlu konfigurasi")
            StatusLine("Peringatan push", state.pushReady, when {
                state.pushReady -> "Perangkat siap menerima notifikasi"
                !state.notificationsAllowed -> "Izin notifikasi belum aktif"
                else -> "Perangkat belum terdaftar"
            })
            if (statusMessage.isNotBlank()) Text(statusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Memuat…" else "Perbarui berita") }
                TextButton(onClick = onOpenSetup) { Text(if (state.backendReady) "Ubah pengaturan" else "Mulai penyiapan") }
            }
            if (!state.backendReady || !state.firebaseReady) TextButton(onClick = onOpenGuide) { Text("Belum punya akun? Lihat panduan mulai dari nol") }
        }
    }
}

@Composable
private fun StatusLine(title: String, ready: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (ready) "Siap" else "Perlu tindakan", color = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarCard(event: CalendarEvent, onOpenUrl: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().then(if (event.url.startsWith("https://")) Modifier.clickable { onOpenUrl(event.url) } else Modifier)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${impactLabel(event.impact)} • ${event.title}", style = MaterialTheme.typography.titleSmall)
            Text("${localTime(event.scheduledAt)} • ${event.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(impactLabel(item.level), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${item.source} • ${utcTime(item.publishedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.reason.isNotBlank()) Text(item.reason, style = MaterialTheme.typography.bodyMedium)
            Text("Ketuk untuk membaca kemungkinan dampak dan zona 4 jam", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

fun impactLabel(level: String): String = when (level.uppercase(Locale.ROOT)) {
    "HIGH" -> "Dampak tinggi"
    "MEDIUM" -> "Dampak sedang"
    else -> "Rutin / dampak rendah"
}

private fun localTime(value: String): String = formatTime(value, ZoneId.systemDefault(), "EEE, dd MMM • HH:mm")
private fun utcTime(value: String): String = formatTime(value, ZoneId.of("UTC"), "dd MMM yyyy, HH:mm 'UTC'")
private fun formatTime(value: String, zone: ZoneId, pattern: String): String = runCatching {
    DateTimeFormatter.ofPattern(pattern, Locale("id", "ID")).withZone(zone).format(Instant.parse(value))
}.getOrElse { value }

