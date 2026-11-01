# OpenWisper 0.2.0 handoff

The approved OpenWhispr-inspired redesign is implemented and verified on the Redmi. Visible app name is **OpenWisper**, exactly O-P-E-N-W-I-S-P-E-R. Android application ID com.veenum.wisprlite, sideload signer and repository endpoint remain compatible with earlier installs. Never uninstall the main app to bypass a signer mismatch or clear user data.

## Current location and source

Active worktree `.tools/redesign`, branch `codex/openwhispr-redesign`; original workspace branch `implementation` will be fast-forwarded for delivery. Commits: ce20d60 core, 23bb89a screens, 1e11483 settings, 94c1054 integration/branding/capsule. A final fixture/documentation commit follows. Read git status and `docs/testing/openwisper-0.2.0.md` for actual evidence. Ignore old 0.1.1 goal completion when assessing this redesign.

## Implemented

Native blue fine-dot logo/light-dark design; right drawer; searchable Home with inline actions; Dictionary/Snippets spoken expansion; exact protected snippet payloads through optional cleanup; per-app tone categories and one-dictation override; activity heatmap/day details; six clean Settings destinations; existing cloud profiles and local model management; manual release check. Schema2 migrates existing history/events; backup2 reads legacy backup1 without deleting absent snippets. Direct mode does not invoke cleanup. All models remain optional downloads.

User's latest amendment: idle circle docks only left/right at a chosen height (nearest side; midpoint left). Recording expands inward into compact **translucent black** capsule with white voice-responsive dotted waveform. Tap start/stop and hold/release share the bubble. App/idle mark stays blue.

## Verified and installed

154 JVM tests and debug/release lint/build pass. 43 Redmi checks across 20 groups pass across full/remaining runs, including migration/snippets/tone, Home/Settings CRUD and synchronization, profiles, retention, permissions, watchdog, appearance/recreation, model controls, history actions, live size preview, language, backup picker, clipboard fallback, recording gestures/capsule and native/web insertion. Test-only fixes preserve assertions and wait for actual async dialog state. GitHub product-source CI at 94c1054 passed: https://github.com/Veenum-SVJ/WisprLite/actions/runs/41357725939 . Private logs live in ignored worktree `.tools`.

Optimized same-key APK `.tools/releases/OpenWisper-0.2.0-android.apk`: 36,285,527 bytes; SHA-256 2CCEBE0BBB194D3589E432F744EC3B0DA5D81858B10E341DC63B1F449575D327. Signature and 16 KiB ZIP alignment verified. Installed on Redmi with install -r; version0.2.0/code3, normal launch, microphone permission and accessibility binding confirmed. Other accessibility service preserved. Owned screenshot caches/fixture preferences and test helper removed. Providers/models/personal history retained. Original font0.9 and portrait lock restored; MIUI test launch op restored to ignore.

## Delivery complete

Final verification/docs commit d733f02 was fast-forwarded to default implementation and pushed. Public preview: https://github.com/Veenum-SVJ/WisprLite/releases/tag/v0.2.0-preview . GitHub asset state is uploaded, size36,285,527 and SHA-256 digest matches the local signed APK. Original workspace also has the APK at `.tools/releases/OpenWisper-0.2.0-android.apk`.

Automatic review rejected discarding the original workspace's owned handoff pointer, so it was preserved instead in a recoverable Git stash named “Preserve original handoff pointer before verified redesign delivery”. No uncommitted edit was discarded. Both default and redesign branches have the final source; this closeout documentation follows the release commit.

Pixel is disconnected: its 0.2.0 install/check remains pending. Install this same APK later using install -r, preserving its existing data; never copy keys between phones or uninstall to solve a mismatch.

## Honest limits

130% Home layout and private light/dark Home/Settings screenshots reviewed. The landscape request still produced portrait, so actual landscape is not verified. No new live provider quota or GB model downloads used. This update does not establish language accuracy, every app's tone routing, interrupted-write recovery or universally fixed historical web-insertion intermittency (this Redmi web test passed). See the verification report.

## Continuing if interrupted

Continue in `.tools/redesign`, inspect git status and this handoff, preserve user data and private keys/screenshots, and continue only a newly requested change or the disconnected Pixel update. The Redmi redesign/release is complete; do not repeat finished checks without a new reason. Use existing ignored local signing identity and shared SDK/cache; do not print keys or copy credentials between phones. Repository is public at https://github.com/Veenum-SVJ/WisprLite . Never publish private `.tools/openwhispr-reference` screenshots or resources/.env. The GitHub connector lacks release-asset upload; existing authorized gh/git CLI handles release delivery.
