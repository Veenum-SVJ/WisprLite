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
