# Implementation progress
Plan: docs/superpowers/plans/2026-10-30-wisprlite.md

User approved full plan on 2026-10-30.
Task 1 in progress: imported v4.0.0, retained attribution, distinct application ID, no automatic release publishing.
Ruling: use the newly created empty project workspace directly on an implementation branch; there is no existing user code or branch to protect. Shell-specific skill bookkeeping is represented here for Windows portability.
Preflight: provider configuration is consumed by settings and dictation; history is consumed by screens and successful dictation; retention is consumed by recording lifecycle and worker. Keep these interfaces aligned.
GitHub retry returned HTTP 401 Bad credentials. Public repository not created.
JDK 17 found. Android SDK and ADB not found on PATH or default Android directory; baseline build pending environment check.

GitHub OAuth restored; public repository https://github.com/Veenum-SVJ/WisprLite created and foundation pushed (713aa1a). Gradle 8.13 downloaded. Official Android command-line tools downloaded and extracted; SDK components installing. ProviderConfigTest added before production implementation; RED execution pending toolchain.

Task 2/3/4 test preparation: added ProviderConfigTest, InsightCalculatorTest, RetentionPolicyTest. Production types intentionally absent until RED is observed. SDK licenses accepted and build-tools installation started. Initial Gradle process remains live resolving dependencies.

CI baseline failure diagnosed: setup-android defaults request removed SDK package 'tools'. Configure packages: platform-tools explicitly; no product-code failure observed yet.

RED observed locally for missing InsightCalculator/RetentionPolicy and ProviderConfig/DictationLanguage; CI run 41131992999 independently reached the same missing-class failures. Implemented pure insight calculation and scoped audio retention; initial 5 tests passed. Added reusable scripts/test-pure.ps1 for component checks while SDK resolves.

All 8 standalone Kotlin/JUnit component tests passed: provider URL safety/normalization, language mapping, Unicode counts, saved-time calculation, and retention. Full Android integration remains pending.

Integrated four-screen native navigation and SQLite history repository. Added instrumentation checks for Unicode, exact-once events, deletion independence, timestamp-tie pagination, and expiry. Device execution pending. Ruling: native SQLite integration will be validated by instrumentation rather than JVM Android stubs; unlike pure components, these tests were added alongside implementation and are not yet claimed passed.

Settings implemented: URL/model/key with connection test, language profiles, appearance, retention, typing speed, history clear and statistics reset. Scheduled expiry worker added; warm light/dark colors set. API keys excluded by default in backup selection. Removed updater UI/install permission. Release shrinking enabled with JNI keep rules. History detail now offers cleanup retry without incrementing counters.

CI run 41133306324 on 991c0d7 passed testDebugUnitTest, assembleDebug and lintDebug (BUILD SUCCESSFUL). Latest settings revision still running. Official platform/build-tools/ADB archives all checksum verified; extracting locally. Extended CI to compile instrumentation tests and validate release shrinking/lint.

Redmi debug install and Home/drawer smoke checks passed. A newer CI APK could not replace it because ephemeral runners generated different debug keys. Added persistent development signing via encrypted GitHub Actions secret; key remains outside Git. Full requirements review started.

Fresh review found credential-slot sharing between custom transcription and Groq cleanup. Separated the generic transcription key from the Groq cleanup key; added host-restricted legacy fallback. Credential-isolation device test and backup integration still pending. Review also identified service destruction/callback cancellation issues to fix before cloud/microphone testing.

Review fixes: separate provider credentials and encrypted-backup slot; service cancellation token, recorder stop/release on interrupt/destroy/pause, guarded late callbacks, synchronized native engine close/reload. DictationSession RED observed then full 9 pure tests GREEN. Credential isolation instrumentation test added (pending execution). Release R8 failure diagnosed as 512MB heap exhaustion; set 2GB heap and two workers. Local full review build running.

Model review fixes: SafeModelArchive stages and validates before same-filesystem promotion, rejects sibling traversal and links, limits extraction sizes, and preserves existing installs on validation failure. Downloader guards duplicate requests, checks available space and full response length, closes HTTP resources, and detects complete model files. Available models exclude staging/partial directories. Language profile selection now uses compatibility suggestions, with explicit multilingual suggestions for Auto/Pidgin; cloud requests receive Pidgin hints. All 12 pure regression tests passed outside sandbox (sandbox denied directory rename). Release signing now leaves unsigned output when no production key is configured instead of failing packageRelease. Full Android check running on current source.

Remaining usability fixes implemented: saved navigation/dictionary draft across rotation, Material buttons, theme-correct drawer surface, recording sound/vibration controls, actual model-storage total, generic provider readiness, safe legacy setup links. Local model loading now enforces chosen profile compatibility and unloads when cloud mode is selected. Multilingual Base is the recommended catalog entry for this app's mixed-language default. Full build from previous source snapshot is still running; final changed source must be rebuilt before verification.

Redmi instrumentation: 3/3 PASS on stable-key local debug APK. CI 41137216341 (66e4986) and 41136868608 passed expanded checks including release shrinking. Current uncommitted network cancellation changes still require a new final build; do not attribute those changes to previous green runs.

Fresh local full build completed successfully: testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, lintDebug, assembleRelease, lintRelease (7m34s). Release SHA-256 81E381BB4BFD0E872555AA8DC7F8314AEE972B392B06BBD495451C4CB85B2B2C. New speech-test/helper and isolated test-database changes were added after that build began and need separate rebuild verification. Model archive (207557382 bytes) and synthetic English WAV provisioned on Redmi; real-engine instrumentation currently running. ADB routine permission/service setup now handled directly per user preference, preserving other accessibility services.

Offline smoke test executed on Redmi and failed before inference: actual archive uses base-encoder/base-decoder/base-tokens filenames, while inherited loader expected unprefixed names. Added regression fixtures; targeted LocalTranscriber tests reproduced failures. Fixed prefixed filename discovery and int8 preference, plus catalog-architecture validation so partial transducers cannot pass as Whisper. Added synthetic test helper activity (test APK only), isolated history test database, reopen persistence check, and atomic guard against starting cleanup after cancellation. Pure session/archive suite: 13/13 PASS. Microphone read failures now stop/release and return to idle. Production signing key generated in ignored local files; not pushed.

Model-loader fix build passed all checks (6m36s). Real offline English test passed on Redmi: 8.055s synthetic audio, 8131ms model load+decode, matching expected English content; total first extraction/test 226.465s. Actual MainActivity launch then reproduced uninitialized keyRowSub; restored purpose-labelled Groq cleanup key row and added ActivityScenario startup/drawer/back regression. Gitleaks local history scan with narrowed debug-password allowance: no leaks found.

2026-10-31: verified startup fix on Redmi (startup/drawer/back 1/1 PASS after matching foreground launch via ADB; MIUI blocks instrumentation background launches). Reran repository/credential/offline speech instrumentation: 4/4 PASS. Full local checks PASS in 3m52s; 103 unit tests zero failures. Signed release 36,044,061 bytes verified by apksigner; SHA256 recorded in device report. Removed redundant in-test shell grants; permissions provisioned externally through ADB. Physical microphone test from interrupted prior session lacks evidence and remains open.

Redmi Activity recreation regression PASS: unsaved Yoruba dictionary draft and destination survive recreation; Back returns Home. Recorded user language corpus separately without treating text references as speech evidence.

Device evidence advanced: actual microphone interrupt/release and cross-app Unicode insertion integration PASS; encrypted backup/device Keystore round trip PASS (synthetic isolated state). Optimized release installed without clearing app data by re-signing a test copy with persistent development certificate; 1294ms cold launch / 75212KB foreground idle PSS snapshot. Large-font visual check found header/status-bar overlap; system-bar/cutout insets and transcript-row margins implemented, current full build running. Do not attribute earlier release measurements to the inset-fix revision.
