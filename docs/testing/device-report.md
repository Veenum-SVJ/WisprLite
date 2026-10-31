# Device validation — work in progress

## Redmi 12
- USB device model: 23053RN02A, Android 15; ADB authorized.
- Tested initial integration commit: 991c0d7, CI run 41133306324.
- Debug APK size: 51,907,857 bytes; models not bundled.
- Installed alongside the original OpenWhispr app using a distinct application ID.
- Cold launch reached Home without an app crash; first launch included notification permission and fresh-install setup.
- Drawer showed Home, Insights, Dictionary, and Settings anchored at bottom.
- Initial foreground idle measurement: PSS 105,215 KB, RSS 230,667 KB. This is a debug-build snapshot, not a release idle/background benchmark.
- Found stale welcome branding; corrected source for next build.

## Remaining validation
Latest Settings/theme revision; instrumentation database checks; release shrinking and install; microphone/accessibility setup; local and cloud dictation; English/Yoruba/Pidgin/mixed accuracy; audio expiry; background recovery; rotation/large font; Pixel 6 comparison. Do not treat these as passed.

## Stable-key local debug checks
- Installed WisprLite and its test APK on the Redmi after approved removal of the temporary-key installation.
- AndroidJUnitRunner: OK (3 tests). Verified isolated transcription/Groq credentials, Unicode text persistence, exact-once counters across cleanup and history deletion, timestamp-tie pagination, and expiry independent of counters.
- CI run 41137216341 for 66e4986 passed the expanded unit/debug/instrumentation-assembly/lint/release checks. Physical instrumentation does not by itself verify microphone capture, model accuracy, or cloud providers.

## Offline engine regression and result
- First real model run correctly exposed the inherited filename mismatch: Base uses base-encoder/base-decoder/base-tokens, not unprefixed names. Added fixtures and fixed detection; also reject catalog/architecture mismatches.
- Rerun on Redmi: OK (1 test), real sherpa-onnx multilingual Whisper Base decoding a synthetic English WAV.
- Fixture: 8.055 seconds. Model loading plus processing: 8,131 ms. Output: 'Hello Whisper Light. This is a short English dictation test. Please send the document tomorrow morning.'
- First installation/extraction plus test took 226.465 seconds; this is not steady-state inference latency.
- Subsequent Activity launch reproduced a missing keyRowSub initialization. Restored the Groq cleanup-key row and added a startup/drawer instrumentation regression; verification pending.
- No Yoruba, Pidgin, mixed-language accuracy or Pixel 6 result is implied by this English synthetic test.

## Current verification (2026-10-31)
- Source 604fa12 plus test-only permission-setup simplification: full local unit/debug/test-APK/lint/debug-and-release build PASS (3m52s), 103 unit tests, zero failures.
- Redmi startup/drawer/back regression: OK (1 test). MIUI blocked the instrumentation background launch until ADB brought the matching MAIN/LAUNCHER intent with flags 0x10008000 to the foreground. No new startup exception occurred.
- Separate rerun: HistoryRepositoryTest, CredentialIsolationTest and OfflineSpeechSmokeTest: OK (4 tests), 7.696s total. Includes real Base model inference; does not test the physical microphone.
- Production-signed release APK: 36,044,061 bytes, signature verified by apksigner. SHA-256: 4F503BB655ACC22D2453A986107B0602FB1118ED3142D14D824947C0556E162F.
- Release certificate SHA-256: a4d82dabfecb078968554f19d625b95bc1cc3499b3289189617cffff17e7e13b. Private signing material remains ignored locally.
- Physical microphone/insertion attempt from the previous session has no surviving result artifact and is NOT counted as passed. Cross-app, retention, rotation/large-font, release runtime and language/device coverage remain open.
- Test prerequisites: grant RECORD_AUDIO and POST_NOTIFICATIONS using ADB before StartupNavigationTest. This test no longer executes redundant shell permission commands inside its own instrumentation process.

- Extended startup regression to navigate to Dictionary, enter unsaved Yoruba Unicode text, recreate the Activity, assert destination and exact draft survive, and return Home with Back. Redmi result: OK (1 test), 20.019s; test APK rebuilt before execution. No dictionary changes were saved.
- User supplied Pidgin/Yoruba accuracy phrases; recorded in language-corpus.md as references only, with real pronunciation testing still pending.

## Microphone, cross-app and backup integration
- ServiceLifecycleIntegrationTest on Redmi: OK (1 test), 11.34s. Test invokes the production start-recording path, verifies Android AudioRecord is recording, interrupts after 250ms, asserts recorder/PCM references cleared and state IDLE, and confirms successful-recording counters unchanged. Capture is cancelled, never uploaded.
- Same test invokes production insertion into the separate com.veenum.wisprlite.test EditText and verifies actual field contents include Yoruba Unicode and Pidgin wording. This proves the insertion path, not a complete floating-button-to-speech-to-field gesture.
- CredentialIsolationTest rerun: OK (2 tests), 5.612s. Includes encrypted file round trip with Android crypto, wrong-passphrase rejection, exact Unicode settings round trip and three independent keys re-encrypted through Android Keystore. Synthetic device-protected test state only; normal settings/credentials untouched. Full system-document-picker UI round trip remains untested.

## Optimized APK runtime sample before inset fix
- Installed a copy of the production release APK re-signed with the existing development certificate, allowing an in-place update without clearing WisprLite data. Production-signed original retained separately.
- Verified release flags omit DEBUGGABLE, Home renders, cold launch TotalTime 1294ms; foreground idle PSS 75212KB, RSS 203723KB, swap PSS 1232KB. Single snapshot with local engine inactive, not an inference or background benchmark.
- At font scale 1.5, Home content remained readable, but visual inspection exposed status-bar/header overlap on Android 15. Fixing system-bar/cutout insets and adding history-row margins; these changes require new build and device confirmation. Original font scale 0.9 restored.

## Inset-fix verification — source 2e69b4b
- Full local checks PASS in 4m32s: 103 unit tests, debug APK, test APK, lint debug/release and signed release. CI Android checks 41177537748 and secret scan 41177537612 PASS on the same source. Local gitleaks full history: no leaks.
- Updated Home visually confirmed on Redmi: navigation button begins at y=121, below the status bar; transcript cards are inset from screen edges. Startup/drawer/Unicode Activity-recreation regression rerun at font scale 1.5: OK (1 test), 18.48s. Restored original font scale 0.9.
- Offline engine rerun with airplane_mode_on=1 and wifi_on=0: OK (1 test), 8.262s. Original airplane_mode_on=0 and wifi_on=1 restored in finally.
- Current signed APK: .tools/releases/WisprLite-0.1.0.apk; 36,044,061 bytes; SHA-256 D622A83A176386A2BD7F08796F4E1A7337C49295A796728989153AA299F7FF55; production signature verifies.
- Native offline runtime libraries account for about 32MB of the APK. Model weights are downloaded separately; this build does not bundle speech models.
- User voice-corpus test requested on the empty helper field; results pending. No accuracy claims inferred from the written reference phrases.

## First user corpus attempt — failed
- User reports the Pidgin/Yoruba phrases appeared in Chinese. Current settings had no language_profile override (Mixed/Auto), multilingual Base selected, direct mode by default.
- This is a failed speech-accuracy result, not a passed language check. Exact output was not independently captured before the user report; do not fabricate a transcript or word-error rate.
- Corrective work: Home language control now opens the language picker directly; explicit Yoruba remains yo; Pidgin now supplies supported en as an English-based recognition hint plus its cloud Pidgin prompt. This is not a claim of native Pidgin support or guaranteed accuracy. Named profiles now override stale legacy language sets, including after upgrades.
- Regression: expected Pidgin en mapping failed (RED) before implementation; standalone suite passed 13/13 afterwards. Full changed-source build and device retest running/pending.

## Explicit profiles and compact Home
- Current native device runner PASS: 8 tests, including a new test proving named Yoruba/Pidgin profiles override stale Chinese legacy settings (yo/en respectively), while Auto clears the forced code. This validates configuration routing, not real Yoruba/Pidgin recognition accuracy.
- Compact Home debug APK installed and visually inspected: plain menu button, language and Direct/Cleanup controls in one row, date-grouped text history below. No repeated introductory text when history exists.
- Home history independently confirms the user's failed Auto attempt contains CJK output. The exact personal transcript and screenshots remain outside Git; no fabricated accuracy metric.
- Manual language-picker UX confirmation and explicit-profile speech retries requested; pending user result.
