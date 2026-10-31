# WisprLite product and technical specification

Date: 2026-10-30

Approved visual refinements on 2026-10-31 supersede the original experience styling below: soft teal Modern tool styling, circular dotted voice logo, custom Signal controls, a right-side drawer, inline history actions, language first in Settings, live bubble-size preview and free bubble placement with tap/hold recording. See [modern UI](../../design/modern-tool-ui.md), [bubble behavior](../../design/circular-dotted-bubble.md) and [model badges/storage](../../design/model-provider-badges.md). These refinements retain the functional and privacy requirements in this specification.

## Purpose
A minimal native Android dictation app for Pixel 6 and Redmi 12, adapted from EdiBianco/OpenWhispr. Android first; iOS and a desktop fork are outside this release. Use existing models, not model training.

## Experience
Warm off-white and charcoal styling with muted violet accents; light, dark, and system appearance. A top-left navigation drawer contains Home, Insights, Dictionary, with Settings anchored at its bottom. Retain the existing floating button and accessibility insertion, including clipboard fallback.

Home lists local text transcripts grouped by date, newest first. Open an entry to read, copy, or delete it. Store original transcription and optional cleaned output together. Display processing errors without inserting error messages into other apps. Cleanup failure preserves the original and offers retry or use-original.

Insights shows successful recording count, original-transcript word count, speaking duration, and estimated typing time saved. Count a successful dictation once; cleanup retries never increase counts. Estimate saved time as max(0, words / typingWordsPerMinute * 60 - speakingSeconds), with a default of 40 WPM editable in Settings. Aggregate today, last seven days, and all time. Deleting history does not silently reset aggregates; provide an explicit reset statistics control.

Dictionary stores canonical phrases and pronunciation aliases, reusing existing parsing and supported model hints. It does not train a model or guarantee recognition.

## Transcription and languages
Cloud settings expose provider base URL, API key, and model name. Support OpenAI-compatible multipart audio transcription, retaining Groq as a preset. Local mode uses sherpa-onnx and explicitly supported downloaded model packs. Never upload audio as an automatic fallback from local mode.

Offer Mixed/Auto, English, Yoruba, and Nigerian Pidgin profile. Pass English or Yoruba language codes only where supported. Mixed does not force a single language. Pidgin is a profile with hints and preservation instructions, not a claim of native support from every model. Preserve language switches and Yoruba characters and tone marks where recognized; never promise accuracy or manufacture missing speech. Evaluate Base and Small multilingual packs plus cloud Large-v3/Turbo before choosing defaults. Desktop Q5 downloads are not automatically compatible with sherpa-onnx.

## Cleanup
Direct and AI Cleanup are explicit modes, with Direct the initial default (builder decision). Separate cleanup base URL, API key, model, and custom instruction field. Preserve meaning, English/Yoruba/Pidgin switches, and Pidgin grammar; no implicit translation. Explain in settings that cloud cleanup sends transcript text even when speech recognition is local. No bundled local cleanup engine in this release.

## Settings
Transcription; offline model download/activate/delete and storage usage; cleanup; language; floating button position/size, sound and vibration; permission/background-service help; storage; appearance; backup; about/source attribution. Existing voice command transformations and in-app APK installation are omitted initially (builder decision); maintain service reliability.

## Storage and privacy
Text history on by default, retained until deleted; optional 7/30/90-day text expiry. Audio retention off by default: delete temporary audio after success, failure, or cancellation, plus startup cleanup for abandoned temporary files. Optional audio retention 1/7/30 days, default seven days when enabled (builder decisions). Retained audio stays app-private and has scheduled expiry, checked at startup and by daily WorkManager work. No network upload without explicit cloud mode or requested cleanup. Store keys with Android Keystore protection; exclude keys, audio, and history from automatic OS backups and logs. Explicit settings export excludes keys unless separately opted in and passphrase-encrypted. No analytics, accounts, sync, or backend service.

## Architecture
Retain native Kotlin, Android Views, Material components, OkHttp, sherpa-onnx, and existing minimum Android API 30 / arm64 foundation. SQLiteOpenHelper owns history and aggregate tables. Keep network requests, storage, cleanup, and UI separate. Do network/model/database work off the UI thread. Serialize dictation jobs and handle cancellation and duplicate taps. Store timestamps in UTC and group dates using device timezone.

Provider endpoints require HTTPS, reject credentials/query/fragment, and normalize documented API-root paths without double appending endpoints. No arbitrary model-file imports initially. Download validated known packs to temporary paths and atomically activate after successful extraction. Never render provider error bodies containing secrets.

## Lightweight requirements
Models are separate downloads; release shrinking and resource shrinking enabled after compatibility checks. Paginate history. No new cross-platform framework. Measure APK bytes, installed storage excluding models, idle memory, cold start, and recording-to-insertion latency on each device; publish measured results without invented targets. Accuracy evaluated separately for English, Yoruba, Pidgin, and mixed speech. Preserve upstream license and attribution, check dependency licenses before public distribution.

## Acceptance
Build, lint, and meaningful unit/instrumented checks pass. Verify direct and cleanup success/failure, custom provider routing, dictionary persistence, history, exact-once statistics, retention expiry, no-audio default, offline airplane mode, model download failure, permission denial, background recovery, and cross-app insertion on connected devices. Test mixed speech for preservation with a small consented corpus; do not commit personal recordings or API keys. Lack of device/API credentials is reported as unverified, never a pass. Produce an APK and test report; publish source to the authorized public WisprLite repository after authentication is available.
