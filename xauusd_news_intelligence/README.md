# XAUUSD News Intelligence

Aplikasi Android native Kotlin untuk memantau berita yang berpotensi relevan terhadap emas/XAUUSD, menjelaskan jalur dampak naik/turun dalam bahasa Indonesia, dan menghitung rentang skenario harga historis empat jam bila data cukup. Backend menggunakan Cloudflare Workers dan Firebase Cloud Messaging (FCM).

## Fitur yang sudah ditulis dalam source

- Feed berita dari GDELT, Federal Reserve, dan U.S. Bureau of Labor Statistics.
- Kalender rilis ekonomi BLS resmi untuk 14 hari mendatang (waktu jadwal dikonversi dari zona waktu sumber ke waktu lokal perangkat).
- Pemeriksaan terjadwal setiap 10 menit melalui Cloudflare Cron Trigger.
- Klasifikasi heuristik `NORMAL`, `MEDIUM`, `HIGH`; push hanya untuk `MEDIUM`/`HIGH`.
- Notifikasi menyertakan alasan dampak, skenario bullish/bearish, serta batas bawah/atas historis empat jam jika data valid.
- Harga spot emas global indikatif dari XAUS, bukan harga broker Exness.
- Validasi freshness; data harga stale/tidak cukup tidak dipakai untuk membuat rentang.
- Dedup berita dan registrasi perangkat dengan kode registrasi.
- Cache feed di perangkat, tampilan detail dan tautan sumber.
- Tampilan Jetpack Compose dengan setup bertahap, tes Worker, status kesiapan push, dan panduan akun baru.
- Beranda berbahasa Indonesia dengan kategori dampak sederhana serta penjelasan rentang historis empat jam.
- Tidak ada auto-trading, AI/API berbayar, atau VPS berbayar.

## Struktur

- `android/` — source aplikasi Android Kotlin.
- `worker/` — Cloudflare Worker, klasifikasi, sumber berita/harga, FCM, dan tes.
- `SETUP_FIREBASE_CLOUDFLARE.md` — konfigurasi akun gratis, rahasia, deploy, dan pembuatan APK.
- `.github/workflows/build-apk.yml` — workflow untuk menguji backend, membangun APK debug, menjalankan unit test, dan mengunggah APK.

## Status build saat ini

Source dan tes backend sudah dibuat. Di lingkungan kerja ini tidak tersedia Android SDK/Gradle, dan akses ke repositori paket untuk mengunduhnya tidak tersedia. Karena itu APK **belum dapat dikompilasi atau diverifikasi di sini**. Workflow GitHub Actions disediakan untuk membangun APK ketika source berada di repository GitHub; instruksi manual juga ada di dokumen setup. Jangan menganggap push aktif sampai Firebase dan Worker Anda dikonfigurasi dan pengiriman diuji pada perangkat Android.

## Tes backend

Memerlukan Node.js 22 atau lebih baru.

```bash
cd worker
npm install
npm test
npm run typecheck
```

Tes di lingkungan penyusunan menggunakan Node.js native test runner dan lulus. Typecheck menggunakan TypeScript. Untuk deploy Worker, pasang dependency dari `worker/` lalu ikuti `SETUP_FIREBASE_CLOUDFLARE.md`.

## Batasan penting

1. Klasifikasi berita adalah heuristik berbasis aturan, belum model yang dilatih atau divalidasi terhadap reaksi pasar.
2. GDELT dan feed resmi Federal Reserve/BLS memperluas cakupan berita, tetapi tidak menjamin seluruh berita dunia. Kalender mendatang saat ini mencakup rilis BLS yang relevan, bukan seluruh kalender ekonomi global atau jadwal FOMC/BEA.
3. Zona harga Q10/Q90 adalah rentang perubahan historis empat jam, bukan prediksi, target pasti, atau probabilitas terkalibrasi.
4. XAUS menyajikan spot indikatif, bukan harga eksekusi broker; freshness dan kecukupan sampel harus tetap lolos validasi.
5. Free tier punya kuota dan ketentuan yang dapat berubah. Biaya Rp0 ditargetkan, bukan dijamin selamanya.

