# Setup Firebase + Cloudflare (target Rp0)

Panduan ini menyiapkan backend dan push notification. Pembuatan proyek memerlukan akun Firebase/Google dan Cloudflare milik Anda; saya tidak dapat membuat atau mengakses akun tersebut dari lingkungan ini.

## A. Buat Firebase project gratis

1. Buka [Firebase Console](https://console.firebase.google.com/) dan buat project.
2. Tambahkan Android app dengan package name persis `com.xauusd.news`.
3. Buka Project settings → General → Your apps dan catat **Web API Key**, **App ID**, **Project ID**, serta **Sender ID / Project number**.
4. Pada Google Cloud Console untuk project yang sama, aktifkan Firebase Cloud Messaging API dan buat service account yang diberi izin untuk mengirim FCM (role yang sesuai: Firebase Cloud Messaging API Admin). Buat private key JSON service account. **Jangan** taruh JSON service account di folder Android, source control, atau APK.
5. Pastikan ponsel Android mempunyai Google Play services. Android 13+ perlu mengizinkan notifikasi saat aplikasi memintanya.

## B. Siapkan Cloudflare Worker

Prasyarat: Node.js 22+, akun Cloudflare, dan terminal. Perintah dijalankan dari folder `worker/`.

```bash
cd worker
npm install
npx wrangler login
npx wrangler kv namespace create DATA
```

Perintah terakhir menampilkan ID KV namespace. Ganti `REPLACE_WITH_KV_NAMESPACE_ID` pada `worker/wrangler.toml` dengan ID tersebut. Jangan memakai placeholder itu saat deploy.

Deploy pertama:

```bash
npx wrangler deploy
```

Simpan URL Worker yang diberikan, biasanya `https://xauusd-news-intelligence.<subdomain>.workers.dev`.

Buat kode registrasi acak yang akan Anda masukkan di aplikasi, lalu set sebagai Worker secret. Contoh menghasilkan kode di Linux/macOS:

```bash
openssl rand -hex 24
npx wrangler secret put DEVICE_REGISTRATION_CODE
npx wrangler secret put FCM_PROJECT_ID
npx wrangler secret put FCM_CLIENT_EMAIL
npx wrangler secret put FCM_PRIVATE_KEY
```

Untuk setiap perintah `secret put`, tempel nilai dari akun Anda ketika diminta. `FCM_PRIVATE_KEY` adalah isi private key PEM dari JSON service account, termasuk baris `BEGIN PRIVATE KEY`/`END PRIVATE KEY`. Tidak perlu memasukkan secret ke `.env`, Git, Kotlin, atau APK.

Deploy ulang setelah menambahkan secret:

```bash
npx wrangler deploy
```

Worker mengambil kalender rilis BLS resmi dari `https://www.bls.gov/schedule/news_release/bls.ics` untuk 14 hari ke depan dan memeriksa berita/harga melalui Cron Trigger `*/10 * * * *`. Kalender BLS bukan kalender ekonomi global lengkap. Periksa `https://URL-WORKER/api/health` untuk status konfigurasi. `GET /api/news` menampilkan cache feed; cache akan terisi setelah scheduled run pertama.

### Kuota dan biaya

- Cloudflare Workers Free mencantumkan kuota request harian, batas CPU per Cron, dan kuota Workers KV. Implementasi menggabungkan daftar perangkat dan daftar dedupe agar mengurangi operasi KV.
- FCM dipakai untuk mengirim push; tidak ada AI berbayar. Sumber data pihak ketiga dapat mengubah syarat/kuota.
- Jangan meng-upgrade ke layanan berbayar jika ingin menjaga target Rp0. Jika kuota gratis terlampaui, sistem bisa berhenti memproses sampai kuota reset.

## C. Atur aplikasi Android

Setelah APK terpasang:

1. Buka **Pengaturan**.
2. Isi URL Worker HTTPS.
3. Isi kode `DEVICE_REGISTRATION_CODE` yang sama dengan secret Worker.
4. Isi Web API Key, App ID, Project ID, dan Sender ID dari Firebase.
5. Tekan **Simpan pengaturan**, lalu **Hubungkan Firebase dan daftarkan perangkat**.
6. Kembali ke feed dan tekan **Perbarui berita**. Tunggu scheduled run pertama.
7. Uji push menggunakan berita MEDIUM/HIGH baru atau FCM test send dari Firebase Console. Berita NORMAL memang tidak mengirim push.

Jika konfigurasi Firebase diubah setelah Firebase SDK diinisialisasi, tutup paksa lalu buka kembali aplikasi sebelum mencoba koneksi lagi. Token FCM disimpan di backend untuk pengiriman; kode registrasi hanya dipakai saat registrasi perangkat.

## D. Build APK debug

### Melalui Android Studio

1. Buka Android Studio dan pilih **Open** pada folder `android/`.
2. Gunakan JDK 17 atau yang kompatibel dengan Android Gradle Plugin 8.13.x.
3. Izinkan Gradle mengunduh dependency, lalu jalankan task `app → Tasks → build → assembleDebug`.
4. APK output: `android/app/build/outputs/apk/debug/app-debug.apk`.

### Melalui terminal dengan Gradle 8.13

Pasang Android SDK dan platform `android-36`, terima lisensi, dan atur `ANDROID_HOME`/`local.properties`. Lalu:

```bash
cd android
gradle assembleDebug
gradle testDebugUnitTest
```

### GitHub Actions

Push source ini ke repository GitHub milik Anda, lalu buka tab Actions dan jalankan workflow **Build Android APK**. Setelah sukses, unduh artifact `xauusd-news-debug-apk`. Workflow hanya menghasilkan artifact; itu tidak men-deploy Worker dan tidak mengaktifkan Firebase otomatis.

## E. Sumber dan cara membaca hasil

- Berita: GDELT, feed press release Federal Reserve, dan feed BLS.
- Harga: XAUS global indicative spot/intraday. Bukan kuotasi eksekusi broker.
- Rentang: Q10/Q90 dari return historis yang berjarak empat jam berdasarkan timestamp aktual. Jika data stale atau sampel kurang, zona tidak ditampilkan dan alert tetap dapat dikirim.
- `HIGH`/`MEDIUM` adalah prioritas aturan heuristik, bukan probabilitas berita akan menggerakkan pasar.

## Troubleshooting

- **Tidak ada berita:** periksa `/api/health`, lalu `GET /api/news`; Cron mungkin belum berjalan atau sumber gagal.
- **Tidak ada push:** periksa izin notifikasi Android, Google Play services, konfigurasi Firebase, Worker secrets, kode registrasi, dan status registrasi perangkat.
- **Harga tidak tersedia:** Worker sengaja tidak membuat zona dari data stale/kurang.
- **Deploy gagal:** pastikan ID KV bukan placeholder, `wrangler login` aktif, dan secret sudah diset.
