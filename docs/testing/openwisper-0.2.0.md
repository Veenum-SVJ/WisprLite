# OpenWisper 0.2.0 verification

Previously named WisprLite. Android application ID and persistent sideload signer are unchanged, so this is a data-preserving update.

## Delivered behavior

Blue fine-dot logo and native light/dark screens; transcript search and inline actions; Dictionary/Snippets with spoken expansion; optional per-app tone rules and next-dictation override; speaking activity heatmap; six grouped Settings destinations; manual release checks. Recording expands the edge-docked logo inward into a translucent black capsule with white voice-responsive dots. Tap and hold share the bubble. No new models download automatically.

## Evidence

- Final product build: 154 JVM tests, debug/release lint, debug APK, AndroidTest APK and optimized release assembly passed. Test-only synchronization corrections subsequently compiled and passed debug lint.
- Redmi: 43 checks across 20 groups passed across the completed full and remaining runs. Covers migration, Unicode snippets, tone rules, Home/Dictionary/Snippets, Settings synchronization/recreation, provider isolation, retention, permission denial, watchdog, light/dark, model controls, inline history actions, live size preview, language selection, backup picker cancellation/roundtrip, clipboard fallback, model availability, tap/hold/cancel/edge docking/capsule shape, native and web insertion.
- Early runs found editor handler initialization, fixture ordering and asynchronous confirmation assumptions, and an obsolete clipboard fixture assumption (Home now has an editable search field). Fixed the initialization and fixtures; retained exact content/history/clipboard assertions. MIUI background activity launch was temporarily allowed only for UI tests and its original ignore state restored.
- Private Home/Settings light/dark captures reviewed. Home at 130% font scale reviewed; original 0.9 font scale and portrait lock restored. Requested landscape rotation did not yield a landscape capture, so actual landscape layout is not claimed verified. Activity recreation and pure capsule geometry tests passed.
- Independent static reviews covered core, screens, settings, pipeline and final branch. Reported restore URL/key pairing, download action/reset, editor lifecycle and cleanup-switch synchronization findings were fixed.
- GitHub Android checks passed for product-source checkpoint 94c1054: https://github.com/Veenum-SVJ/WisprLite/actions/runs/41357725939 . Subsequent changes are fixture synchronization and documentation.

## APK and installation

OpenWisper-0.2.0-android.apk: **36,285,527 bytes** (36.3 MB / 34.6 MiB), SHA-256 `2CCEBE0BBB194D3589E432F744EC3B0DA5D81858B10E341DC63B1F449575D327`. Optional model storage is separate. Signature verification and 16 KiB ZIP alignment passed.

Optimized version 0.2.0 / versionCode 3 installed on the Redmi with install -r. Normal launch passed; OpenWisper accessibility bound with microphone permission, preserving the other enabled service. Removed only owned test cache directories, isolated fixture preferences and com.veenum.wisprlite.test. Existing providers, models and personal history were retained; the main app was not uninstalled. Pixel was not connected for this redesign verification or final installation.

## Limits

No new live cloud speech/cleanup quota was used for this visual update. Prior provider accuracy results remain historical; these checks do not establish multilingual accuracy, every target app's tone behavior, interrupted-device-write recovery, actual landscape recording animation, or every OEM's background recovery behavior. The previous web-insertion intermittency is not claimed universally solved, although this Redmi native/web run passed. New downloaded model packs were not automatically provisioned or evaluated.

Private logs/screenshots remain under ignored .tools and are not published. The app has no account, relay backend or transcript sync.

Public release: https://github.com/Veenum-SVJ/WisprLite/releases/tag/v0.2.0-preview . Uploaded APK state/size/SHA-256 verified against the local installed artifact.
