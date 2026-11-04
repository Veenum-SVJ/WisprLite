# OpenWisper 0.2.4 bubble and launch-screen update

## Requested behavior

The floating bubble is monochrome; the supplied launcher/header logo and notification icon remain unchanged. Tap recording opens a shorter capsule with continuous motion from the circle; hold recording uses a brighter outline and a small release cue. Processing animates the waveform inside the circular logo. The Settings size preview shows the same monochrome bubble.

The recording window is100dp at100% size (previously132dp); the visible surface is88dp versus the44dp idle circle. Both scale with the existing size slider, and the touch target stays at least48dp. A260ms expansion and220ms collapse animate only the drawable; window geometry changes at transition endpoints. System-disabled animations skip the morph and keep processing static.

## Visibility correction

Idle visibility uses a fresh visible IME window, or an active editor with a visible physical keyboard. A stale input connection, retained editor focus after soft-keyboard dismissal or a cached overlay-insets flag cannot alone keep it visible. Recording and processing stay reachable until finished. Repeated focus/content events no longer indefinitely restart the same pending300ms visibility change. Window checks poll every500ms; fade-out is140ms.

## Launch-screen correction

The image contains a gradient while the Android splash uses a solid blue, which exposed a square boundary. The splash now draws the white logo mark directly on the blue surface using transparent vector artwork. The original full-colour PNG is unchanged (SHA-2565473b51b51035311b62d3ec4509b4295e4782f5980138a9a91d13e8725280289). No change to launcher/header or notification branding.

## Verification checkpoint

Initial full build and both lints passed. Final JVM run:156 tests, zero failures. Redmi checks passed for retained-focus IME dismissal under repeated refreshes, ordinary Home visibility, tap/hold/drag/cancel, size preview, native and local WebView insertion, and Unicode clipboard fallback (six checks). Initial visual test caught a slight blue tint in the charcoal surface; production rendering was corrected to neutral RGB24/24/24. Final Redmi rendering and gesture checks passed after the tint correction. Reviewed actual rendered idle/tap/hold/processing captures and a cold-launch screenshot: the blue splash has no square logo boundary. Seven focused checks passed across two runs (gestures repeated on the final build). Final release build/lint and installation state are recorded in the handoff. No speech API trial or model download is needed for this update.
