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
