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
