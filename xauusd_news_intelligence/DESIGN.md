# XAUUSD News Intelligence — Design

**Status:** Approved for implementation by the user on 2026-10-26.

## 1. Purpose
Build an Android application that monitors global news relevant to gold (XAUUSD), explains plausible conditional effects in Indonesian, and sends timely push alerts for MEDIUM/HIGH items. It is decision support, not a trading signal guarantee.

## 2. Goals
- Native Android app written in Kotlin.
- Check configured news sources on a 10-minute server schedule.
- Send push notifications only for new MEDIUM and HIGH news.
- Explain possible bullish and bearish pathways, with source/time and uncertainty.
- For MEDIUM/HIGH items, calculate a four-hour upper/lower scenario zone from global gold spot and historical volatility only when input data passes freshness/coverage checks.
- Keep expected operating costs at Rp0 using free tiers, with explicit quotas and fallback behavior.
- Keep secrets (FCM credentials and backend secrets) on the server, never in the APK.

## 3. Non-goals
- No automatic trading or broker order placement.
- No promise to capture every news item worldwide or deliver every alert instantly.
- No paid AI/API, paid VPS, or subscription dependency in the initial version.
- No guarantee that price projections are targets, predictions, or probabilities of future movement.
- No dependence on Exness-specific quotes; use global indicative gold spot.

## 4. Selected approach
**Native Kotlin Android + Cloudflare Workers scheduled backend + Firebase Cloud Messaging.**

Alternatives considered:
1. **Kotlin native (selected):** best fit for Android-first app and native notification behavior; requires Firebase setup and user-side account configuration.
2. Flutter: supports multiple platforms, but iOS is not currently required and it adds setup overhead.
3. PWA: simpler deployment, but native APK and reliable push experience are the priority.

## 5. Proposed components
- **Android app:** news feed, upcoming BLS economic release calendar (14-day window), article/detail screen, impact filters, notification permission/onboarding, cached feed, source links, last-update and data-quality labels.
- **Cloudflare Worker + Cron Trigger:** fetch selected feeds/search endpoints, normalize and deduplicate items, classify relevance/impact with transparent rules, estimate scenario zones, and send FCM messages for newly seen MEDIUM/HIGH items.
- **Free storage (initially Workers KV if suitable):** seen-item IDs, compact recent-news state, and device token registry. Respect published quotas; avoid storing unnecessary personal data.
- **News sources:** GDELT plus official Federal Reserve and BLS news feeds. Upcoming scheduled releases use the official BLS ICS calendar. The BLS calendar is not a complete global economic calendar and does not currently cover FOMC/BEA schedules. Source availability, licensing, endpoint behavior, and rate limits must be verified during implementation. Source coverage will be broad but not exhaustive.
- **Global gold price:** XAUS spot and intraday history endpoint as a candidate. Validate current terms, availability, timestamps, stale-data flags, and actual response schema before relying on it.
- **Push delivery:** Firebase Cloud Messaging. On Android 13+, the app must request runtime notification permission; without permission, the OS may suppress notifications.

## 6. News impact and explanation
- NORMAL: routine or low direct relevance; no push.
- MEDIUM: plausible potential to change rate, yield, USD, risk-sentiment, or gold expectations; push.
- HIGH: potentially material macro/geopolitical event or meaningful surprise; push.

Classification begins as a transparent rule-based heuristic, not a trained or validated forecasting model. Explanations must be conditional (e.g. “if this raises expected US yields, gold may face pressure”), include counter-scenarios where reasonable, and avoid claiming certainty or unsupported probabilities. The app should show source, publication time, detection time, impact rationale, and freshness. Upcoming BLS calendar items should show their official schedule converted to device-local time.

## 7. Four-hour price scenario zones
- Let `P0` be the latest acceptable global spot price.
- Use the provider’s actual intraday timestamps to form rolling four-hour returns; do not assume a fixed sample cadence.
- Compute empirical lower/upper quantiles (initial proposal: Q10/Q90) from timestamp-matched four-hour returns, then calculate `lower = P0 * (1 + Q10)` and `upper = P0 * (1 + Q90)`.
- Label the result as a **historical-volatility scenario range**, not a directional target or a guarantee. Quantiles from a short history are uncertain; overlapping windows are not independent. The initial source’s intraday retention may limit the effective sample, so the app must expose sample size, lookback coverage, current spot basis, and uncertainty.
- The qualitative news explanation must not silently distort numeric bounds. A directional bias can be shown separately as conditional reasoning, not represented as a calibrated probability.
- If spot is stale, history is insufficient, timestamps are invalid, or endpoint fails, show “Zona harga belum dapat dihitung—data tidak cukup/kedaluwarsa” while still sending the news alert if the news itself is valid.

## 8. Schedule and notifications
- Backend polling target: every 10 minutes using a Cloudflare Cron Trigger.
- Notify only on newly detected MEDIUM/HIGH items; deduplicate by stable source ID or canonicalized URL/content fingerprint.
- Retries must not create repeated alerts. Failures and stale data must be visible in app status.
- Android background refresh is not the authoritative scheduler; server Cron is responsible for periodic news checking.

## 9. Security and privacy
- FCM server credentials and signing secrets stay in Cloudflare secrets, not source control or APK.
- Validate inbound requests to any backend endpoints; do not expose an unauthenticated broadcast endpoint.
- Store only minimum device token and delivery metadata needed. Document token deletion and rotation behavior.
- Do not require login for the initial single-user MVP unless a technical constraint proves otherwise.

## 10. Cost and operational constraints
- Target Rp0 with free tiers only. Free-tier terms/quotas can change and do not constitute a cost guarantee.
- Cloudflare Workers Free currently documents 100,000 requests/day and 10 ms CPU per Cron Trigger invocation; the implementation must remain within limits or fail gracefully.
- Firebase Cloud Messaging is used for delivery, but Firebase project setup and Android app registration still require the user to create/configure their own project.
- External news/price providers may change access, limits, or terms; provide source health checks and graceful fallback.

## 11. MVP acceptance criteria
1. App opens and displays cached/latest fetched news with source and timestamps.
2. MEDIUM/HIGH events trigger a push once; NORMAL events do not.
3. Every push has Indonesian conditional bullish/bearish explanation and an impact rationale.
4. Four-hour upper/lower zones appear only when validated price/history data is fresh and sufficient; sample count, history coverage, price source, and uncertainty are shown; otherwise the app clearly reports why unavailable.
5. Duplicate feed entries do not create duplicate notifications.
6. Missing internet, malformed feed data, stale price, FCM failure, or denied Android notification permission does not crash the app and is surfaced clearly.
7. Tests cover classification, deduplication, freshness checks, quantile/range math, and invalid/insufficient data.
8. The feed includes impact filters and upcoming BLS release-calendar items; build instructions explain required Firebase and Cloudflare setup. Do not claim a signed/installed APK until a real build and device/emulator verification has run.

## 12. Implementation boundary
The user approved this design on 2026-10-26 and implementation has begun. Source code and local tests are being built in testable stages. No cloud resources, Firebase project, or deployed Worker has been created from this environment; an APK will only be called complete after a real Android build succeeds.
