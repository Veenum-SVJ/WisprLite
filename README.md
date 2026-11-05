# OpenWisper

Previously named WisprLite. A minimal native Android dictation app with configurable cloud providers and optional downloadable offline speech models.

[Download OpenWisper 0.2.6 preview APK](https://github.com/Veenum-SVJ/WisprLite/releases/download/v0.2.6-preview/OpenWisper-0.2.6-android.apk) · 37.6 MB · Android 11+ / arm64. Models download separately.

The 0.2.2 update uses a monochrome ring-and-waveform OpenWisper logo for service notifications, replacing the default microphone icon. Android supplies its status-bar tint.

The 0.2.3 update makes copied transcripts readable in clipboard previews and explains the words-per-minute setting used by Insights. Clipboard previews follow Android/keyboard behavior.

The 0.2.4 update adds a monochrome floating bubble, a shorter capsule that expands smoothly from the logo, distinct hold-to-record feedback and an in-logo loading waveform. Idle visibility follows the current keyboard or physical-keyboard editor, fixing retained bubbles after typing. The splash draws its mark directly on the blue background to remove the square image boundary.

The 0.2.6 update makes tap and hold visually distinct (capsule versus circular HOLD button) and keeps the loading waveform active when Android animation scales are zero.

## Screens
The 0.2.1 redesign keeps existing providers, models and history when updated with the same signer. See [verification and limits](docs/testing/openwisper-0.2.1.md).

- Home: searchable transcript history, inline copy/delete and original/expanded/cleaned text, expandable long transcripts and Direct/Cleanup mode.
- Insights: speaking activity heatmap, selectable day details, words, recordings, speaking time and streaks. Typing time saved is an estimate.
- Dictionary: custom words and pronunciation aliases, plus snippets whose spoken shortcuts expand saved text.
- Settings: grouped Preferences, Tone & Style, Speech-to-Text, Language Models, Privacy & Data, and System pages.

Use the top-right menu to open the right-side drawer; Settings is anchored at the bottom. The supplied blue waveform logo, licensed Yowza typography and Nucleo icons accompany desktop OpenWhispr light-mode styling adapted to mobile, with a dark theme too. Tone rules are optional, remembered per destination app; a manual override applies to the next dictation. Direct mode never enables AI cleanup. Snippet payloads are protected through cleanup; invalid AI placeholder output falls back to the expanded text.

## Updates

From version0.2.5, open Settings → System → Check for Updates. Choose Download and install to fetch the APK inside OpenWisper, watch progress or cancel, then confirm Android’s installation prompt. On first use, Android may ask you to allow installations from OpenWisper; return to the app afterward. Completed downloads can be installed again without re-downloading if you cancel the installer. Updates preserve settings and history. This flow is for the GitHub-distributed APK; older versions need a one-time APK/ADB update to get it.

Downloads use the official repository’s uploaded APK, published SHA-256 and size; the app also checks package identity, a newer Android version code and the existing signing certificate before requesting installation. No background update checks, silent installs or speech-provider credentials are used.

## Setup
Install an APK, grant microphone access and enable the OpenWisper Accessibility Service. On some Xiaomi/Redmi phones, enabling Install via USB or approving the installation prompt is necessary during development.

On Xiaomi/Redmi/POCO, also allow OpenWisper under Background autostart. Settings includes a shortcut and explanation; this permission is separate from standard battery optimization. On the tested Redmi, enabling it allowed automatic service recovery after an idle process kill. This is not a guarantee against every firmware restriction, force-stop or reboot case.

Choose cloud transcription and enter an HTTPS API base URL, model name and API key, or download and activate a compatible offline model. Saved providers keeps multiple encrypted credentials with separate explicit choices for speech and cleanup. Groq uses Whisper-compatible multipart speech; the Google AI Studio preset uses audio chat. Saving a profile does not enable cloud recording or cleanup, and failures never switch providers. Live provider/model availability still needs a configured account and speech trial. Cloud cleanup sends text even when speech recognition is offline.

Model management remains visible in cloud and local modes, including installed packs, official publisher badges, download sizes and installed storage. See [saved profiles and Settings behavior](docs/design/saved-provider-profiles.md).

In the 0.1.1 update, every downloaded model has a Delete action, including the selected model. You can remove all local packs and use cloud transcription only. Choose a saved provider's **Use for transcription** or **Use for AI cleanup** independently; for example, Groq speech with Google AI Studio cleanup. Direct mode skips cleanup. See [Insights and model removal](docs/design/insights-and-model-removal.md) for update status.

The local catalog offers 12 compatible packs: multilingual and English Whisper Tiny, Base and Small; multilingual Medium, Large-v2, Large-v3 and Turbo; NVIDIA Parakeet; and Moonshine Tiny. Official provider logos, download sizes and installed storage appear in Settings. Downloads range from roughly 100 MB to 1.93 GB; models are downloaded separately from the APK. Larger packs need additional extraction space and RAM and may be slow on phones. The newly listed larger packs are official compatible sherpa-onnx exports, not phone performance or accuracy recommendations. Fresh Whisper installations retain the compact int8 pair when available. Desktop GGUF/Q5 files and arbitrary model files cannot be imported. See [model details](docs/design/model-provider-badges.md).

Language at the top of Settings offers **English** (the default) and **Auto-detect**. Auto-detect uses the languages supported by the chosen model; English-only models remain English-only. Short phrases can be misidentified. Explicit Yoruba and Nigerian Pidgin profiles were removed at the user's request after unsuccessful accuracy trials. Historical research is retained for transparency and is not a current accuracy guarantee. Existing retired profiles migrate to Auto-detect; you can select English explicitly.

Tap the floating bubble to start/stop, or hold to record and release to transcribe. Drag it vertically along either screen edge; releasing docks it to the nearest left or right side. Tap recording opens the compact waveform capsule. Hold recording stays circular with a live waveform, double ring and HOLD indicator; release to finish. Processing animates inside the logo even if Android transition animations are disabled. Settings provides a live bubble-size preview. Audio saving is off by default. Text history and statistics stay on the device; see [PRIVACY.md](PRIVACY.md).

## Development
Requires JDK 17, Android SDK platform 35 and build-tools 35.0.0. Set sdk.dir in an untracked local.properties file.

    ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease lintRelease
    # After installing the debug APK and debug Android-test APK:
    powershell -File scripts/test-device.ps1 -Serial YOUR_ADB_SERIAL

Android 11/API 30 or newer and arm64 are targeted. The device script handles Redmi foreground-launch restrictions and the test helper field. Database and credential fixtures use isolated test storage; the service test briefly records and cancels microphone audio, then verifies cross-app insertion. It briefly revokes microphone permission to test denial, grants permissions needed by the checks, then restores the original microphone/notification grant states and whether WisprLite accessibility was enabled, preserving other services. It does not uninstall the app or clear personal history. Run it while no personal dictation is in progress. For Xiaomi foreground-launch restrictions, `-AllowMiuiTestLaunch` temporarily allows the app’s MIUI background activity launch operation and restores its original state in the runner’s final cleanup. Real speech accuracy, cloud-provider credentials and airplane-mode checks are separate.

Optional device-runner flags are explicit: `-IncludeOfflineFixture` exercises the complete offline service with a provisioned synthetic `files/english-test.wav` and installed Tiny English model. It refuses pre-existing personal recording files and removes only its own fixtures. `-IncludeGroqCloudFixture` sends synthetic English audio and synthetic mixed-language cleanup text to the configured Groq endpoints using keys stored on the device; it can incur provider usage. Neither live API calls nor these provisioned model fixtures run by default. Do not substitute personal audio for the synthetic fixture.

For an explicit idle-process recovery test with the debug app, run `python scripts/test-background-recovery.py --serial YOUR_ADB_SERIAL` while no dictation is in progress. It checks the existing binding and microphone state, requires Xiaomi startup/battery setup where applicable, kills only WisprLite through its own UID, then observes automatic rebind and short-term stability. It never force-stops, uninstalls or clears app data, and restores only its existing accessibility entry if automatic recovery fails. JSON evidence stays under ignored `.tools/device-tests/`.

Release code/resource shrinking is enabled. Without keystore.properties the release APK is unsigned. Debug builds use the persistent development keystore when available under .tools; CI restores it from an encrypted repository secret. Never distribute development signing material or commit API keys, recordings, keystores or signing passwords.

The optimized sideload APK is approximately 36 MB; installed app storage and optional model storage are separate. See [daily-use handoff](docs/testing/daily-use-handoff.md) and [device results](docs/testing/device-report.md) for measured validation and remaining work. A passing build does not establish language accuracy or device performance.

The [public speech evaluation](docs/testing/public-corpus-evaluation.md) records pinned Yorùbá/Pidgin samples, opt-in measurement commands and Unicode-aware scoring. Its observed Large-v3 errors and Google request failures do not establish language acceptance.

## Provenance and license
Adapted from EdiBianco/OpenWhispr v4.0.0, commit 7f41114c1028190749b3bfeed721c3aef05d56fe, and its kafkasl/phone-whisper foundation. Apache-2.0 license retained in LICENSE; original documentation is preserved under docs/upstream. See NOTICE. WisprLite changes are recorded in Git history.
