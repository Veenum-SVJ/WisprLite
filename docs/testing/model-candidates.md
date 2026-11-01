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

The user subsequently approved saved provider profiles. The app now keeps named profiles with separate encrypted keys and explicit speech/cleanup selections, alongside manual settings. Keys from different providers must not be combined in one field.

Groq uses multipart speech. Google AI Studio issues Gemini credentials and its preset selects the explicitly implemented audio-chat request format. Google's compatible chat endpoint also serves text cleanup. Subsequent live trials are recorded in the [public-corpus evaluation](public-corpus-evaluation.md): Groq Large-v3 returned text with substantial Yorùbá/Pidgin errors; Google requests failed, including a separate low-reasoning experiment. A prior Google synthetic diagnostic returned a compatible response, but this does not establish reliable recognition.

Saved profiles do not enable cloud mode or cleanup and never silently switch providers after an error. See [profile behavior](../design/saved-provider-profiles.md). Explicitly authorized synthetic and public-corpus live requests are documented separately; no personal recordings or keys were published.

Google source: [OpenAI compatibility and audio input](https://ai.google.dev/gemini-api/docs/openai?hl=en).

## LyngualLabs conversion investigation — 2026-11-01

The existing Apache-2.0 model is pinned to revision `ac05aac79a0a6fa522f75055a6af1523eb7952b6`. Its 966,995,080-byte safetensors file finished downloading into ignored research storage and matched the publisher's SHA256 `98b4e0a8e0c083c899c526f60b6c83bda6906eebf3c9f01080f9db8e84afe6fe`. This is an existing fine-tune, not a training run, and it has not replaced any phone model.

A metadata-only check mapped all 479 tensor names and shapes to encoder/decoder modules from pinned OpenAI Whisper revision `86098128c0b4f24f0e2aa2994de830614b474227`, with no missing names, unexpected names, shape mismatches or mapping collisions. The check instantiated only meta-device modules; it did not load weights or run recognition. Earlier tokenizer comparison matched all 50,257 regular token IDs and identified one special-token name alias at ID 50362 (`<|nospeech|>` versus `<|nocaptions|>`), which still requires deliberate handling and inference parity checks.

CPU-only PyTorch 2.15.0+cpu and evaluation/export dependencies were installed in the isolated ignored Python 3.14 environment. These are workstation research dependencies, not Android app dependencies. Strict weight loading/tied-output verification, HF/OpenAI numerical and independent decoded-text parity, and float ONNX numerical/full-decoding parity subsequently passed on the two pinned public clips. Int8 structural checks passed but output changed. Original HF float had 66.67% Yorùbá WER / 50.00% Pidgin WER; workstation int8 had 61.54% / 57.14%. Redmi native int8 ran successfully but had 71.79% / 42.86%, with 60.845s / 10.943s decode time. Conversion/runtime compatibility does not resolve quality. No model pack or catalog entry has been added; no installed model was replaced. See the [conversion investigation](lyngual-conversion.md) for provenance, methods, limits and measurements.

The initial Transformers metadata/tokenizer command was not executed because automatic approval review reported a usage-limit failure, explicitly not an unsafe-action determination. A subsequent request through normal review was accepted and the check completed; no bypass or redownload was used. Private evidence remains under `.tools/model-research/`.
