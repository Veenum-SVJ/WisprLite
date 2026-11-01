# Public speech evaluation — 2026-11-01

These two clips supplement the user's trials; they are not a representative benchmark, a mixed-speech evaluation, or acceptance for the user's voice. No model was trained. Audio and generated transcripts remain in ignored local test artifacts, outside the app release and public Git repository.

## Sources and pinned fixtures

Both publishers declare CC BY 4.0. Preserve their attribution when reusing the clips:

- [Google FLEURS](https://huggingface.co/datasets/google/fleurs), revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, Yorùbá `yo_ng`, test ID 1836, file `10004614807418206077.wav`. Reference is field 2 of the matching `data/yo_ng/test.tsv` row, preserving its original writing. The audio is the matching member of `data/yo_ng/audio/test.tar.gz`. Only the first 4 MiB of that archive was retrieved, then the matching WAV read with streaming `tarfile.extractfile`; no archive paths were extracted to the filesystem. The Dataset Viewer rows endpoint returned HTTP 500, so it was not used as a reference.
- [Michael Odafe, pidgin-asr-combined](https://huggingface.co/datasets/michaelodafe/pidgin-asr-combined), revision `f85a9680f9e148979b79a072a61d1a02a5325fbb`, default configuration, test row 0. This is an aggregate of publicly available Pidgin sources; see its card for original-source attribution. The `text` field supplies the reference. Dataset revision was checked before retrieving its public audio URL.

The Yorùbá clip was converted with FFmpeg 9.0.2 to mono 16 kHz PCM16 WAV; the Pidgin source already had that format. The Android evaluation rejects any bytes differing from these exact files, including a changed WAV header. References were not sent as prompts. Only the production language profile/hint and an empty dictionary were used.

| Fixture | Duration | Reference words | Uploaded WAV SHA256 |
|---|---:|---:|---|
| yoruba.wav | 26.280 s | 39 | `f99f74cd579a9e63dfdde864856b6111426019dbd345557584c8519bc95306bb` |
| pidgin.wav | 5.808 s | 14 | `35d76258b77a840c166d698696d08144e2e1ac9ca1f28c738a1b42f636ff47e4` |

## Method

`PublicCorpusEvaluationTest` is excluded from the default device script. It requires an explicit provider and confirmation argument, snapshots a unique saved profile without activating it, validates both audio hashes before any request, and records results in app-private cache. It never reads personal history, personal audio or the dictionary. Keys remain inside Android. An instrumentation OK means measurement completed, including recording API failures; it does not mean speech accuracy passed.

Provision the exact files under `cache/public-corpus-eval/` in the debug app. Run the test with `publicCorpusProvider=groq` and `confirmedCloudTrial=public_corpus_groq`, or `google` and `public_corpus_google`. These are live, potentially billable API requests. Result files are `groq-results.json` and `google-results.json` in the same private cache directory. They contain only these public clips' outputs, model, latency and redacted failure status. No profiles or settings are modified.

Score the retrieved JSON using `python scripts/score-public-corpus.py references.json provider-results.json`. The reference JSON maps each fixture filename to the exact publisher-supplied reference text. The scorer lowercases, normalizes NFC, replaces punctuation with spaces, and collapses whitespace. Word error rate (WER) is Levenshtein edits divided by reference words; character error rate includes normalized spaces. Tone/underdot marks remain in the main score. A separate mark-folded WER removes combining marks as a diagnostic, never as a replacement for the required writing. Failed or empty responses are unscored. WER may exceed 100%; values are not clipped.

Scorer checks covered known substitution, insertion and deletion counts, canonical-equivalent Yorùbá strings, and the distinction between marked and unmarked names. The test APK and debug lint passed before the live runs. No production app source changed for these measurements.

## Observed results on Redmi 12

| Provider/model | Clip | Request latency | Word edits | WER | Mark-folded WER | Result |
|---|---|---:|---:|---:|---:|---|
| Groq / whisper-large-v3 | Yorùbá | 1,715 ms | 37 / 39 | 94.87% | 69.23% | Returned text; poor match |
| Groq / whisper-large-v3 | Pidgin | 517 ms | 6 / 14 | 42.86% | 42.86% | Returned text; substantial errors |
| Groq / whisper-large-v3-turbo | Yorùbá | 2,130 ms | 36 / 39 | 92.31% | 74.36% | Returned text; poor match |
| Groq / whisper-large-v3-turbo | Pidgin | 610 ms | 5 / 14 | 35.71% | 35.71% | Returned text; substantial errors |
| Google / gemini-3.8-flash | Yorùbá | 62,085 ms | — | — | — | Redacted request failure; unscored |
| Google / gemini-3.8-flash | Pidgin | 60,064 ms | — | — | — | Redacted request failure; unscored |

Groq's speed is not evidence of sufficient accuracy. These measurements support the user's earlier failure report and do not justify recommending generic Large-v3 as a verified Yorùbá solution. Google's two failures provide no accuracy comparison. Other utterances, accents, recording conditions and model versions may differ.

The separately required Turbo comparison uses `groqTurbo=true` and confirmation `public_corpus_groq_turbo`. It overrides only the test request model, never saved profiles or active selection, and writes `groq_turbo-results.json`. Test-only APK/debug lint passed in 59s; the two-clip measurement completed in 2.898s. Turbo did not establish adequate Yorùbá/Pidgin accuracy on these cases. This completes the named Large-v3/Turbo comparison, not mixed-speech/user-voice acceptance. Private evidence: `.tools/turbo-corpus-build.log`, `.tools/turbo-corpus-device.log` and scored JSON under `.tools/corpus-eval/`.

Google documents [`reasoning_effort=low`](https://ai.google.dev/gemini-api/docs/openai) for its compatible API. A separate explicit experiment uses `googleReasoningLow=true` and confirmation `public_corpus_google_low`, writing `google_low-results.json`; it changes only the test request, not production behavior or saved profiles. Its outcome must be reported separately. Do not infer that default reasoning caused these failures without comparative evidence.

The low-reasoning comparison also failed: Yorùbá returned HTTP 503 after 9,879 ms; Pidgin returned a redacted request failure after 60,485 ms. Neither was scored. This does not establish a latency or accuracy benefit, so no production reasoning setting was changed. Test-only rebuild/debug lint passed in 54 seconds; the measurement run completed in 70.523 seconds. An earlier test-only build/debug lint passed in 1m2s; Groq measurement completed in 2.389s and default Google measurement in 122.288s. Existing production checks remain 122 unit tests and 29 Redmi checks at revision 3d92e20, with both CI workflows successful.

Private logs: `.tools/public-corpus-build.log`, `.tools/public-corpus-groq-device.log`, `.tools/public-corpus-google-device.log`, `.tools/public-corpus-low-build.log`, `.tools/public-corpus-google-low-device.log`; measurement/reference JSON under `.tools/corpus-eval/`.
