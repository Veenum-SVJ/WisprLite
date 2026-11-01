# Privacy — WisprLite

WisprLite records speech only after you start dictation using its floating microphone button. The Accessibility Service identifies a focused text field and inserts the result. It does not upload the field's surrounding text for transcription or cleanup.

## Local and cloud processing
Local speech recognition processes audio on the phone. If a compatible local model is unavailable, dictation is refused; there is no automatic cloud fallback. Known speech-model packs download from the sherpa-onnx GitHub releases.

Cloud speech recognition sends recorded audio directly to the HTTPS transcription provider you configure. Optional AI Cleanup sends transcript text directly to the configured cleanup provider, including when speech recognition itself is local. Provider privacy policies, charges and retention rules apply. A connection test queries the provider's model list using your key.

## Storage
Text history is stored in an app-private SQLite database, with original, optional snippet-expanded and optional cleaned text. Dictionary entries, snippets and tone preferences stay in app-private storage. Snippet expansion runs locally; exact saved payloads are protected with placeholders during optional AI cleanup. History is kept until deleted by default, or expires after the period selected in Settings. Usage counters contain timestamps, word counts and speaking durations; deleting history does not reset these counters. Reset statistics separately.

Audio retention is off by default. Without retention, recording bytes are held for processing and are not saved as audio files. If enabled, WAV files are saved in app-private storage for 1, 7 or 30 days. Expiry runs at startup/settings changes and in daily background work; Android may defer scheduled work. Clear-text audio is not included in settings exports.

API keys are encrypted using Android Keystore-backed storage. Transcription and cleanup credentials are kept separately. Settings exports exclude keys by default; explicitly selected keys are included only in passphrase-encrypted backups. Automatic Android app backup is disabled.

Per-app tone rules use the destination app identifier locally. The launcher app picker is used only to choose rules; app inventory and package identifiers are not included in cleanup requests. Selected tone instructions, custom refinements and dictionary vocabulary may be included in the cleanup prompt. A next-dictation tone override is not exported in backups.

## Clipboard and permissions
When inserting text, the app may place it on the clipboard. On supported Android versions the clip is marked sensitive. The microphone, accessibility, notification and battery-management permissions support dictation and its background availability. You can pause the service or revoke permissions in Android Settings.

## Services
WisprLite has no account, analytics service, transcript sync or relay backend. Source and updates are available from https://github.com/Veenum-SVJ/WisprLite. An explicit update check contacts the public GitHub releases API without transcripts or provider keys. The app does not install APK updates itself.
