# Model provider badges

The model picker identifies each model family with its provider's official logo. These marks are publisher labels, not WisprLite branding or an endorsement. Settings controls continue to use WisprLite's custom Signal icons.

Unmodified 128 px PNGs were obtained from the providers' official GitHub organization avatars:

| Asset | Provider | Source |
| --- | --- | --- |
| `model_brand_openai.png` | OpenAI / Whisper | https://avatars.githubusercontent.com/u/14957082?v=4&s=128 |
| `model_brand_nvidia.png` | NVIDIA / Parakeet | https://avatars.githubusercontent.com/u/1728152?v=4&s=128 |
| `model_brand_moonshine.png` | Moonshine | https://avatars.githubusercontent.com/u/98664891?v=4&s=128 |

The marks remain the property of their respective owners. They are displayed without tinting, on a neutral badge background. They are not covered by WisprLite's source-code license.

## Small models and storage

The catalog adds Whisper Tiny multilingual and English packs from the official sherpa-onnx ASR model release. Their downloads are approximately 116 MB and 118 MB respectively; Moonshine Tiny and Parakeet 110M are also approximately 100 MB downloads. Download size and installed storage are shown separately. English-only packs are labelled explicitly.

Fresh Whisper installations retain the int8 encoder and decoder already preferred by the inference engine and omit redundant full-precision counterparts only after validating the complete int8 pair. Existing user installations are left intact. Temporary free-space checks still account for the full archive and staged extraction.

Small packs prioritize storage and speed. They do not establish reliable Yorùbá, Nigerian Pidgin or code-switching accuracy. Language coverage labels are not quality guarantees.

## Custom API compatibility

The transcription adapter accepts HTTPS Whisper-compatible multipart audio transcription endpoints with Bearer authentication, plus explicitly selected audio-chat requests for providers such as Google AI Studio. A base URL and API key alone cannot adapt arbitrary provider protocols. Saved profiles keep separate credentials and speech/cleanup selections.

Example: Groq base URL `https://api.groq.com/openai/v1`, speech model `whisper-large-v3-turbo`, and a Groq-issued API key. Other API formats require an adapter; newer APIs with different language parameters are not automatically compatible.

Sources: [sherpa-onnx model release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models), [Groq speech API](https://console.groq.com/docs/speech-to-text).
