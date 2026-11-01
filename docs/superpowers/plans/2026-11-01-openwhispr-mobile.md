# Proposed implementation sequence

Status: draft requested by user; design review pending. Read ../specs/2026-11-01-openwhispr-mobile-design.md and HANDOFF-OPENWHISPR-REDESIGN.md first. Do not treat the completed 0.1.1 plan as completion of this redesign.

## 1. Preserve baseline and approve visual direction
- [x] Inspect actual installed desktop app; capture private references; confirm exact logo blue.
- [x] Confirm transcript-only search, spoken snippets and per-app tone/manual override.
- [ ] Review full design and static logo/screen proposal with user; settle any material reviewer questions.
- [ ] Create a codex/ branch from current implementation after approval; inspect AGENTS.md and current changes.
- [ ] Record app/data/signing constraints and baseline checks. Do not copy or expose keys or reset phone data.

## 2. Brand and navigation shell
- [ ] Update native vector launcher/splash/header mark and DottedVoiceDrawable, with one #2056DF blue disk/ring interior and denser small white dots.
- [ ] Update values/colors.xml, values-night/colors.xml, ModernUi.kt and overlay brand colors with light/dark contrast checks; preserve voice amplitude/hold/tap/drag behavior.
- [ ] Split screen responsibilities out of WisprNavigation.kt/MainActivity.kt as needed rather than further growing those files. Keep native Views and existing repositories/providers.
- [ ] Add Settings category index/detail/back state, preserve readiness visibility and existing settings handlers. Model management has one destination. Retain right-side drawer.
- [ ] Validate layout/rotation/keyboard/large text with synthetic preview data before migrating storage.

## 3. Storage and deterministic text pipeline
- [ ] Implement tested transactional schema v2 migration in HistoryDatabase.kt; add SnippetRepository and optional expanded text; retain existing rows/events/IDs.
- [ ] Add normalized unique-trigger validation and a pure SnippetExpander with longest-match, Unicode boundaries and nonrecursive behavior.
- [ ] Integrate into WhisperAccessibilityService.kt after dictionary normalization and before optional cleanup. Protect exact replacement payloads with validated placeholders; persist raw/expanded/cleaned text correctly.
- [ ] Extend SettingsBackup.kt/import UI/version validation for snippet and tone data while reading v1 exports. Test round-trip, duplicates, corrupt data, interrupted migration and rollback.
- [ ] Prove missing/disabled snippets, direct mode, cleanup failure and no unexpected cloud calls using fixtures, not personal addresses or API keys.

## 4. Dictionary and Home
- [ ] Replace the multiline-only Dictionary editor with Dictionary/Snippets tabs and searchable list/edit/add/delete; preserve canonical aliases.
- [ ] Add paginated transcript search on the repository worker, debounce/cancellation, escaping, empty/no-results states and accurate original/processed search.
- [ ] Restyle date groups and transcript rows; retain minimal inline Copy/Delete/expand/original/cleanup actions. No calendar, notes or promotional cards.
- [ ] Update meaningful UI tests to navigate the new pages instead of assuming the old view index/accordion structure.

## 5. Tone & Style
- [ ] Add category/per-package rule store and an app picker with scoped launcher queries.
- [ ] Capture destination at recording start; add one-dictation manual override without overloading tap/hold bubble gestures.
- [ ] Integrate style only with opted-in cleanup, preserving numbers/names/meaning and exact snippet payloads. Direct mode remains AI-free.
- [ ] Test package switches, keyboard/overlay focus, unknown apps, disabled cleanup, override consumption and process restoration without exporting app inventory.

## 6. Insights
- [ ] Add event aggregation for daily words/count/duration, recognized WPM, current/longest streak, timezone/DST and date ranges.
- [ ] Replace donut-led design with metric tiles and native speaking-activity heatmap/day details; retain secondary duration/typing estimate.
- [ ] Verify against deterministic fixtures and accessible non-color/day-list equivalents, zero data and retained events after deleting history.

## 7. Providers, privacy and system
- [ ] Move existing editors into Speech-to-Text / Language Models without losing provider profiles or forcing local models.
- [ ] Add prompt View/Edit/Reset; explicit opt-in synthetic preview/test only if implemented; retain guarded cleanup behavior.
- [ ] Put privacy explanation, retention, storage, backup and destructive confirmations in Privacy & Data.
- [ ] Add explicit release check using existing OkHttp, prerelease-aware version comparison and external release link; handle offline/404/403/timeouts. No silent install.

## 8. Verify and deliver
- [ ] Run ./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease lintRelease --no-daemon, using workspace .tools/gradle.
- [ ] Run relevant device tests on authorized connected phones. Keep core mic denial, gestures, recording cancellation, native/web insertion and optional offline checks; do not call intermittent results universally fixed.
- [ ] Test actual user opt-in provider flows without exposing keys; don't automatically spend API quota or download GB models for a visual test.
- [ ] Build optimized APK, verify signature and 16 KiB ZIP alignment; measure bytes and compare baseline.
- [ ] Reuse the existing installation signer for adb install -r; never uninstall com.veenum.wisprlite to bypass a mismatch. Keep production signing material private.
- [ ] Remove owned fixtures/test package, restore only own accessibility binding, preserve other services and confirm daily-use startup.
- [ ] Scan staged changes, commit/push authorized source, verify CI and publish approved APK with accurate release notes. Keep screenshots/private data ignored.

## Usage-limit strategy

No five-hour completion promise. Prioritize usable checkpoints: brand/navigation -> storage/pipeline -> Dictionary/Snippets/search -> tone -> Insights/settings polish -> verification/release. Keep feature work on the development branch until gates pass; don't replace the user's working APK with a partially implemented build. Update handoff with exact commit, commands, failures, connected-device state and next step after each checkpoint. If interrupted, stop after saving a truthful handoff, not after inventing completion.
