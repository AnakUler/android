# XAUUSD News Intelligence — Android APK build

This repository contains the complete Android and Cloudflare Worker sources under `xauusd_news_intelligence/`. GitHub Actions builds the APK directly from those tracked sources.

## Unduh APK

Setiap push ke source aplikasi/backend menjalankan **Build XAUUSD News Intelligence APK**. Workflow menguji Worker, menjalankan typecheck TypeScript, membangun APK debug, dan menjalankan unit test Android.

Untuk installer langsung, buka [Releases](https://github.com/AnakUler/android/releases) dan unduh `app-debug.apk` dari prerelease terbaru. Artefak Actions berbentuk ZIP yang berisi APK dan akan kedaluwarsa sesuai masa retensinya.

## Setup dan penggunaan

Lihat [`xauusd_news_intelligence/SETUP_FIREBASE_CLOUDFLARE.md`](xauusd_news_intelligence/SETUP_FIREBASE_CLOUDFLARE.md) untuk setup Firebase/Cloudflare, onboarding aplikasi, cara membaca skenario 4 jam, dan pemecahan masalah.

## Important

The APK is not a production release. Firebase and Cloudflare still need configuration before push notifications can work. Never commit private service-account keys or other secrets.

