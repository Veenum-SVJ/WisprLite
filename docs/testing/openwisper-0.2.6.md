# OpenWisper0.2.6 recording modes and Pixel loading

## Behavior

Tap recording uses the existing compact waveform capsule. Hold recording stays circular with a live waveform, a double ring and a bold HOLD indicator. The touch target retains the existing minimum and size slider. Releasing ends hold recording; tap recording requires a second tap. Processing stays inside the circular logo.

## Pixel root cause

Pixel6 had animator_duration_scale, transition_animation_scale and window_animation_scale all set to0. The previous drawable gated functional loading feedback on ValueAnimator.areAnimatorsEnabled(), making its loading waveform static. The old rendering test skipped its movement assertion when animations were disabled, missing this case. Loading now schedules its own frames during processing, independently of transition-duration settings. Decorative expansion/collapse still follows the system animator preference. No phone animation setting is changed.

## Evidence checkpoint

- Reproduced on the actual Pixel with the old0.2.5 implementation: the visible processing drawable scheduled0 frames in the new native-view regression test. The old hold mode also failed the new circular-window assertion (width198 versus height110 at the saved user size).
- Fixed build passed160 JVM tests, debug/release assembly and both lint variants.
- Pixel, with animator_duration_scale still0: eight focused checks passed—live loading-frame scheduling, rendered state shapes/movement, keyboard visibility, tap/hold/drag/cancel, size preview, native insertion, local WebView insertion and Unicode clipboard fallback.
- Visual review prompted a final adjustment to keep HOLD clear of the circular border. Final full build/both lints/160 JVM tests passed. Final Pixel loading, render and gesture checks passed again after that adjustment. A test-only startup race (service instance published before overlay creation) was fixed by waiting for both before checking frames; its final rerun passed.

Tests use synthetic processing and interrupted recordings rather than uploading speech. No API keys, provider profiles or models are changed. Private evidence is under ignored .tools/clear-modes-* and .tools/clear-recording-modes-*. The supplied launcher/header logo and notification icon remain unchanged. Final optimized installation, APK digest and publication are recorded in the latest handoff.


Optimized0.2.6/code9 installed on Pixel with install-r; app launch, microphone permission, own service binding and installed APK digest verified. Other enabled accessibility services retained. Test helper and owned screenshot caches removed. No main app data clear. APK37,561,928 bytes; SHA-256260145823f13eaf7bb0f18a302affcd32d268a2e6377d6dc3ef34c8ed6868e28. Signature and16 KiB ZIP alignment passed. Redmi is disconnected and still0.2.5; it can fetch this release through the in-app updater once published.
