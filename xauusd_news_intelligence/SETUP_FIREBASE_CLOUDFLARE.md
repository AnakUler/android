# Panduan pemula: Firebase, Cloudflare, dan aplikasi XAUUSD

Panduan ini untuk menyiapkan akun baru. Jika project Firebase atau Worker Cloudflare sudah ada, lewati bagian pembuatan akun dan lanjutkan dari bagian yang belum selesai. Saya tidak dapat masuk ke akun Anda dari sini; jangan kirim password, token Cloudflare, atau private key ke chat.

## Gambaran singkat

- **Cloudflare Worker** mengambil dan menyimpan data berita, kalender, serta harga indikatif.
- **Firebase Cloud Messaging (FCM)** mengantar notifikasi Android.
- Aplikasi hanya memerlukan URL Worker, kode registrasi perangkat, serta empat nilai identitas Firebase. Service-account private key FCM hanya disimpan sebagai Cloudflare secret.
- Berita tetap dapat dibuka tanpa setup push. Notifikasi hanya digunakan untuk berita `MEDIUM` dan `HIGH`.

## 1. Buat project Firebase

1. Buka [Firebase Console](https://console.firebase.google.com/) dan buat project baru.
2. Pada project, pilih **Add app → Android**. Isi package name persis `com.xauusd.news`, lalu daftarkan aplikasi. Unduh `google-services.json` tidak diperlukan untuk aplikasi ini karena konfigurasi Firebase dimasukkan lewat layar setup.
3. Buka **Project settings → General → Your apps**. Catat **Web API Key**, **App ID**, **Project ID**, dan **Project number** (ini yang dipakai sebagai Sender ID).
4. Aktifkan **Firebase Cloud Messaging API** pada project Google Cloud yang sama.
5. Di **Project settings → Service accounts**, buat private key service account JSON. Pastikan email service account mempunyai role **Firebase Cloud Messaging API Admin** pada project target.
6. Simpan berkas JSON itu di tempat aman di komputer Anda. Jangan masukkan ke folder `android/`, jangan unggah ke GitHub, dan jangan tempel ke aplikasi atau chat. Nanti hanya nilai `project_id`, `client_email`, dan `private_key` yang akan dimasukkan sebagai Cloudflare secrets.

> Android harus mempunyai Google Play services untuk FCM. Pada Android 13 atau lebih baru, izinkan notifikasi saat aplikasi meminta izin.

## 2. Siapkan Cloudflare Worker

Gunakan Windows PowerShell atau terminal lain. Pasang Node.js 22 atau lebih baru, unduh/clone source proyek, lalu buka terminal pada folder `xauusd_news_intelligence/worker`.

```powershell
npm install
npx wrangler login
npx wrangler kv namespace create DATA
```

Perintah KV menampilkan ID namespace. Buka `worker/wrangler.toml`, ganti nilai `REPLACE_WITH_KV_NAMESPACE_ID` pada `[[kv_namespaces]]` dengan ID tersebut, lalu simpan berkas.

Buat kode registrasi acak di PowerShell:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$registrationCode = [Convert]::ToHexString($bytes).ToLowerInvariant()
$registrationCode
```

Simpan kode itu di password manager atau catatan pribadi. Masukkan sebagai Cloudflare secret. Ulangi perintah `secret put` untuk setiap nilai; Wrangler meminta nilainya secara interaktif.

```powershell
npx wrangler secret put DEVICE_REGISTRATION_CODE
npx wrangler secret put FCM_PROJECT_ID
npx wrangler secret put FCM_CLIENT_EMAIL
npx wrangler secret put FCM_PRIVATE_KEY
npx wrangler deploy
```

Isi secret sebagai berikut:

- `DEVICE_REGISTRATION_CODE`: kode acak yang baru dibuat.
- `FCM_PROJECT_ID`: nilai Project ID Firebase.
- `FCM_CLIENT_EMAIL`: nilai `client_email` di JSON service account.
- `FCM_PRIVATE_KEY`: hanya nilai `private_key` dari JSON, bukan seluruh isi JSON. Worker menangani baris `\n` pada private key.

Secret terenkripsi tidak ditampilkan kembali di dashboard/Wrangler. Jangan menyimpannya sebagai `vars`, `.env` yang di-commit, atau konstanta source code. Cloudflare menyediakan jenis **Secret** di Worker → **Settings → Variables and Secrets**; gunakan jenis itu bila memilih dashboard.

Setelah deploy, simpan URL Worker yang diberikan, biasanya berformat `https://nama-worker.<akun>.workers.dev`. Uji dengan membuka `https://URL-WORKER/api/health`. Respons yang menunjukkan `configured.registrationCode: true` dan `configured.fcm: true` berarti secret backend terdeteksi. Nilai `false` berarti periksa nama dan isi secret di Cloudflare.

Worker menjalankan pemeriksaan sumber setiap 10 menit. `GET /api/news` dapat kosong sampai pemeriksaan terjadwal pertama menyelesaikan pengambilan data. Kalender yang tersedia saat ini berasal dari rilis BLS AS; ini bukan kalender ekonomi global lengkap.

## 3. Hubungkan aplikasi

1. Pasang APK dan buka **Penyiapan aplikasi**. Anda dapat memilih **Nanti saja** untuk melihat feed sebelum konfigurasi selesai.
2. Langkah **Cloudflare Worker**: tempel URL Worker HTTPS dan kode `DEVICE_REGISTRATION_CODE` yang sama dengan secret. Tekan **Tes koneksi Worker**.
3. Langkah **Firebase**: masukkan Web API Key, App ID, Project ID, dan Sender ID dari Project settings Firebase.
4. Langkah **Peringatan**: izinkan notifikasi, lalu tekan **Hubungkan Firebase dan daftarkan perangkat**.
5. Kembali ke beranda dan tekan **Perbarui berita**. Status Worker, konfigurasi Firebase, pendaftaran push, jumlah berita, jadwal, dan waktu pembaruan tampil di sana.

Layar **Pengaturan** dapat dibuka kapan saja untuk memperbaiki konfigurasi atau menghapus perangkat dari daftar push. Mengubah project Firebase mungkin memerlukan aplikasi ditutup paksa lalu dibuka kembali agar SDK memuat ulang konfigurasi.

## 4. Cara membaca aplikasi

- **Dampak tinggi/sedang** adalah tingkat relevansi berita menurut aturan klasifikasi; itu bukan prediksi arah.
- Buka kartu berita untuk membaca alasan, skenario jika emas naik, dan skenario jika emas turun.
- **Rentang historis 4 jam** dihitung dari pergerakan harga pada sampel lampau jika datanya cukup dan masih baru. Rentang itu bukan target harga, probabilitas terkalibrasi, atau jaminan hasil.
- Harga spot global adalah harga indikatif, bukan harga eksekusi broker Anda.
- Kalender ekonomi yang terlihat mengikuti jadwal yang disediakan Worker.
- Jika Worker atau Firebase belum siap, aplikasi tetap dapat menampilkan feed cache yang tersimpan.

## 5. Pemecahan masalah

| Gejala | Periksa |
|---|---|
| Tes Worker gagal | URL harus lengkap dan memakai `https://`; periksa Worker sudah di-deploy dan koneksi internet tersedia. |
| `registrationCode` bernilai `false` pada `/api/health` | Secret `DEVICE_REGISTRATION_CODE` belum ditambahkan atau namanya salah. |
| Worker tersambung tetapi registrasi perangkat ditolak | Kode di aplikasi harus sama persis dengan secret Cloudflare. |
| `fcm` bernilai `false` | Lengkapi tiga secret FCM di Cloudflare, lalu deploy ulang. |
| Firebase tidak menghasilkan token | Periksa empat nilai client Firebase, API FCM aktif, koneksi internet, dan Google Play services pada ponsel. |
| Tidak ada notifikasi | Periksa izin Android, status push di beranda, secret FCM, dan tunggu berita baru berkategori MEDIUM/HIGH. Berita rutin tidak memicu push. |
| Feed masih kosong | Tunggu satu siklus terjadwal Worker, lalu tekan Perbarui berita. Periksa `/api/health` dan dashboard Cloudflare untuk error Worker. |

## Keamanan dan biaya

APK yang tersedia saat ini adalah **debug build**, bukan APK production yang ditandatangani dengan kunci rilis Anda. Aplikasi tidak perlu menerima private key Firebase server atau token API Cloudflare. Jangan membagikan kredensial akun. Kuota dan harga layanan gratis dapat berubah; periksa dashboard layanan bila penggunaan mendekati limit.

## Referensi resmi

- [Firebase: menyiapkan FCM di Android](https://firebase.google.com/docs/cloud-messaging/android/get-started)
- [Firebase: mengirim melalui HTTP v1 dan service account](https://firebase.google.com/docs/cloud-messaging/send/v1-api)
- [Cloudflare Workers: menyimpan secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
- [Cloudflare Wrangler: perintah secret put](https://developers.cloudflare.com/workers/wrangler/commands/workers/)

