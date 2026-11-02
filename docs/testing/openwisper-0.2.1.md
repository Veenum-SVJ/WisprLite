# OpenWisper 0.2.1 verification

Native mobile adaptation of desktop OpenWhispr light mode: reference neutral surfaces, blue controls, Yowza/Yowza Soft typography, Nucleo icons, grouped settings and compact transcript controls. The user-supplied logo is copied unchanged; the recording capsule stays translucent black with white voice-responsive rounded bars. OpenWisper spelling is used throughout the visible app. Application ID and sideload signing identity stay unchanged.

## Evidence

- Full debug/release assembly, both lint variants and 154 JVM tests passed after the final visual refinements.
- Pixel 6: all 43 integration checks passed for the complete logo/font update; after the last icon/spacing refinements, 12 focused screen, settings, appearance, model/history control and bubble preview/gesture checks passed.
- Pixel light/dark screen captures reviewed privately. User history/screenshots are excluded from public source.
- Optimized release installed on Pixel using install -r. Version 0.2.1/code4, normal launch, microphone permission and bound accessibility service verified. Other accessibility services preserved; owned screenshot caches and test helper removed. Existing providers, models and history retained.
- APK signature and 16 KiB ZIP alignment passed. Size: 37,545,488 bytes. SHA-256: 0208e8283966d95e3a497cee4fb465703abc71b1781a192b38888a4b4b4a6ccc.
- Five licensed font files are restored privately during GitHub Actions builds using encrypted secrets. They are excluded from public source. Without those files, local builds use the system fallback. CI for the font integration passed: https://github.com/Veenum-SVJ/WisprLite/actions/runs/41391154517 .

## Limits

The final 0.2.1 update is installed on Pixel only; Redmi was disconnected and still needs this APK. Mobile navigation and Android touch targets adapt the desktop layout rather than reproducing desktop dimensions. No new live cloud speech trial, model download or language accuracy claim is part of this visual update. Prior speech-recognition limitations remain. Exact user font/icon licences are supplied by the user; see NOTICE and font build documentation.
