# XAUUSD News Intelligence — Android APK build

This repository contains the complete Android and Cloudflare Worker sources under `xauusd_news_intelligence/`. GitHub Actions builds the APK directly from those tracked sources.

## Build an APK

1. Open **Actions → Build XAUUSD News Intelligence APK**.
2. Choose **Run workflow** on the `main` branch.
3. Once the run succeeds, open it and download the `xauusd-news-debug-apk` artifact.

Changes under `xauusd_news_intelligence/` start the workflow automatically. It tests and typechecks the Worker, builds the Android debug APK, runs Android unit tests, and uploads the APK as an artifact.

## Important

The APK is a debug build, not a production release. Firebase and Cloudflare still need configuration before push notifications can work. Never commit private service-account keys or other secrets.
