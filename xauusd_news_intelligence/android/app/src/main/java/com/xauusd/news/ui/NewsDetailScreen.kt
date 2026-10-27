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
import com.xauusd.news.NewsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(item: NewsItem, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Rincian berita") }, navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } }) }) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(impactLabel(item.level), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${item.source} • ${item.publishedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DetailCard("Mengapa berita ini diperhatikan", item.reason)
            DetailCard("Jika harga emas naik", item.bullish)
            DetailCard("Jika harga emas turun", item.bearish)
            DetailCard("Rentang historis 4 jam", buildString {
                if (item.zoneAvailable && item.zoneLower != null && item.zoneUpper != null) {
                    append("Kisaran indikatif: \$${"%.2f".format(java.util.Locale.US, item.zoneLower)} – \$${"%.2f".format(java.util.Locale.US, item.zoneUpper)}\n")
                    item.currentPrice?.let { append("Harga acuan global: \$${"%.2f".format(java.util.Locale.US, it)}\n") }
                    append("Berdasarkan ${item.sampleCount} sampel historis. Ini rentang statistik, bukan target atau jaminan harga.")
                } else append(item.zoneMessage)
            })
            if (item.summary.isNotBlank()) DetailCard("Ringkasan sumber", item.summary)
            Button(onClick = { if (item.url.startsWith("https://")) onOpenUrl(item.url) }, enabled = item.url.startsWith("https://"), modifier = Modifier.fillMaxWidth()) {
                Text("Buka sumber asli")
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body.ifBlank { "Belum tersedia." }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

