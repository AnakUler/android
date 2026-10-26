# Build and verification status

Date: 2026-10-26

## Backend

- TypeScript source typecheck: **PASS** (`cd worker && tsc --noEmit --pretty false`).
- Automated tests: **PASS — 27 tests, 0 failures** (`cd worker && npm test`).
- Coverage includes news classification, canonical URL dedupe, timestamp-based four-hour zones, stale/invalid market data, BLS calendar parsing, FCM authorization/error handling, push dedupe, and Worker registration routes.

## Android

- Native Kotlin source, manifest, resources, app settings, feed/detail UI, filters, calendar display, cache, Firebase runtime initialization, token registration/removal, and notification service are present.
- Kotlin parser scan: no syntax diagnostics. This is **not** an Android compile.
- Android Gradle build: **BLOCKED**. Running `gradle assembleDebug` returned `gradle: command not found` (exit 127). `sdkmanager` and Android SDK directories are also absent from this environment, and dependency downloads cannot be performed here.
- Therefore no APK was generated or verified in this session. The repository includes `.github/workflows/android-build.yml` to build a debug APK in GitHub Actions after the source is pushed to a GitHub repository, or the project can be built in Android Studio with Android SDK 36 and Gradle 8.13.

## Deployment

- Cloudflare Worker and Firebase project are not deployed/configured from this environment because they require the user's accounts and secrets.
- Push notifications are not live until the Worker is deployed, secrets are set, the Firebase project is configured in the app, and a real device delivery test succeeds.
