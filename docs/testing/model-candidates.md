# Accuracy-first speech model shortlist

Checked against publisher documentation on 2026-10-31. The user removed the approximately 100 MB constraint when evaluating recognition quality. None of the candidates below has passed a WisprLite user-voice acceptance trial. Published weight-file sizes are not Android pack sizes or RAM requirements.

| Candidate | Evidence and size | Next validation |
| --- | --- | --- |
| LyngualLabs Whisper Small Yorùbá | Publisher specifically targets Yorùbá–English code-switching; published float32 weights approximately967 MB | Export/validate an Android-compatible pack; test real mixed speech and tone marks. No specific Pidgin claim. |
| Axiveri NaijaVox2.0 | Publisher lists Yorùbá, Nigerian Pidgin and Nigerian English training and mixed-language robustness; merged weights3.09 GB | Validate extended tokenizer/language tokens and runtime conversion before phone use. Publisher benchmarks are not our results. |
| Intron Sahara2.5 | Hosted African speech service with explicit Yorùbá/Pidgin coverage and language-mixing support | Separate API adapter and user-configured credentials; compare real consented phrases with cleanup disabled. No public phone pack verified. |
| OpenAI Whisper Large-v3 / Turbo | Established multilingual candidates; Turbo published weights1.62 GB; Groq hosts both | Groq trial works with existing multipart adapter once configured. Local use needs compatible conversion/runtime and memory/latency checks. No guarantee of correct Yorùbá tones or Pidgin. |
| Audio-capable Gemma variants | Gemma3n and Gemma4 variants support audio, but general multilingual text capability does not establish Yorùbá/Pidgin ASR quality | Different runtime integration; verify actual audio coverage, model artifacts, memory and user-voice accuracy before recommending. |
| NVIDIA Parakeet0.6B-v3 | Official language list is25 European languages, excluding Yorùbá and a distinct Nigerian Pidgin language | Useful English option; not a solution for the requested Yorùbá recognition. |

Sources: [LyngualLabs card](https://huggingface.co/LyngualLabs/whisper-small-yoruba), [weight files](https://huggingface.co/LyngualLabs/whisper-small-yoruba/tree/main), [NaijaVox card](https://huggingface.co/Axiveri/NaijaVox-2.0), [weight files](https://huggingface.co/Axiveri/NaijaVox-2.0/tree/main), [Sahara2.5](https://www.intron.io/), [Whisper Turbo weights](https://huggingface.co/openai/whisper-large-v3-turbo/tree/main), [Groq speech API](https://console.groq.com/docs/speech-to-text), [Gemma3n](https://ai.google.dev/gemma/docs/gemma-3n), [Gemma4](https://ai.google.dev/gemma/docs/core/model_card_4), [Parakeet card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3).

## Provider settings and saved profiles

The current app holds one speech configuration and a separate cleanup configuration. It does not yet have a saved library of multiple speech-provider profiles. Replacing the speech URL/model/key replaces that active speech configuration; keys from different providers must not be combined in one field.

Groq's multipart speech endpoint matches the existing adapter. Google AI Studio issues Gemini credentials: Google's documented audio input uses its native API or audio content in chat completions, so the current multipart transcription adapter cannot be assumed compatible. Google's OpenAI-compatible chat endpoint is relevant to the existing text cleanup adapter, but has not been live-tested here.

A future saved-profile design should keep a distinct encrypted credential for each named provider, allow explicit speech and cleanup selections, and never silently switch providers after an error. This is a proposed follow-up, not an implemented feature or automatic upload authorization.

Google source: [OpenAI compatibility and audio input](https://ai.google.dev/gemini-api/docs/openai?hl=en).
