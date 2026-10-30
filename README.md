# WisprLite

A minimal native Android dictation app with configurable cloud providers and optional downloadable offline speech models.

## Screens
- Home: local text history, original/cleaned text, copy/delete, cleanup retry, Direct/Cleanup mode.
- Insights: recordings, words, speaking duration and estimated typing time saved.
- Dictionary: custom words, names and pronunciation aliases.
- Settings: providers, offline models, language profiles, appearance, recording feedback, retention and encrypted settings backup.

Use the top-left menu to navigate; Settings is anchored at the bottom. Warm light/dark themes follow the system by default.

## Setup
Install an APK, grant microphone access and enable the WisprLite Accessibility Service. On some Xiaomi/Redmi phones, enabling Install via USB or approving the installation prompt is necessary during development.

Choose cloud transcription and enter an HTTPS API base URL, model name and API key, or download and activate a compatible offline model. API endpoints must support OpenAI-compatible multipart audio transcription. Cleanup can use a separate provider/key/model. Cloud cleanup sends text even when speech recognition is offline.

Language profiles: Mixed/Auto, English, Yoruba and Nigerian Pidgin. English/Yoruba can supply an explicit language code. Pidgin has cloud recognition hints; it is not a separately supported base Whisper language. Multilingual Base is the starting recommendation for mixed-language testing, not a guarantee of Yoruba or Pidgin accuracy. Desktop Q5 model files cannot be imported into the Android runtime.

Audio saving is off by default. Text history and statistics stay on the device; see [PRIVACY.md](PRIVACY.md).

## Development
Requires JDK 17, Android SDK platform 35 and build-tools 35.0.0. Set sdk.dir in an untracked local.properties file.

    ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease lintRelease
    ./gradlew connectedDebugAndroidTest

Android 11/API 30 or newer and arm64 are targeted. Instrumentation tests currently run against a fresh test installation and exercise history/database behavior and credential isolation. Do not run database-reset tests against personal history you want to retain.

Release code/resource shrinking is enabled. Without keystore.properties the release APK is unsigned. Debug builds use the persistent development keystore when available under .tools; CI restores it from an encrypted repository secret. Never distribute development signing material or commit API keys, recordings, keystores or signing passwords.

See docs/testing/device-report.md for measured results and outstanding tests. A passing build does not establish language accuracy or device performance.

## Provenance and license
Adapted from EdiBianco/OpenWhispr v4.0.0, commit 7f41114c1028190749b3bfeed721c3aef05d56fe, and its kafkasl/phone-whisper foundation. Apache-2.0 license retained in LICENSE; original documentation is preserved under docs/upstream. See NOTICE. WisprLite changes are recorded in Git history.
