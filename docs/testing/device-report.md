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
