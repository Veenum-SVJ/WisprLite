# OpenWisper0.2.5 in-app updates

Settings → System now checks GitHub release metadata, offers Download and install, displays progress/cancellation and hands the verified private-cache APK to Android’s installer. The first install can route to the app-specific unknown-sources permission, returning to continue. Cancelling an installer keeps the verified download available. Activity recreation keeps in-process transfer state; a completed APK and bounded metadata survive process restart. Interrupted partial downloads are removed on next initialization and retry from scratch. Checks/downloads require explicit user action.

Only the canonical OpenWisper-version-android.apk for the release is selected, with uploaded state, a positive bounded size and published SHA-256. Download URLs are constructed for the fixed repository; HTTPS redirects are restricted to GitHub asset hosts. Streaming rejects oversize, incomplete, cancelled or corrupt downloads. Before offering installation and again before opening Android, the app verifies checksum/size, package identity, a strictly newer version code and the currently installed signer. FileProvider exposes only the dedicated app-updates cache folder with temporary read access. There is no browser handoff, silent install or provider-key involvement. This is the GitHub APK distribution path, not a Google Play in-app-update implementation.

## Validation

-160 JVM tests passed, including malformed metadata/asset names, checksum/size/partial/cancellation and URL construction.
- Debug/test assemblies and debug lint passed. Final release assembly and release lint passed. Signature and16 KiB ZIP alignment verified.
- Redmi: same-version APK rejection and narrow read-only content URI checked; four Settings routing/recreation checks passed.
- Redmi: actual newer same-key0.2.5 APK accepted; identical newer package signed with a test key rejected. The forged APK was only parsed, never installed.
- First test-helper install was cancelled by MIUI; immediate retry succeeded. No main app uninstall or data clear.

Signed APK37,561,928 bytes; SHA-256d080778169900303b30aafafacd30b09f11c98060c9f742e1c5a5faf2b6678c3. Real in-app download/permission/installation trial follows publication and is recorded in the handoff. Private logs/screenshots and test signing fixture remain ignored under .tools. Pixel update remains pending unless a later checkpoint says otherwise.
