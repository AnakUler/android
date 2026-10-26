# XAUUSD News Intelligence — Android APK build

This repository builds the APK using GitHub Actions. The source archive is intentionally uploaded as `project.zip`.

## Steps

1. Download `xauusd_news_intelligence_source.zip` from the ChatGPT conversation.
2. Rename the downloaded archive to `project.zip` (do not extract it).
3. In this repository, choose **Add file → Upload files** and upload `project.zip` at the repository root.
4. Commit the upload. The workflow should start automatically.
5. Open **Actions → Build XAUUSD News Intelligence APK**, open the latest run, and download the `xauusd-news-debug-apk` artifact if the run succeeds.

The workflow tests the Worker, builds the Android debug APK, runs Android unit tests, and uploads the APK as an artifact.

## Important

The APK is not a production release. Firebase and Cloudflare still need configuration before push notifications can work. Never commit private service-account keys or other secrets.
