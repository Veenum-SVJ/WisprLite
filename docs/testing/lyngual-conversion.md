# Existing Yorùbá model: conversion and evaluation

This investigation evaluates an existing model; it does not train one. It has not added a downloadable catalog entry, activated a new model, or replaced an installed phone model. Accuracy acceptance remains open.

## Provenance

- Model: [LyngualLabs/whisper-small-yoruba](https://huggingface.co/LyngualLabs/whisper-small-yoruba), declared Apache-2.0, revision `ac05aac79a0a6fa522f75055a6af1523eb7952b6`.
- Original safetensors: 966,995,080 bytes, verified publisher SHA256 `98b4e0a8e0c083c899c526f60b6c83bda6906eebf3c9f01080f9db8e84afe6fe`.
- Upstream Whisper architecture/tokenizer: [OpenAI Whisper](https://github.com/openai/whisper), revision `86098128c0b4f24f0e2aa2994de830614b474227`.
- Cached ONNX wrappers: [sherpa-onnx exporter](https://github.com/k2-fsa/sherpa-onnx/blob/7be9a38e275ece9ee9a9277cc78295f2cbfd6673/scripts/whisper/export-onnx.py), revision `7be9a38e275ece9ee9a9277cc78295f2cbfd6673`.
- Workstation environment: isolated Python 3.14, PyTorch 2.15.0+cpu, Transformers 5.22.1, safetensors 0.8.0, ONNX 1.23.2, ONNX Runtime 1.31.1. These are research dependencies, not additions to the APK.

Remote model code was not enabled, and pickle training artifacts were not loaded. The safetensors and processor files were pinned. Architecture and export source were inspected before executing their model/wrapper definitions; generic download/main entry points were not called. Research assets remain under ignored `.tools/model-research/`.

## Compatibility checks

All 479 weight names/shapes map without collisions to the upstream encoder/decoder. The reverse mapping removes the `model.` prefix, maps `layers` to `blocks`, attention q/k/v/out projections to query/key/value/out, self/cross attention layer norms to their upstream names, and fc1/fc2 to MLP layers 0/2. Final encoder/decoder layer norms and positional/token embeddings use their corresponding upstream names. Strict mapped loading reported no missing or unexpected weights.

The Hugging Face model exposes 480 state entries because the output projection shares the decoder token-embedding tensor. The file stores that tensor once. Shared-parameter identity and all shapes were verified; no randomly initialized output head was accepted. Processor initialization verified 80 mel features, 16 kHz sampling and 51,865 tokenizer entries.

All 50,257 regular token byte IDs matched the pinned OpenAI tokenizer. The special-token name alias at ID 50362 is `<|nocaptions|>` in the HF assets and `<|nospeech|>` in OpenAI. Conversion keeps that numeric ID and uses standard OpenAI no-speech metadata; it does not renumber the vocabulary.

On the two [pinned public clips](public-corpus-evaluation.md), encoder and full teacher-forced decoder logits stayed within explicit numerical tolerances (`atol=2e-4, rtol=1e-3` for encoder; `atol=2e-3, rtol=1e-3` for logits). Maximum absolute encoder differences were 0.000620/0.000425; decoder logits 0.000061/0.000031. Raw argmax choices matched across 132/20 tested positions. Separate cached greedy decoding, using the pinned suppression rules and explicit yo/en prefixes, terminated and matched every generated token and decoded text for both clips.

The floating-point ONNX export uses opset 17, explicit legacy export (`dynamo=False`), upstream cached wrappers, and runtime metadata. The research harness initially mixed inference-only tensors with autograd tracing; decoder tracing failed before producing a decoder. It was corrected to use no-grad tensors. The existing encoder was structurally checked and reused, then decoder export completed. Both files passed ONNX structural validation. Encoder/cached-decoder numeric comparison (`atol=2e-3, rtol=1e-3`) and full independent ONNX decoding passed on both clips, with exact baseline tokens/text. This validates those cases, not every dynamic input length or Android execution.

Int8 conversion used the upstream method: dynamic QInt8 quantization of MatMul weights. Structural checks passed, but tokens changed, so it is not labeled exact parity. Encoder/decoder sizes are 112,413,545 and 262,200,764 bytes (374,614,309 combined, plus tokens). Float sizes are 409,494,289 and 559,086,847 bytes.

## Accuracy observations

Same source references and scorer as the public-corpus evaluation. These are two cases, not a representative benchmark or personal-voice acceptance. Pidgin has no separate model-language claim; the test used the app's English-based Pidgin routing. No cleanup, reference-text prompt, translation or training was applied.

| Execution | Yorùbá word edits / WER | Yorùbá mark-folded WER | Pidgin word edits / WER |
|---|---:|---:|---:|
| Original HF float, workstation CPU | 26/39 · 66.67% | 51.28% | 7/14 · 50.00% |
| Int8 ONNX, workstation CPU | 24/39 · 61.54% | 43.59% | 8/14 · 57.14% |

Float ONNX exactly matched the HF float tokens/text. Original HF decoding took 8,480/4,286 ms for Yorùbá/Pidgin; float ONNX 22,727/5,665 ms; int8 ONNX 18,330/3,915 ms. All workstation runs used four CPU threads. These single samples are not phone timings or a fair cloud-versus-device latency comparison. Int8 improved word edits on one clip while worsening Pidgin and Yorùbá character error; it does not establish a general accuracy benefit.

## Android gate

`LyngualNativeEvaluationTest` is an explicit opt-in measurement outside the default device suite. It hash-checks two int8 graphs, tokens and the public WAVs before loading the existing JNI runtime from a separate private cache directory. It does not alter preferences, history, catalog files or active model selection. It records load/decode latency, process PSS after load, coarse thermal status and public-clip output. Measurement completion is distinct from accuracy acceptance. Native results are recorded after the actual run; source availability is not a pass.

On the authorized Redmi 12, the test-only APK/debug lint build passed in 1m4s and the native evaluation completed in 91.825 seconds. Both clips produced text through the existing JNI library. These are compatibility results, not accuracy acceptance:

| Native int8 measurement | Yorùbá | Pidgin |
|---|---:|---:|
| Clip duration | 26.280 s | 5.808 s |
| Model initialization | 8,480 ms | 9,533 ms |
| Decode | 60,845 ms | 10,943 ms |
| Process PSS after load | 481,378 KB | 480,917 KB |
| Word edits / WER | 28/39 · 71.79% | 6/14 · 42.86% |
| Mark-folded WER | 53.85% | 42.86% |
| Character error rate | 21.62% | 24.29% |
| Android thermal-status code, before/after | 2 → 2 | 2 → 2 |

PSS includes the process/framework and is not isolated model memory or peak decoding memory. Thermal status is a coarse OS signal, not a temperature measurement; code 2 was already present before each trial. These are single-run observations. Native int8 output differs from the workstation output, so no cross-runtime text-parity claim is made. Initialization was measured separately for each explicit language configuration.

The candidate is not recommended as a verified accuracy/speed solution on this evidence. It remains outside the catalog; generated packs were not published and existing installed models were preserved. Temporary research model/audio files are removed from the phone after results are retrieved. Further Yorùbá/Pidgin/mixed evaluation and the user's voice trial would be needed before any accuracy claim. Neither the publisher's self-reported metric nor successful conversion resolves the observed recognition errors.

Private evidence: `hf-metadata.log`, `hf-baseline.log`, `independent-decode-parity.log`, `float-export-resume.log`, `float-onnx-parity.log`, `int8-quantization.log`, `int8-evaluation.log` and their JSON reports under `.tools/model-research/`; `.tools/lyngual-native-build.log`, `.tools/lyngual-native-device.log`; public-clip outputs under `.tools/corpus-eval/`.
