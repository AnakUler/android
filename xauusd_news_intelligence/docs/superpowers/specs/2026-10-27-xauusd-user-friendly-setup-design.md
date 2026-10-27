# XAUUSD News Intelligence: Easier Setup and Daily Use

**Status:** Approved by user on 2026-10-27  
**Date:** 2026-10-27

## Intent

Make the installed Android app understandable to an Indonesian first-time user, help them configure Firebase and Cloudflare without mixing server secrets into the app, and explain how to read the news and four-hour scenarios.

The current app is a native Kotlin screen with a news feed and a single settings page. That page asks for six values at once, gives no step-by-step checks, and leaves the user to infer whether news or push notifications are working.

## Goals

- Add a short first-run path that explains the required Cloudflare Worker URL and registration code, then Firebase client identifiers.
- Validate the Worker URL with `/api/health` and show actionable connection/configuration results.
- Keep the FCM service-account private key and other server credentials only in Cloudflare Worker secrets.
- Give the main screen a clear setup/readiness summary, last update, refresh action, and readable news/calendar cards.
- Explain `HIGH`, `MEDIUM`, `NORMAL`, conditional up/down scenarios, and the historical four-hour range in plain Indonesian.
- Keep the settings page available for later edits and device removal; avoid showing all advanced values on the first screen.
- Add an Indonesian usage guide with setup steps, testing, common errors, and safe secret handling.
- Audit source-visible permissions, dependencies, network destinations, secret handling, and build provenance; state clearly what cannot be concluded without the installed APK binary or an on-device scan.

## Non-goals

- No changes to the Worker API contract, classification policy, market-data math, or notification thresholds.
- No auto-trading, paid APIs, ads, analytics, or new account/login system.
- No creation of Firebase or Cloudflare resources using the user's accounts from this environment.
- No claim that a source review proves an APK is malware-free.

## Constraints

- Keep the approved native Kotlin + Cloudflare Worker + Firebase Cloud Messaging architecture.
- Use Jetpack Compose for the Android UI; preserve the package ID and backend protocol.
- Preserve package ID `com.xauusd.news` and existing `/api/health`, `/api/news`, and device-registration routes.
- Never request or commit Firebase service-account JSON/private keys, Cloudflare API tokens, or Worker secrets.
- The current debug APK is not a production-signed release. Device-specific push testing requires the user's Firebase/Cloudflare configuration and an Android device.
- Configuration screens must work on small Android screens and with Indonesian-language labels.

## Approaches considered

1. **Retitle and tidy the existing screens.** Lowest implementation cost, but the six-field form and unclear setup order remain.
2. **Guided setup plus a readiness-focused home screen.** Split setup into ordered steps with checks, and show connection, push, and data freshness clearly on the home screen. The user approved this experience.
3. **Rewrite the Android UI with Jetpack Compose.** The user selected a new UI framework. Compose replaces the current programmatic View screens while keeping the app native Kotlin and preserving the backend architecture.

The user approved the guided setup and readiness-focused home, chose a new UI framework, and selected starting Firebase/Cloudflare setup from zero. Implement with Jetpack Compose; no backend protocol or trading behavior changes.

## Proposed experience

### First run and setup

1. Show a welcome screen with a concise explanation of what the app does and what is needed before alerts work.
2. Step 1: enter the HTTPS Worker URL and device registration code; provide an explicit “Test backend” result using `/api/health`.
3. Step 2: enter Firebase client values (Web API key, App ID, Project ID, Sender ID); explain that these identify the app and are not the FCM server private key.
4. Step 3: request notification permission, retrieve an FCM token, register the device, and show success/failure as separate states.
5. Allow “Later” so the user can browse the app before setup is complete.

### Home

- A top status card says whether backend, push registration, and latest data are ready.
- A prominent refresh action has a loading state and keeps cached results visible on failure.
- News impact filters remain available but use Indonesian labels and brief explanations.
- Calendar entries and article cards display source/time and readable impact labels.
- Detail view separates the “if gold rises / if gold falls” explanation from the historical four-hour range and its uncertainty.

### Configuration guide

The guide will provide two starting points (new Firebase/Cloudflare projects, or connecting existing projects), then converge on Firebase Android app registration for package `com.xauusd.news`, Cloudflare KV namespace setup, Worker deploy, creation of `DEVICE_REGISTRATION_CODE`, storing FCM credentials as Worker secrets, entering only the client-side values in the app, and testing health/news/push. It will warn against pasting private keys into chat, the app, or Git.

## Security review scope

- Review the Android manifest, Kotlin source, Worker endpoints, dependencies/configuration, and workflow.
- Compare the release-asset metadata and CI run to the source commit where possible.
- Do not call the installed APK “clean” based only on source inspection. A binary scan and Play Protect check require the actual installed/downloaded APK or the device.
- Track the registration code being stored in app preferences as a hardening concern. `allowBackup=false` is set; any encrypted-at-rest change should be evaluated without introducing deprecated or unmaintained dependencies.

## Verification

- Preserve and extend Android unit tests for setup state, backend health parsing, missing/invalid config, and scenario labels.
- Run Worker tests and typecheck, Android unit tests, and debug APK build in GitHub Actions.
- Verify the new build artifact/release asset and provide its digest and a usage/configuration guide.
- Provide a separate manual test checklist for Firebase push on an Android device because credentials and device access are user-owned.

## Open calls for the implementer

- Choose Material-style native components only if the repository's existing dependencies make that appropriate; otherwise use the existing native views and shared style resources.
- Keep server deployment in Wrangler CLI instructions unless the user asks to configure deployment through GitHub Actions.
- Use accessible labels, large tap targets, explicit empty/loading/error states, and Indonesian copy.

