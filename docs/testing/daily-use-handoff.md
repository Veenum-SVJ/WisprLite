# Daily-use handoff — 2026-11-01

The user revised acceptance to English-first dictation, with English and Auto-detect as the only language choices. Explicit Yoruba and Nigerian Pidgin profiles are removed. Auto-detect follows the selected model's capabilities. Historical language failures remain documented; no general accuracy guarantee is made. The fixed mixed voice trial preserved its requested English clause in both original and cleaned output; further personal Yoruba/Pidgin trials were explicitly withdrawn.

## Build and validation

- 122 unit tests passed; debug/release lint, debug/test APK and optimized release builds passed.
- Final Pixel 6 regression: 28 device checks passed. A separate synthetic offline English check using its installed Whisper Base passed (3.704 seconds including test setup).
- Optimized daily-use APK: 36,215,895 bytes (36.2 MB decimal; 34.5 MiB), separate from downloaded models.
- SHA-256: `7e0aa72b7284f3e29e099fe11a08bfa628644b2de830cefd8ee3f5d7c58575c3`.
- Pixel updated without clearing data; English selected, local Whisper Base preserved, test app and identified test fixtures removed. No saved provider keys were present on Pixel; none were copied from the other phone.
- The optimized sideload artifact retains the existing installation's signing identity so it updates without data loss; signing files are never distributed. It is non-debuggable and code/resource shrinking is enabled.

## Model and provider scope

The 12-pack catalog includes small English/multilingual options and larger official Whisper Medium, Large-v2, Large-v3 and Turbo exports. Largest listed download is approximately 1.93 GB; extraction requires additional free space. Native Whisper reads feature dimensions from model metadata, including 128-bin v3 exports. Newly listed large packs have not been benchmarked on these phones. No large models were downloaded automatically. Arbitrary GGUF/Q5 imports are not supported.

Multiple named provider profiles keep encrypted keys separately, with explicit transcription and cleanup selections. Base URLs must implement the chosen request format; any random API URL is not automatically compatible. Groq multipart speech and Google AI Studio audio-chat presets are available. Provider availability, quota, latency and language quality depend on the selected service/model. Cloud cleanup sends text even with offline transcription; Direct mode avoids that cleanup request.

## Daily use

Tap the floating bubble to start and tap again to stop; hold to record until release; drag to reposition. Language is first in Settings. Home keeps text history with inline actions; audio saving defaults off. Settings contains model download/delete/select controls, official provider badges, saved providers, live bubble-size preview, appearance and retention controls. On Redmi, keep Background autostart enabled as well as the standard battery exemption.

Only owned test fixtures and the test package are removed during handoff. Personal history, providers, model downloads and the original OpenWhispr app remain intact. Historical research documents describe prior experiments, not active release requirements.

## Redmi final handoff

The final code passed all 28 standard device checks on Redmi 12 using the foreground-aware harness. A preliminary direct invocation did not complete correctly under MIUI foreground restrictions and is not counted as a pass. The optimized APK then installed without data loss and cold-launched successfully (3,211 ms reported by Android). WisprLite's Accessibility Service was rebound and confirmed bound with no crashed services; other enabled services were preserved. The test package was uninstalled.

Cleanup removed owned internal fixtures, screenshots, synthetic audio, test UI XML/video, picker-export fixture and the single guarded fixed-phrase trial row. Two saved provider profiles and four installed models (Whisper Base, Tiny multilingual, Tiny English and Small) were verified unchanged before the optimized update. English is selected; existing cloud transcription/cleanup choices remain configured. The disconnected Pixel retains its completed optimized installation and earlier recorded handoff checks.

The APK signature and 16 KiB ZIP alignment verify successfully. Unit/lint/build checks passed locally. GitHub's final implementation secret scan passed; workflow results are independently visible in Actions. New larger catalog packs remain unbenchmarked on these phones. Google service availability and historical Yoruba/Pidgin accuracy failures are retained as limitations rather than hidden or claimed fixed.

## 0.1.1 follow-up

The update adds setup-first, collapsible Settings; a single inline Local models disclosure; Delete for every installed model including the selected one; refreshed model storage; and native Insights charts with numerical details. See the design documents for behavior and the recorded intermittent web-test limitation.

Final production build passed 122 unit tests, debug/release lint and debug/test/release APK builds in 3m57s. Targeted Pixel layout/model/chart checks and light/dark appearance passed, followed by a full 28-check Pixel run. The initial language UI test assumed the previous layout and was updated for the user-requested order. Two intermediate broad runs failed the web field's text-match assertion despite paste acknowledgement; focused cold/native/web checks and the final full run passed. Do not describe the cause as proven or the intermittent observation as definitively fixed.

Optimized APK: 36,215,895 bytes (36.2 MB decimal), SHA-256 `f7ac3ce508d994eb1080c97d73d6ffa9f14c7567311ecedf34787f3350f93bce`. Signature and 16 KiB ZIP alignment verify. No new UI/chart dependency or model download was introduced.

Pixel now runs non-debuggable 0.1.1 and cold-launched in 918 ms. Its accessibility service is confirmed bound with no crashed services. The test helper and identified fixtures/picker exports were removed. Its two saved provider profiles, cloud-only choice and zero installed local packs were verified preserved before the optimized update. The handoff preserved the current model storage and provider profiles; credentials were not transferred between phones.

Redmi also received the optimized 0.1.1 APK via a data-preserving update. Android reported a successful warm launch in 3,074 ms. Its Accessibility Service is confirmed bound, no crashed services are reported, the test package is absent, and Xiaomi Background autostart remains allowed. No local models were downloaded or removed during this handoff. Both phones now run non-debuggable 0.1.1. The public release asset digest matches the verified local APK; release commit 787002f passed GitHub Android checks and secret scan. A tag-triggered duplicate build may finish separately; the completed branch checks cover the same commit.
