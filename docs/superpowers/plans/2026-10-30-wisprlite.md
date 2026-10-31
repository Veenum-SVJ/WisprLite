# WisprLite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver a minimal Android dictation app with configurable providers, local models, history, insights, and dictionary.

**Architecture:** Adapt the upstream native Kotlin app. Separate configuration, dictation orchestration, local storage, and screen rendering; preserve tested accessibility behavior.

**Tech Stack:** Kotlin, Android Views, Material, SQLiteOpenHelper, OkHttp, sherpa-onnx, WorkManager.

**Spec:** ../specs/2026-10-30-wisprlite-design.md

## Global Constraints
Android API 30 minimum and arm64 initially. No automatic cloud fallback. Existing models only. No personal recordings or keys in Git. Retain upstream attribution. Warm minimal light/dark/system theme. Audio saving off by default.

## Review Focus
- Cleanup retries must not double-count successful dictation.
- Failed and interrupted recordings must not leave audio when retention is off.
- Provider paths and errors must neither leak keys nor misroute requests.
- Mixed speech cleanup must preserve language and remain recoverable via original text.
- Missing permission/model/network must produce a recoverable state, never silent upload.

## Task 1: Reproducible upstream foundation
Files: upstream app/, gradle/, build scripts, LICENSE; new NOTICE and README.md.
- [x] Retrieve pinned EdiBianco/OpenWhispr v4.0.0 source; inspect all applicable AGENTS.md instructions and workflow files before execution.
- [x] Record commit provenance, preserve license, rename display identity to WisprLite and application ID to a distinct stable identifier.
- [x] Disable inherited automatic publication and APK-install workflows; add build/test CI without secrets.
- [x] Run existing unit tests and assembleDebug using JDK 17 and Android SDK; record baseline APK size and failures.
- [x] Commit foundation after checking staged files for credentials and generated content.

## Task 2: Provider and language configuration
Files: existing TranscriberClient.kt, CleanupProviderConfig.kt, SecureKeyStorage.kt; new ProviderConfig.kt, DictationLanguage.kt and corresponding tests in app/src/test/kotlin/com/edib/openwhispr/.
Interfaces: ProviderConfig(baseUrl: String, model: String); DictationLanguage enum AUTO, ENGLISH, YORUBA, PIDGIN; transcription request consumes config and optional language.
- [x] Add failing tests for custom HTTPS API roots, trailing slashes, full endpoints, invalid URLs, missing model/key, language mapping (en, yo, omitted), and redacted errors.
- [x] Implement configurable multipart transcription while retaining Groq preset; protect separate cleanup credentials using existing key storage.
- [x] Add cleanup preservation rules and tests ensuring dictated text remains data rather than executable instructions.
- [x] Run targeted tests then full testDebugUnitTest; commit.

## Task 3: Durable text history and exact-once insights
Files: new HistoryDatabase.kt, HistoryRepository.kt, InsightCalculator.kt and tests.
Interfaces: DictationRecord(id: String, createdAt: Long, original: String, cleaned: String?, speakingMillis: Long, mode: String); saveSuccessful(record), updateCleanup(id, text), listBefore(timestamp, limit), delete(id); aggregate counters keyed by local reporting date policy.
- [x] Add tests: duplicate record ID increments once; cleanup retry increments zero times; deleting history retains aggregates; explicit reset clears aggregates; 80 words at 40 WPM with 30 speaking seconds saves 90 seconds.
- [ ] Implement transactional SQLite persistence and paginated queries; test upgrade path and Unicode Yoruba round trip with instrumented SQLite checks.
- [x] Integrate successful transcription save into existing dictation flow; errors do not create successful records.
- [x] Run unit/instrumented checks available; commit and record any device dependency.

## Task 4: Audio lifecycle and retention
Files: new RetentionPolicy.kt, RetentionWorker.kt; modify recording lifecycle and app startup.
Interfaces: retention disabled or days in {1,7,30}; expiry compares creation time against clock-injected cutoff; startup sweep and daily worker share the same policy.
- [ ] Test disabled retention deletes temporary audio on success/failure/cancel; expired files deleted, unexpired retained, unrelated files untouched; abandoned temporary file removed on next startup.
- [x] Implement app-private recordings, deterministic cleanup, and daily WorkManager scheduling; text expiry choices 7/30/90 days or forever.
- [x] Verify no key/audio/history appears in OS backup configuration or logs; run tests and commit.

## Task 5: Four-screen minimal interface
Files: MainActivity.kt; new HomeScreen.kt, InsightsScreen.kt, DictionaryScreen.kt, SettingsScreen.kt; resources and themes.
Interfaces: screens consume repositories/configuration rather than network calls; navigation destinations HOME, INSIGHTS, DICTIONARY, SETTINGS.
- [x] Implement drawer with Settings at bottom, warm minimal theme and system/light/dark choice.
- [x] Build paginated date-grouped history, original/cleaned detail, copy/delete, loading/empty/error states.
- [x] Add insights periods, dictionary editor reusing upstream parsing, and grouped settings per specification.
- [x] Expose Direct/Cleanup selection and language selection, with clear local-audio/cloud-text behavior.
- [ ] Exercise rotation, large font, dark mode, keyboard dialogs, accessibility labels, empty history, long transcripts, and Yoruba text; commit.

## Task 6: Local models and cross-app reliability
Files: existing LocalTranscriber.kt, ModelDownloader.kt, WhisperAccessibilityService.kt and tests.
- [ ] Keep known model packs and advertise actual supported languages; verify multilingual Base/Small artifacts before listing them.
- [ ] Test interrupted/corrupt downloads, insufficient storage, missing active model, repeated taps, and local mode with no network.
- [ ] Preserve focused-field insertion and clipboard fallback; verify permissions denied/revoked and background process recovery.
- [ ] Remove unused voice-command and updater UI/dependencies only after confirming no dictation coupling; run regression suite and commit.

## Task 7: Release and physical-device validation
Files: app/build.gradle.kts; CI workflow; docs/testing/device-report.md.
- [x] Enable release shrinking/resource shrinking and add only proven required keep rules; build lint/test/debug/release artifacts.
- [x] Use adb devices -l; install only on user-authorized connected test device, without uninstalling their existing app.
- [ ] Test recording/insertion in standard fields and custom composers; airplane mode; background recovery; optional retention; custom cloud provider and cleanup if credentials entered in-app.
- [ ] Run English/Yoruba/Pidgin/mixed evaluation on Pixel 6 and Redmi 12; record model, accuracy observations, latency, heat, memory, APK/storage sizes. Do not claim unrun checks passed.
- [ ] Inspect final diff and staged content for secrets; commit and push to authorized public WisprLite repository. Supply APK, repository link, measured results, and remaining limitations.
