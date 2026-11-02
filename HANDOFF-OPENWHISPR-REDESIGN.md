## Active follow-up — 2026-11-02 light-mode fidelity

The user rejected the prior visual interpretation and explicitly asked to copy OpenWhispr light mode and icons. Work is now in the ORIGINAL workspace root, branch `codex/reference-light-mode`, not the old .tools/redesign worktree. No new release published. See `docs/design/2026-11-02-light-reference.md` and git status.

Current Light desktop UI and CSS inspected; corrected white/neutral surfaces, compact grouping, native Light/Dark/Auto segmented controls, Dictionary rows/tabs and Home search. User confirmed Nucleo and Yowza licences, explicitly authorized private font upload to GitHub, and supplied final logo PNG. Original image copied unchanged to drawable-nodpi/openwisper_logo.png. 28 Nucleo glyphs ported to native vectors; five licensed fonts stay in ignored .tools/licensed-fonts and nine approved private GitHub secret chunks. Raw fonts must never be committed. Fonts are generated build resources with a system-font fallback for public-source builds. Header/launcher/splash/idle bubble use supplied logo; recording retains black translucent capsule and voice-responsive white rounded bars. Build/version0.2.1/code4.

Earlier layout builds and the first full licensed-branding build passed 154 JVM tests, debug/release lint and assembly. Final source is frozen in .tools/light-brand-source.sha256 (653cb9c7f3b61a03353cf6efcf702fb3f85c9a0ac195b93bb09d0f3c0da94bcc); delivery build running .tools/light-brand-delivery-build.log. This includes final Dictionary icon actions/header and Theme choice synchronization. Initial phone preview Home/Settings inspected, but helper installation was cancelled. Redmi currently has a temporary debug layout preview0.2.0 (not the final0.2.1 asset build); data retained. Retry helper installation with final APK, run scripts/test-device.ps1 -AllowMiuiTestLaunch (or focused .tools/test-light-reference.ps1), inspect actual final screenshots, then build/sign/align optimized APK with existing persistent sideload signer, install -r, remove owned test artifacts/helper and publish new0.2.1 preview only after verification. Keep original0.2.0 APK under .tools/releases. No pending user permissions: both asset licences confirmed; private GitHub font upload explicitly approved and nine YOWZA_FONT_01..09 secrets stored successfully. No raw fonts staged. Desktop app left on Home in its original Light mode.

The sections below describe the already delivered 0.2.0 release, not completion of this follow-up.

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

## Current follow-up checkpoint

All licence questions resolved. User explicitly approved private GitHub font storage; YOWZA_FONT_01..09 stored successfully and workflow reassembles them. Fonts stay under ignored .tools/licensed-fonts and generated build output; no raw OTF files are public. The provided final logo is in drawable-nodpi/openwisper_logo.png, copied unchanged. Final code0.2.1/code4 includes exact 28 Nucleo vectors, Yowza/Yowza Soft, reference light tokens/layout, inline Theme choices, compact Dictionary icon actions and new logo/rounded recording waveform. Multiple full builds passed 154 tests/both lints. Final proportional icon wrapper build currently .tools/light-brand-icon-build.log; frozen source hash de93e4da71e0ba517bb49cabf076550d9fa8229ae552e5b80b382c770955b48e.

ADB device disconnected before final install. Async reconnect question pending; no phone visible on last check. Redmi still has earlier temporary debug layout0.2.0 preview, not final0.2.1. Its data has been preserved; the rejected helper was not installed. When phone reconnects, use app/build/outputs/apk/debug/app-debug.apk and matching helper, approve phone prompt if needed, run relevant UI/gesture tests or full scripts/test-device.ps1 -AllowMiuiTestLaunch, inspect new font/logo screenshots, then sign/verify/align latest release with existing sideload key and install -r. Existing .tools/releases/OpenWisper-0.2.1-android.apk is a pre-final-icon-wrapper candidate and must be replaced from the newest build before delivery. Final same-key source/default-branch merge/public release not done. Keep all private images/logs/raw fonts ignored.
