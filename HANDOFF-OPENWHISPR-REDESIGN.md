# OpenWisper current handoff — 0.2.4

Use the ORIGINAL workspace root, not .tools/redesign. Current feature branch codex/monochrome-bubble is being fast-forwarded to default implementation. Product code commit0ab1e00. App spelling OpenWisper (capital O/W, no h); public repository remains Veenum-SVJ/WisprLite. User authorizes data-preserving device updates, testing and public source/APK delivery. Never uninstall or clear main app data, copy keys between phones, or publish private screenshots/credentials.

## Latest completed work

Monochrome floating logo; shorter100dp recording window at100% size, live waveform with continuous260ms expansion/220ms collapse, hold-specific brighter outline and release cue, processing waveform inside the logo. Existing size control/48dp minimum touch target retained; Settings preview matches. Reduced-motion skips animation. Launcher/header supplied full-colour logo and notification icon unchanged.

Visibility now checks fresh visible keyboard windows, or an active editor with physical keyboard. Removed stale started-input/insets signals as independent witnesses. Repeated focus events cannot indefinitely restart the pending hide. Busy capture/processing stays reachable; idle bubble hides after software keyboard dismissal even with retained editor focus. User also reported a square on launch: splash now uses transparent white ring/waveform directly on the blue surface; a cold Redmi launch capture confirmed no square boundary.

## Verified and installed

Final full debug/release builds, both lint variants and156 JVM tests passed. Seven unique focused Redmi checks passed across two runs (gestures repeated on final build): rendered states/monochrome/edge continuity/loading movement, keyboard dismissal under repeated focus refreshes, tap/hold/drag/cancel, live size preview, native insertion, local WebView insertion and Unicode clipboard fallback. Initial visual test caught slight blue tint; corrected to neutral RGB24/24/24 and passed final render check. Private evidence: .tools/monochrome-bubble-final-build.log, .tools/monochrome-bubble-behavior.log, .tools/monochrome-bubble-final-phone.log and .tools/monochrome-bubble-ui. See docs/testing/openwisper-0.2.4.md.

Redmi12 (50e64c4c7d76): optimized0.2.4/code7 installed with install-r, normal launch/microphone permission/service binding/no crashed services verified. MIUI dropped own service selection during update; restored it preserving Attenora. Test app and owned screenshot caches removed. MIUI10021 restored to ignore. User data retained. Pixel is disconnected, last installed0.2.3; install same APK later with install-r.

APK .tools/releases/OpenWisper-0.2.4-android.apk:37,545,488 bytes. SHA-25694a7eaa19efbe7f101172222e89073eb9e5777ca7bebbf033f9ad77223540df4. Persistent sideload key/signature and16 KiB alignment verified. Release target v0.2.4-preview; verify GitHub upload before reporting publication. Final GitHub CI may still be running; local checks passed. Do not claim speech quality/live cloud or every app was tested by this visual update.

## Prior changes and private assets

0.2.3 shows readable plain-text clipboard previews (removed sensitive flag) and explains typing speed as WPM used only for Insights time-saved estimates. User clarified the lingering30-second item is Android's clipboard preview; its lifetime is system-controlled, no app timeout change made. Insertion remains paste-based with clipboard fallback. Do not re-mask ordinary transcripts or change global phone accessibility timeouts.

Five licensed Yowza/Yowza Soft fonts stay ignored in .tools/licensed-fonts. User confirmed font and Nucleo licences and explicitly approved private GitHub font storage. CI restores nine encrypted YOWZA_FONT_01..09 secrets; never publish raw font files. 28 Nucleo vectors plus MIT square-sparkle icon, reference light styling and original supplied logo are from0.2.1. Original PNG SHA-2565473b51b51035311b62d3ec4509b4295e4782f5980138a9a91d13e8725280289.

Keep recoverable Git stash “Preserve original handoff pointer before verified redesign delivery”. Do not discard it. Public repository history retains earlier delivery details. GitHub connector lacks asset upload, so existing authorized gh/git CLI handles APK releases. No new goal created; old goal status is not evidence this follow-up is incomplete.
