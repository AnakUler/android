# XAUUSD News Intelligence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app and free-tier serverless backend that monitor gold-relevant news every 10 minutes, explain plausible effects in Indonesian, estimate conditional four-hour spot-price scenario zones when data is adequate, and send deduplicated MEDIUM/HIGH push alerts.

**Architecture:** Android Kotlin app consumes a small Cloudflare Worker JSON API and registers an FCM token. A scheduled Worker polls validated news and global spot-history providers, classifies and deduplicates articles, calculates historical Q10/Q90 scenario zones only with fresh sufficient data, and sends FCM messages using server-only credentials.

**Tech Stack:** Kotlin, AndroidX, Gradle, JUnit/Robolectric, Cloudflare Workers TypeScript, Node.js native test runner, Workers KV, Cron Triggers, Firebase Cloud Messaging, GitHub Actions build workflow.

**Spec:** `DESIGN.md`

## Global Constraints

- No paid AI/API, paid VPS, subscription dependency, or automatic trading.
- Backend news polling target is every 10 minutes.
- Only new MEDIUM and HIGH news sends push notifications; NORMAL does not.
- All user-facing explanations are in Indonesian and conditional, not certainty claims.
- Four-hour zones use global indicative spot and timestamp-matched historical four-hour returns; they are not price targets or guarantees.
- If spot/history is stale, malformed, or insufficient, do not fabricate zones; still allow a valid news alert.
- FCM credentials and backend secrets must never be placed in the APK or source control.
- The final APK must not be described as verified until an actual build completes; push delivery must not be described as live until Firebase and Cloudflare are configured and tested.

## Review Focus

- Malformed or partially missing news fields must not crash ingestion or create duplicate alerts.
- Stale/invalid spot data and too-small return samples must suppress price zones, not valid news alerts.
- Retries, multiple feeds, and canonical URL variants must not repeatedly notify the same item.
- FCM credentials or device tokens must not leak in logs, APK resources, or repository files.
- Android notification permission denial or missing Firebase configuration must be surfaced as setup/status issues rather than a crash.

---

## File Structure

- `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`: Android build configuration.
- `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`: app configuration and permissions.
- `android/app/src/main/java/com/xauusd/news/`: activity, API client, models, repository, notification service, token registration, and UI.
- `android/app/src/main/res/`: app name, theme, icons/placeholders, and strings.
- `android/app/src/test/`: unit tests for parsing, formatting, and state behavior.
- `worker/src/index.ts`: Worker HTTP routes and scheduled entry point.
- `worker/src/sources/news.ts`: GDELT and official RSS feed adapters.
- `worker/src/sources/globalSpot.ts`: XAUS current spot and intraday data adapter.
- `worker/src/sources/economicCalendar.ts`: BLS official ICS calendar parser and fetcher.
- `worker/src/domain/`: normalized models, relevance classifier, scenario-zone math, and deduplication helpers.
- `worker/test/`: deterministic unit tests for classification, normalization, freshness, quantiles, and deduplication.
- `worker/wrangler.toml`, `worker/package.json`, `worker/tsconfig.json`: Worker configuration and tests.
- `.github/workflows/android-build.yml`: reproducible debug APK build artifact workflow.
- `.env.example`, `README.md`, `SETUP_FIREBASE_CLOUDFLARE.md`: safe configuration examples and setup instructions; no real secrets.

## Task 1: Backend domain rules and tests

**Files:** Create `worker/package.json`, `worker/tsconfig.json`, `worker/src/domain/types.ts`, `worker/src/domain/classifier.ts`, `worker/src/domain/scenario.ts`, `worker/src/domain/dedupe.ts`, and tests under `worker/test/`.

**Interfaces:**
- `classifyNews(item: NormalizedNews): Classification` returns `NORMAL | MEDIUM | HIGH`, reason, conditional bullish/bearish pathways, and tags.
- `calculateScenarioZone(prices: SpotObservation[], nowMs: number, currentPriceOverride?: number): ScenarioZoneResult` returns either a valid lower/upper range with sample metadata or a typed unavailable reason.
- `makeDeduplicationKey(item: NormalizedNews): string` uses stable source ID when available, otherwise normalized canonical URL/content fingerprint.

- [x] Write tests first for known macro/geopolitical keywords, irrelevant routine news, missing fields, stale prices, invalid timestamps, insufficient samples, quantile math, and duplicate URL variants.
- [x] Run `cd worker && npm test` and confirm the new tests fail before implementation.
- [x] Implement pure TypeScript domain functions; keep classification deterministic and explainable.
- [x] Run `cd worker && npm test`; all domain tests must pass.

## Task 2: Backend data-source adapters and scheduled ingestion

**Files:** Create `worker/src/sources/news.ts`, `worker/src/sources/globalSpot.ts`, `worker/src/sources/economicCalendar.ts`, `worker/src/index.ts`, `worker/wrangler.toml`, and integration tests under `worker/test/`.

**Interfaces:**
- `fetchNewsCandidates(env, nowMs): Promise<NewsCandidatesResult>` fetches GDELT and official RSS feeds, normalizes items, and isolates per-source failures.
- `fetchSpotHistory(nowMs): Promise<SpotHistoryResult>` validates current XAUS spot and intraday schema, timestamps, freshness, and sample coverage. `fetchEconomicCalendar(nowMs)` parses upcoming BLS ICS releases.
- Worker routes: `GET /api/health`, `GET /api/news`, `POST /api/register-device`, `POST /api/unregister-device` (plus DELETE compatibility); scheduled handler performs ingestion, KV dedupe, calendar refresh, and push dispatch.

- [x] Write tests with mocked fetch responses for valid feeds, HTTP errors, malformed responses, stale spot data, and partial source outages.
- [x] Confirm tests fail before adapters exist.
- [x] Implement bounded-time source fetches and explicit provider status; do not treat unavailable sources as empty proof of no news.
- [x] Add a 10-minute Cron Trigger, KV bindings, and protected device registration; reject oversized/invalid request bodies and do not expose a public broadcast endpoint.
- [x] Run `cd worker && npm test` and TypeScript typecheck; both must pass.

## Task 3: FCM delivery and retry-safe notification flow

**Files:** Create `worker/src/push/fcm.ts`, `worker/src/push/dispatch.ts`, and tests under `worker/test/`.

**Interfaces:**
- `sendFcmNotification(env, deviceToken, payload): Promise<DeliveryResult>` signs server requests using secret configuration and returns a safe status without logging credentials or full tokens.
- `dispatchNewAlerts(env, classifiedItems): Promise<DispatchSummary>` stores dedupe state and dispatches only MEDIUM/HIGH items once per dedupe key.

- [x] Write tests for NORMAL suppression, MEDIUM/HIGH dispatch, duplicate retries, missing secrets, expired/invalid device tokens, and FCM HTTP errors.
- [x] Core dispatch tests failed before dispatch implementation; transport-specific tests were added after the FCM code.
- [x] Implement FCM v1 authorization using server-only service-account values stored as Worker secrets; document the required secret names without supplying real values.
- [x] Run `cd worker && npm test`; all notification tests must pass.

## Task 4: Android app shell, feed, and details

**Files:** Create Android Gradle files, manifest, app theme/resources, and Kotlin files in `android/app/src/main/java/com/xauusd/news/` for `MainActivity`, `NewsApiClient`, `NewsRepository`, `NewsItem`, `NewsDetailFormatter`, and feed/detail UI. Add unit tests under `android/app/src/test/`.

**Interfaces:**
- `NewsApiClient.fetchNews(baseUrl: String): NewsFeedResponse` calls `GET /api/news` using a configurable backend base URL.
- `NewsRepository` exposes cached last-known items and fetch status.
- UI shows impact category, source, published/detected time, rationale, conditional bullish/bearish pathways, four-hour zone or explicit unavailable reason, and original source link.

- [x] Write unit tests for JSON parsing, missing optional fields, Indonesian formatting, calendar parsing, and unavailable-zone rendering (source added; cannot execute without Android build dependencies).
- [ ] Confirm tests fail before implementation (Android parser tests were added after the initial UI implementation).
- [x] Implement native Kotlin UI with loading, empty, offline, stale-data, and error states; use a clear single-screen feed and readable detail screen. (Source written; Android compile not yet verified.)
- [ ] Build debug APK with Gradle if Android SDK/Gradle are available; otherwise rely on CI build workflow in Task 6 and state the local limitation.

## Task 5: Android notification permission and device registration

**Files:** Create `android/app/src/main/java/com/xauusd/news/NewsFirebaseMessagingService.kt`, `DeviceRegistration.kt`, and notification permission/onboarding UI; modify manifest and Gradle dependencies; add tests for registration request formatting where practical.

**Interfaces:**
- `DeviceRegistration.connectFirebase(context, callback)` obtains an FCM token and registers it over HTTPS; `registerRefreshedToken(context, token)` updates the backend registry.
- App requests `POST_NOTIFICATIONS` at runtime on Android 13+ and clearly explains when notifications are disabled or Firebase config is missing.

- [ ] Add tests for registration request serialization and backend failure state.
- [x] Implement FCM token refresh handling, local notification display for received data messages, and minimal status UI.
- [x] Ensure token and secrets are not printed in logs.
- [ ] Verify Android manifest permissions and compile task; document that real push requires a configured Firebase project and `google-services.json` or equivalent valid Firebase options.

## Task 6: Reproducible build, documentation, and final verification

**Files:** Create `.github/workflows/android-build.yml`, `README.md`, `SETUP_FIREBASE_CLOUDFLARE.md`, `.gitignore`, and `.env.example`.

- [x] Add CI workflow to build the debug APK and upload it as an artifact only when repository is connected and workflow is run.
- [x] Document free-tier setup, required Firebase Android registration, Cloudflare Worker/KV/Cron configuration, Worker secrets, backend URL, and how to install the APK.
- [x] Run Worker tests/typecheck and attempt Android build where available; Worker tests/typecheck pass, Android build is blocked because Gradle/Android SDK are not installed.
- [x] Audit repository for real secrets, tokens, private keys, accidental paid-service dependencies, and claims of guaranteed forecasts; only placeholder values are present.
- [x] Produce the APK only if an actual build succeeds; actual build was blocked, so provide the complete source project and exact build steps without inventing an APK link.

## Acceptance Gate

- [x] Domain and ingestion tests pass (Worker suite: 27/27).
- [x] MEDIUM/HIGH alerts are deduplicated; NORMAL does not notify (automated tests pass).
- [x] Invalid or stale market data cannot produce a fabricated price zone (automated tests pass).
- [x] Android source implements feed, detail, errors, and notification permission handling; runtime/build verification remains blocked by missing Android SDK.
- [x] No real credentials are committed or embedded; only configuration placeholders are present.
- [x] Build and deployment limitations are reported accurately; live service is not claimed before real account configuration and end-to-end verification.
