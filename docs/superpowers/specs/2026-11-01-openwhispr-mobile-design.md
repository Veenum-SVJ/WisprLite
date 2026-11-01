# WisprLite: OpenWhispr-inspired mobile redesign

Status: proposed design for review; NOT implemented. Existing shipped baseline is 0.1.1, repository branch implementation, baseline commit 0c7da0c. This request supersedes the teal styling and donut-led Insights design, while preserving dictation and user data.

## Confirmed requirements

- Use the actual installed desktop OpenWhispr as the reference, adapted for Android rather than reproducing a desktop sidebar on a narrow screen.
- Logo: circular blue background, one white inner ring, many smaller white waveform dots. Same blue inside/outside the ring; no pale disc, chat tail, W or WL. Desktop asset confirms blue #2056DF.
- Keep Home, Insights, Dictionary and Settings. Dictionary gains a Snippets tab.
- Search dictated transcripts only. No Notes feature.
- Spoken snippet triggers expand saved text into the focused destination app.
- Tone/style is remembered per app, with a manual override.
- Home adopts grouped transcript rows/cards, timestamps, compact inline actions and search; omit calendar cards and promotional banners.
- Insights adopts metric cards and a speaking-activity heatmap, with useful numerical detail.
- Settings follows desktop categories: App (Preferences, Tone & Style), AI Models (Speech-to-Text, Language Models/Prompts), System (Privacy & Data, System/Updates).
- No accounts, billing, hotkeys, calendar, meetings, chat, integrations, note recording or voice-assistant extras.
- Keep native Kotlin Android Views, existing offline/cloud engines, saved provider profiles, English/Auto-detect, direct transcription, AI cleanup, tap/hold bubble gestures, free positioning, live size preview, optional audio retention and provider logos.
- Maintain lightweight/fast behavior; no WebView-based app shell, new UI framework, analytics or model downloads for cosmetics.

## Reference observations

Installed desktop OpenWhispr 1.11.0 was inspected read-only via Windows Computer Use. No settings or credentials were edited. Its logo SVG at resources/src/assets/logo.svg uses #2056DF. Do not read resources/.env.

Private screenshots are under .tools/openwhispr-reference/, ignored by Git:
- home-private.jpg: date-grouped transcript card rows, timestamps and global search.
- dictionary-private.jpg: Dictionary/Snippets segmented tabs; add input; searchable/sortable word list.
- snippets.jpg: spoken trigger -> saved text concept and primary Add action.
- insights-private.jpg: words, words/minute, dictations, streak, calendar heatmap.
- settings-preferences.jpg; settings-tone-style.jpg; settings-speech-cloud-private.jpg.
- settings-language-models-private.jpg; settings-prompts-private.jpg; settings-privacy-private.jpg; settings-system.jpg.

Some contain personal transcripts, dictionary entries, usage counts or masked key fragments. Keep originals local; do not commit, publish or send them to third-party design tools. Use synthetic content in public previews. Preserve upstream provenance/license; keep WisprLite branding.

## Mobile visual proposal — builder decisions, awaiting review

Use #2056DF for brand blue and the logo in both themes. Neutral light surfaces and charcoal dark surfaces replace teal-tinted panels. Retain system/light/dark modes and system fonts to avoid downloads. Use crisp 12-16dp rounded groups, fine dividers, compact metadata, 48dp touch controls and existing consistent Signal icons updated for blue. Do not copy the desktop sidebar or pack multiple columns into narrow phone cards.

Retain the existing right-side drawer: Home, Insights, Dictionary; Settings anchored below. Home header includes search; no extra top-level navigation destination for Snippets.

Logo proposal: fine white ring near the circle edge and 11 waveform columns with roughly 72 smaller dots. Native vector/Canvas implementation; validate at launcher, splash, header and smallest bubble sizes. The bubble retains voice-responsive dot motion, tap start/stop, hold-until-release and dragging. Do not reuse hold for a tone menu.

## Home and search

Show date headings with a shared surface containing separated transcript rows, timestamp, mode and compact inline Copy/Delete actions. Long text expands inline; retain original versus processed selection and explicit cleanup/retry actions. Preserve history pagination.

Search original, snippet-expanded and cleaned text; debounce on the worker, parameterize/escape literal SQL input, paginate results and show clear/no-results states. No network search. Avoid loading all history into memory. A focused search result expands the matching transcript. Typing speed and text retention behavior remain unchanged.

## Dictionary and snippets

Dictionary: two tabs, add-word field, searchable/sortable list, inline edit/delete and aliases in a small detail editor. Migrate the existing canonical/alias preference representation without losing entries. Dictionary supplies recognition hints; it does not train a model or guarantee recognition.

Snippet editor: unique trigger, saved plain text, enabled flag. Provide add/edit/delete, search, preview and empty state. Warn against common accidental triggers. Match normalized case/whitespace with Unicode-aware word boundaries; prefer longest non-overlapping trigger; expand each occurrence once; never recursively expand replacement text. Reject duplicate normalized triggers. URLs, multiline text, punctuation and Unicode must remain exact.

Processing order: recognition -> existing dictionary normalization -> snippet matching -> optional cleanup -> exact insertion. In Direct mode, expansion is local and sends no cleanup request. In AI Cleanup, protect matched snippet payloads with opaque placeholders, require the expected placeholder multiset/order, then restore exact saved text. On cleanup failure or token corruption, deliver the locally expanded transcription rather than inventing content. A disabled snippet never expands.

Keep recognized speech as original. Add optional expanded text to persisted transcripts; delivered text resolves cleaned then expanded then original. Spoken-word statistics count recognized original speech, not the length of an expanded address/template. Re-running cleanup uses the record's captured expansion, not today's potentially edited snippet definition.

## Tone and style

Categories mirror the reference: Personal messages, Work messages, Emails, Other apps. Proposed defaults: Casual, Formal, Formal, Neutral respectively. User can assign destination apps to categories and override a particular app's tone.

Capture the destination package when recording begins; do not accidentally classify WisprLite or the keyboard after overlay changes focus. Use Android launcher-intent app discovery with scoped package visibility, not QUERY_ALL_PACKAGES. Store assignments locally; do not send the app inventory or package identifier to a provider. Only the chosen tone instruction joins an opted-in cleanup request.

Manual override is proposed as a Home tone picker applying to the next dictation, then returning to app rules. This lifetime is a builder proposal, not a user-confirmed preference. Tone/style requires AI Cleanup; Direct mode never starts AI or enables cleanup automatically. Warn when cleanup is off. An app category is a formatting preference, not an instruction to change facts, names, numbers, intent or execute dictated commands. Preserve the original for comparison.

## Insights

Four compact metrics: recognized words, recognized words/minute, dictations and current streak, plus longest streak. Rate = recognized words / recorded speaking duration, labelled as an estimate from transcription; handle zero duration.

Speaking activity uses existing events, not transcript rows, so deleting text does not unexpectedly erase statistics. Group by local calendar date with correct timezone/DST boundaries. Default mobile heatmap: recent 12 weeks; allow range selection or horizontal scrolling for more history. Tap a day for date, recordings, words and duration; provide an accessible day-list equivalent and textual low/high legend. Do not rely on color alone. No leaderboard, account or sync.

Current streak counts consecutive active local dates ending today or yesterday, with the grace convention explained; longest streak spans retained events. Keep speaking duration and estimated typing time saved below the primary metrics with the estimate explanation. Empty and reset-statistics states must work. Do not fabricate metrics from retained text or model output length.

## Settings destinations

Settings becomes a compact category index with native detail pages and Back navigation, replacing the long stacked accordion page:

- App / Preferences: theme, English/Auto language, sound/vibration, bubble behavior/size with preview, app interface language where supported.
- App / Tone & Style: category tones, per-app assignments, enable switch and manual-override explanation.
- AI Models / Speech-to-Text: Dictation only; Local / Cloud / Custom-compatible endpoint options; saved provider selection, URL/key/model editor, model management with logos/sizes/download/delete. Never hide deletion for the selected model or force a local download for cloud mode. Existing self-hosted compatible URLs use the custom adapter; do not advertise arbitrary protocol support.
- AI Models / Language Models: Direct / Cleanup switch, independent cleanup provider/profile and model, custom instructions, Prompts with View/Edit/Reset-to-default. Preserve existing secure credentials and faithful cleanup guards. An optional synthetic prompt test must explicitly disclose the cloud request and never send personal history automatically.
- System / Privacy & Data: explain local history, cloud audio versus cloud cleanup text, retention, model/audio storage, encrypted backup/restore, clear history and reset statistics. Keep audio off by default. No new telemetry switches for a nonexistent analytics service.
- System / System: microphone/accessibility/battery/autostart status/setup, app version, diagnostics summary and explicit Check for Updates. Keep a compact status/setup banner at the top of the category index with a link here, preserving the user's need for visible readiness.

The new request supersedes the earlier detailed setup-first layout; the compact readiness banner retains its purpose without duplicating all setup controls. No accounts or hotkeys. Model controls have one home, inside Speech-to-Text.

Update check: explicit read-only GitHub release metadata request over HTTPS, cached/ETag-aware where practical, handles preview tags (the repository currently publishes prereleases), offline/rate-limit/error states and semantic versions. Show installed/latest and open the verified repository release page for user-managed install. No automatic background update, silent installer or new install-packages permission.

## Data, privacy and compatibility

Implement a real transactional SQLite v1 -> v2 upgrade: create snippets and add nullable expanded-text field; existing transcript/event IDs, times, raw/cleaned text and statistics remain intact. Existing onUpgrade rejects all version changes and MUST be replaced with a tested migration. Keep category/app-tone settings as a versioned local preference structure; destination package need not be persisted with history.

Extend encrypted backups compatibly for snippets/tone settings if included. Continue reading v1 backups; do not silently drop new data during round-trip. Import validation, duplicate IDs/triggers, atomic rollback and existing credential handling must remain safe. Never log keys, raw audio, personal transcripts, snippet payloads or app inventories. Do not clear main app data or uninstall it to resolve signing mismatch.

## Acceptance and delivery

- All current approved dictation behavior remains functional with cloud-only/no-local-model setup.
- Search persists, paginates and handles literal punctuation/Unicode without leaking history.
- Dictionary survives migration; snippet expansion works in Direct and Cleanup without recursive replacement or AI corruption.
- Per-app style captures the actual destination and changes only opted-in cleanup. Next-dictation override resets predictably.
- Heatmap/metrics match event fixtures, dates, empty states and timezone changes.
- Both themes, large fonts, keyboard/rotation, screen-reader labels and minimum bubble size are reviewed on phone layouts.
- Unit tests, appropriate device checks, debug/release lint/build, release signature/alignment and no-secret scan pass. Compare APK bytes against 36,215,895; do not promise the same size before measuring.
- Install data-preserving updates on authorized connected Pixel/Redmi; remove owned test fixtures and only the test package afterward; preserve models, keys and original OpenWhispr.
- Publish the authorized repository/source/APK only after verification, and record exact results and limitations. Prior web-insertion tests were intermittent; final 0.1.1 suite passed but no definitive cause was proven. Do not rewrite that history as fixed.

## Review boundary

This document and the design artifacts need user review before product-code changes. No new app implementation has started. This is a feature expansion (snippets, per-app style, search and heatmap), not solely a recolor. Use staged commits and update HANDOFF-OPENWHISPR-REDESIGN.md after each milestone. No completion-time guarantee based on a usage percentage.

Independent builder review completed: **nothing important left**. Reviewer identified no blocking unanswered design questions. Static proposal: ../../design/openwhispr-redesign/mobile-proposal.png; logo: ../../design/openwhispr-redesign/logo-proposal.svg. These are design artifacts with synthetic content, not screenshots of an implemented Android redesign.
