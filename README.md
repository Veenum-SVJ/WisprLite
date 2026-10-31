# WisprLite

A minimal native Android dictation app with configurable cloud providers and optional downloadable offline speech models.

## Screens
- Home: inline text history, original/cleaned selection, copy/delete and cleanup actions, expandable long transcripts, Direct/Cleanup mode.
- Insights: recordings, words, speaking duration and estimated typing time saved.
- Dictionary: custom words, names and pronunciation aliases.
- Settings: providers, offline models, language profiles, appearance, recording feedback, retention and encrypted settings backup.

Use the top-right menu to open the right-side drawer; Settings is anchored at the bottom. Warm light/dark themes follow the system by default.

## Setup
Install an APK, grant microphone access and enable the WisprLite Accessibility Service. On some Xiaomi/Redmi phones, enabling Install via USB or approving the installation prompt is necessary during development.

Choose cloud transcription and enter an HTTPS API base URL, model name and API key, or download and activate a compatible offline model. API endpoints must support OpenAI-compatible multipart audio transcription. Cleanup can use a separate provider/key/model. Cloud cleanup sends text even when speech recognition is offline.

The local catalog includes Whisper Tiny multilingual/English, Base and Small, NVIDIA Parakeet, and Moonshine Tiny, with official publisher badges. Download sizes and installed storage are shown separately; Tiny packs are approximately 100–120 MB downloads. Fresh Whisper installations omit redundant full-precision weights when a complete int8 pair is present; existing installations are preserved. Smaller models are not a verified fix for Yorùbá accuracy. See [model badges, storage and API compatibility](docs/design/model-provider-badges.md).

Language profiles: Mixed/Auto, English, Yoruba and Nigerian Pidgin. English/Yoruba can supply an explicit language code. Pidgin uses English-based recognition and cloud Pidgin vocabulary hints; it is not a separately supported base Whisper language. Auto detection can misidentify short phrases: choose English, Yorùbá or Pidgin explicitly using Language at the top of Settings. Both generic Base and Small failed our real Yoruba accuracy/diacritic trials; neither is recommended as a verified Yoruba solution. Desktop Q5 model files cannot be imported into the Android runtime.

Tap the floating bubble to start/stop, or hold to record and release to transcribe. Drag it freely to position it. Settings provides a live bubble-size preview. Audio saving is off by default. Text history and statistics stay on the device; see [PRIVACY.md](PRIVACY.md).

## Development
Requires JDK 17, Android SDK platform 35 and build-tools 35.0.0. Set sdk.dir in an untracked local.properties file.

    ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease lintRelease
    # After installing the debug APK and debug Android-test APK:
    powershell -File scripts/test-device.ps1 -Serial YOUR_ADB_SERIAL

Android 11/API 30 or newer and arm64 are targeted. The device script handles Redmi foreground-launch restrictions and the test helper field. Database and credential fixtures use isolated test storage; the service test briefly records and cancels microphone audio, then verifies cross-app insertion. It briefly revokes microphone permission to test denial, grants permissions needed by the checks, then restores the original microphone/notification grant states and whether WisprLite accessibility was enabled, preserving other services. It does not uninstall the app or clear personal history. Run it while no personal dictation is in progress. Real speech accuracy, cloud-provider credentials and airplane-mode checks are separate.

Release code/resource shrinking is enabled. Without keystore.properties the release APK is unsigned. Debug builds use the persistent development keystore when available under .tools; CI restores it from an encrypted repository secret. Never distribute development signing material or commit API keys, recordings, keystores or signing passwords.

See docs/testing/device-report.md for measured results and outstanding tests. A passing build does not establish language accuracy or device performance.

## Provenance and license
Adapted from EdiBianco/OpenWhispr v4.0.0, commit 7f41114c1028190749b3bfeed721c3aef05d56fe, and its kafkasl/phone-whisper foundation. Apache-2.0 license retained in LICENSE; original documentation is preserved under docs/upstream. See NOTICE. WisprLite changes are recorded in Git history.
