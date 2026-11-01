> Current scope amendment (2026-11-01): English-first daily use, with only English and Auto-detect choices. Explicit Yoruba/Pidgin profiles and further personal language trials were withdrawn by the user. Historical failures below remain recorded; they do not establish supported-language accuracy. See [daily-use handoff](/docs/testing/daily-use-handoff.md).

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
- [x] Implement transactional SQLite persistence and paginated queries; test upgrade path and Unicode Yoruba round trip with instrumented SQLite checks. Version1 creation/reopen and unsupported-upgrade rollback preserve Unicode/history/counters; forced transcript-write failure rolls back its statistics event.
- [x] Integrate successful transcription save into existing dictation flow; errors do not create successful records.
- [x] Run unit/instrumented checks available; commit and record any device dependency.

## Task 4: Audio lifecycle and retention
Files: new RetentionPolicy.kt, RetentionWorker.kt; modify recording lifecycle and app startup.
Interfaces: retention disabled or days in {1,7,30}; expiry compares creation time against clock-injected cutoff; startup sweep and daily worker share the same policy.
- [x] Test disabled retention deletes temporary audio on success/failure/cancel; expired files deleted, unexpired retained, unrelated files untouched; abandoned temporary file removed on next startup.
- [x] Implement app-private recordings, deterministic cleanup, and daily WorkManager scheduling; text expiry choices 7/30/90 days or forever.
- [x] Verify no key/audio/history appears in OS backup configuration or logs; run tests and commit.

## Task 5: Four-screen minimal interface
Files: MainActivity.kt; new HomeScreen.kt, InsightsScreen.kt, DictionaryScreen.kt, SettingsScreen.kt; resources and themes.
Interfaces: screens consume repositories/configuration rather than network calls; navigation destinations HOME, INSIGHTS, DICTIONARY, SETTINGS.
- [x] Implement drawer with Settings at bottom, warm minimal theme and system/light/dark choice.
- [x] Build paginated date-grouped history, original/cleaned detail, copy/delete, loading/empty/error states.
- [x] Add insights periods, dictionary editor reusing upstream parsing, and grouped settings per specification.
- [x] Expose Direct/Cleanup selection and language selection, with clear local-audio/cloud-text behavior.
- [x] Exercise rotation, large font, dark mode, keyboard dialogs, accessibility labels, empty history, long transcripts, and Yoruba text; commit.

## Task 6: Local models and cross-app reliability
Files: existing LocalTranscriber.kt, ModelDownloader.kt, WhisperAccessibilityService.kt and tests.
- [x] Keep known model packs and advertise actual supported languages; verify multilingual Base/Small artifacts before listing them.
- [x] Test interrupted/corrupt downloads, insufficient storage, missing active model, repeated taps, and local mode with no network.
- [x] Preserve focused-field insertion and clipboard fallback; verify permissions denied/revoked and background process recovery. Redmi recovery passed after enabling its separate Xiaomi Background autostart switch; see docs/testing/background-recovery.md for exact scope and limits.
- [x] Remove unused voice-command and updater UI/dependencies only after confirming no dictation coupling; run regression suite and commit.

## Task 7: Release and physical-device validation
Files: app/build.gradle.kts; CI workflow; docs/testing/device-report.md.
Device-scope amendment: the user disconnected/took the Pixel 6 and explicitly directed all remaining tests to the Redmi 12. Preserve the completed Pixel baseline evidence; remaining physical trials target only Redmi.
- [x] Enable release shrinking/resource shrinking and add only proven required keep rules; build lint/test/debug/release artifacts.
- [x] Use adb devices -l; install only on user-authorized connected test device, without uninstalling their existing app.
- [x] Test recording/insertion in standard fields and custom composers; airplane mode; background recovery; optional retention; custom cloud provider and cleanup if credentials entered in-app. Groq synthetic speech/cleanup passed; Google request failures and model-quality failures remain explicitly recorded separately.
- [x] Complete revised English-first evaluation (Yoruba/Pidgin accuracy acceptance withdrawn by user), retaining Pixel 6 baseline results and targeting remaining physical trials to Redmi 12 per the user's amendment; record model, accuracy observations, latency, heat, memory, APK/storage sizes. Do not claim unrun checks passed.
- [x] Inspect final diff and staged content for secrets; commit and push to authorized public WisprLite repository. Supply APK, repository link, measured results, and remaining limitations.

## Follow-up: 0.1.1 model removal and Insights
- [x] Reveal Delete for every installed model, including the selected/only model; keep confirmation and prevent deletion during dictation.
- [x] Refresh model storage and local engine after deletion; retain explicit cloud/local selection.
- [x] Add native Insights donut charts with accessible numerical details and honest estimate labels.
- [x] Build/lint/unit-test the update and verify model controls, charts and light/dark appearance on Pixel.
- [ ] Remove new test fixtures and test package; sign and install optimized 0.1.1 on both phones without clearing data.
- [ ] Scan, commit and push the follow-up, publish its APK and verify GitHub checks.
- [x] Group Settings into native collapsible sections, put Status/Setup/Service/Engine first and keep a single inline Local models disclosure. Compile, targeted UI checks, light/dark visual review and the final full 28-check Pixel regression passed. Intermittent earlier web-test failures are recorded in the handoff report.
