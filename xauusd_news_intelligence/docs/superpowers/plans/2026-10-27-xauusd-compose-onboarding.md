# XAUUSD Compose Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unfamiliar Android screens with an Indonesian Jetpack Compose app that guides first-time setup and makes news, Worker, and push status easy to understand.

**Architecture:** Keep the existing Kotlin Activity, repository, Worker API, and Firebase Messaging integration. Enable Jetpack Compose and split UI into focused composables for the app shell, setup steps, home/feed, settings, and news details. Keep API work off the main thread and preserve cached news if refresh fails.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 8.13.2, Jetpack Compose BOM, Material 3, existing Firebase Messaging SDK, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-27-xauusd-user-friendly-setup-design.md`

## Global Constraints

- Preserve package ID `com.xauusd.news` and Android min SDK 26.
- Keep native Kotlin + Cloudflare Worker + Firebase Cloud Messaging; do not change Worker routes or classification behavior.
- Collect only Worker HTTPS URL, device registration code, and Firebase client identifiers in the Android app.
- Keep FCM service account private key and Cloudflare API tokens out of the app, chat, and source control.
- Use Indonesian labels, explicit loading/error/success states, accessible controls, and layouts that scroll on small screens.
- Make the onboarding skippable; news browsing remains available before configuration.

## Review Focus

- Empty or malformed Worker URL and incorrect registration code must show actionable errors and never send credentials over HTTP; test URL validation and failed health check.
- Firebase client configuration may be partially entered; show the missing values and do not attempt FCM registration until all are present; test setup readiness calculation.
- Worker/API failure must keep the cached feed visible and show the refresh failure; test refresh state behavior.
- Empty feed, empty calendar, and empty selected filter must each render explanatory empty states; test composable semantics where practical.
- Notification permission denial must leave news usable and explain how to enable alerts; preserve the existing Android permission request and status callback.

---

### Task 1: Enable Compose and add setup-state rules

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/xauusd/news/SetupState.kt`
- Test: `android/app/src/test/java/com/xauusd/news/SetupStateTest.kt`

**Interfaces:**
- `SetupState` exposes backend-ready, Firebase-ready, and push-ready flags from saved config and current registration status.
- `SetupState.missingFirebaseFields(config)` returns Indonesian-readable field labels in stable order.

- [ ] Write tests for complete/incomplete Worker configuration, each missing Firebase field, and push readiness.
- [ ] Run the targeted JUnit test and confirm it fails because the model is absent.
- [ ] Enable Compose build feature, compatible Compose compiler plugin, Compose BOM, Material 3, Activity Compose, and UI tooling/test dependencies.
- [ ] Implement `SetupState` and pass the targeted tests.
- [ ] Run `:app:testDebugUnitTest` and `:app:assembleDebug`.

### Task 2: Add Compose theme, app shell, and clear home screen

**Files:**
- Create: `android/app/src/main/java/com/xauusd/news/ui/XauNewsTheme.kt`
- Create: `android/app/src/main/java/com/xauusd/news/ui/XauNewsApp.kt`
- Create: `android/app/src/main/java/com/xauusd/news/ui/HomeScreen.kt`
- Create: `android/app/src/main/java/com/xauusd/news/ui/NewsDetailScreen.kt`
- Modify: `android/app/src/main/java/com/xauusd/news/MainActivity.kt`
- Test: `android/app/src/test/java/com/xauusd/news/ImpactLabelTest.kt`

**Interfaces:**
- `XauNewsApp` receives saved config, current feed/status, refresh callback, setup navigation, and detail selection callback.
- Home shows backend/push/data readiness, a refresh action, Indonesian impact filters, calendar, and news cards.
- Detail screen explains conditional rise/fall scenarios and the historical four-hour zone separately.

- [ ] Add and run unit coverage for the Indonesian impact labels used by the feed.
- [ ] Implement app shell, home/feed, theme, and detail composables; keep content scrollable and preserve cached results on refresh failure.
- [ ] Replace programmatic View rendering in `MainActivity` with Compose `setContent` and state updates.
- [ ] Run Android unit/UI tests and assemble the debug APK.

### Task 3: Build the guided setup flow and Worker connection check

**Files:**
- Modify: `android/app/src/main/java/com/xauusd/news/NewsApiClient.kt`
- Create: `android/app/src/main/java/com/xauusd/news/ui/SetupScreen.kt`
- Modify: `android/app/src/main/java/com/xauusd/news/MainActivity.kt`
- Test: `android/app/src/test/java/com/xauusd/news/NewsApiClientTest.kt`
- Test: `android/app/src/test/java/com/xauusd/news/NewsApiClientTest.kt`

**Interfaces:**
- Add `NewsApiClient.checkHealth(baseUrl): Result<WorkerHealth>` using `/api/health` over validated HTTPS.
- Setup screen steps: welcome/Worker URL and registration code, Firebase client settings, notification permission/device registration, completion.
- Provide “Nanti”/skip and “Buka panduan konfigurasi” actions.

- [ ] Add tests for URL normalization, HTTP rejection, successful health JSON, and health errors.
- [ ] Add health response tests, including an unexpected endpoint response.
- [ ] Run targeted tests and confirm the new health parser cases fail first.
- [ ] Implement health client and stepper UI; save each step and validate before advancing.
- [ ] Preserve backend secrets solely in Cloudflare Worker configuration; never expose or request them in Android UI.
- [ ] Run all Android tests and assemble debug APK.

### Task 4: Integrate Firebase setup, push readiness, and settings access

**Files:**
- Modify: `android/app/src/main/java/com/xauusd/news/MainActivity.kt`
- Modify: `android/app/src/main/java/com/xauusd/news/DeviceRegistration.kt` only if needed for Compose callbacks

**Interfaces:**
- Settings remains reachable from home and can edit Worker URL/code and Firebase client identifiers.
- Firebase connection/registration operations report loading, success, and error into Compose state.
- User can unregister the current device from push; browsing remains usable without FCM.

- [ ] Reuse the guided SetupScreen as the settings screen; provide Firebase identifier explanations, server-secret warning, connection action, and unregister action.
- [ ] Bridge existing callbacks into Compose state and clear push readiness if Firebase/Worker settings change.
- [ ] Preserve Android 13+ notification permission request and denied-permission explanation.
- [ ] Run all Android tests and assemble debug APK.

## Execution Rulings

- Ruling: Use the available existing checkout on a separate local branch instead of creating a linked worktree — the checkout contains the restored source files needed for this feature, and workspace instructions prefer reusing a suitable checkout. The unfinished files remain uncommitted until CI verification.
- Ruling: Run the Android compile/test gate on GitHub Actions — this environment has no Java, Gradle, Android SDK, or Gradle Wrapper, so local Android verification cannot run. Do not report the feature complete until the remote workflow succeeds.
- Ruling: Use pure unit tests for deterministic setup/API/label logic and require the remote Android compile for Compose screens — this repository's CI does not provision an emulator for Compose semantics tests.

### Task 5: Update Indonesian setup and usage guide; verify delivery

**Files:**
- Modify: `SETUP_FIREBASE_CLOUDFLARE.md`
- Modify: `README.md`
- Modify: `BUILD_STATUS.md`

- [ ] Add beginner steps for new Firebase and Cloudflare accounts, while retaining the existing-project path.
- [ ] Document in-app onboarding, news classifications, four-hour zones, push behavior, troubleshooting, and secret handling.
- [ ] Run Worker tests/typecheck and Android tests/build in the verified CI environment.
- [ ] Review manifest/dependencies/network behavior and scan the built APK metadata/hash; state limits of source-only malware review.
- [ ] Publish the APK artifact/release and verify the exact downloadable output before reporting completion.

