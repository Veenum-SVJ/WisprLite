# Device validation — work in progress

## Redmi 12
- USB device model: 23053RN02A, Android 15; ADB authorized.
- Tested initial integration commit: 991c0d7, CI run 41133306324.
- Debug APK size: 51,907,857 bytes; models not bundled.
- Installed alongside the original OpenWhispr app using a distinct application ID.
- Cold launch reached Home without an app crash; first launch included notification permission and fresh-install setup.
- Drawer showed Home, Insights, Dictionary, and Settings anchored at bottom.
- Initial foreground idle measurement: PSS 105,215 KB, RSS 230,667 KB. This is a debug-build snapshot, not a release idle/background benchmark.
- Found stale welcome branding; corrected source for next build.

## Remaining validation
Latest Settings/theme revision; instrumentation database checks; release shrinking and install; microphone/accessibility setup; local and cloud dictation; English/Yoruba/Pidgin/mixed accuracy; audio expiry; background recovery; rotation/large font; Pixel 6 comparison. Do not treat these as passed.

## Stable-key local debug checks
- Installed WisprLite and its test APK on the Redmi after approved removal of the temporary-key installation.
- AndroidJUnitRunner: OK (3 tests). Verified isolated transcription/Groq credentials, Unicode text persistence, exact-once counters across cleanup and history deletion, timestamp-tie pagination, and expiry independent of counters.
- CI run 41137216341 for 66e4986 passed the expanded unit/debug/instrumentation-assembly/lint/release checks. Physical instrumentation does not by itself verify microphone capture, model accuracy, or cloud providers.

## Offline engine regression and result
- First real model run correctly exposed the inherited filename mismatch: Base uses base-encoder/base-decoder/base-tokens, not unprefixed names. Added fixtures and fixed detection; also reject catalog/architecture mismatches.
- Rerun on Redmi: OK (1 test), real sherpa-onnx multilingual Whisper Base decoding a synthetic English WAV.
- Fixture: 8.055 seconds. Model loading plus processing: 8,131 ms. Output: 'Hello Whisper Light. This is a short English dictation test. Please send the document tomorrow morning.'
- First installation/extraction plus test took 226.465 seconds; this is not steady-state inference latency.
- Subsequent Activity launch reproduced a missing keyRowSub initialization. Restored the Groq cleanup-key row and added a startup/drawer instrumentation regression; verification pending.
- No Yoruba, Pidgin, mixed-language accuracy or Pixel 6 result is implied by this English synthetic test.

## Current verification (2026-10-31)
- Source 604fa12 plus test-only permission-setup simplification: full local unit/debug/test-APK/lint/debug-and-release build PASS (3m52s), 103 unit tests, zero failures.
- Redmi startup/drawer/back regression: OK (1 test). MIUI blocked the instrumentation background launch until ADB brought the matching MAIN/LAUNCHER intent with flags 0x10008000 to the foreground. No new startup exception occurred.
- Separate rerun: HistoryRepositoryTest, CredentialIsolationTest and OfflineSpeechSmokeTest: OK (4 tests), 7.696s total. Includes real Base model inference; does not test the physical microphone.
- Production-signed release APK: 36,044,061 bytes, signature verified by apksigner. SHA-256: 4F503BB655ACC22D2453A986107B0602FB1118ED3142D14D824947C0556E162F.
- Release certificate SHA-256: a4d82dabfecb078968554f19d625b95bc1cc3499b3289189617cffff17e7e13b. Private signing material remains ignored locally.
- Physical microphone/insertion attempt from the previous session has no surviving result artifact and is NOT counted as passed. Cross-app, retention, rotation/large-font, release runtime and language/device coverage remain open.
- Test prerequisites: grant RECORD_AUDIO and POST_NOTIFICATIONS using ADB before StartupNavigationTest. This test no longer executes redundant shell permission commands inside its own instrumentation process.

- Extended startup regression to navigate to Dictionary, enter unsaved Yoruba Unicode text, recreate the Activity, assert destination and exact draft survive, and return Home with Back. Redmi result: OK (1 test), 20.019s; test APK rebuilt before execution. No dictionary changes were saved.
- User supplied Pidgin/Yoruba accuracy phrases; recorded in language-corpus.md as references only, with real pronunciation testing still pending.

## Microphone, cross-app and backup integration
- ServiceLifecycleIntegrationTest on Redmi: OK (1 test), 11.34s. Test invokes the production start-recording path, verifies Android AudioRecord is recording, interrupts after 250ms, asserts recorder/PCM references cleared and state IDLE, and confirms successful-recording counters unchanged. Capture is cancelled, never uploaded.
- Same test invokes production insertion into the separate com.veenum.wisprlite.test EditText and verifies actual field contents include Yoruba Unicode and Pidgin wording. This proves the insertion path, not a complete floating-button-to-speech-to-field gesture.
- CredentialIsolationTest rerun: OK (2 tests), 5.612s. Includes encrypted file round trip with Android crypto, wrong-passphrase rejection, exact Unicode settings round trip and three independent keys re-encrypted through Android Keystore. Synthetic device-protected test state only; normal settings/credentials untouched. Full system-document-picker UI round trip remains untested.

## Optimized APK runtime sample before inset fix
- Installed a copy of the production release APK re-signed with the existing development certificate, allowing an in-place update without clearing WisprLite data. Production-signed original retained separately.
- Verified release flags omit DEBUGGABLE, Home renders, cold launch TotalTime 1294ms; foreground idle PSS 75212KB, RSS 203723KB, swap PSS 1232KB. Single snapshot with local engine inactive, not an inference or background benchmark.
- At font scale 1.5, Home content remained readable, but visual inspection exposed status-bar/header overlap on Android 15. Fixing system-bar/cutout insets and adding history-row margins; these changes require new build and device confirmation. Original font scale 0.9 restored.

## Inset-fix verification — source 2e69b4b
- Full local checks PASS in 4m32s: 103 unit tests, debug APK, test APK, lint debug/release and signed release. CI Android checks 41177537748 and secret scan 41177537612 PASS on the same source. Local gitleaks full history: no leaks.
- Updated Home visually confirmed on Redmi: navigation button begins at y=121, below the status bar; transcript cards are inset from screen edges. Startup/drawer/Unicode Activity-recreation regression rerun at font scale 1.5: OK (1 test), 18.48s. Restored original font scale 0.9.
- Offline engine rerun with airplane_mode_on=1 and wifi_on=0: OK (1 test), 8.262s. Original airplane_mode_on=0 and wifi_on=1 restored in finally.
- Current signed APK: .tools/releases/WisprLite-0.1.0.apk; 36,044,061 bytes; SHA-256 D622A83A176386A2BD7F08796F4E1A7337C49295A796728989153AA299F7FF55; production signature verifies.
- Native offline runtime libraries account for about 32MB of the APK. Model weights are downloaded separately; this build does not bundle speech models.
- User voice-corpus test requested on the empty helper field; results pending. No accuracy claims inferred from the written reference phrases.

## First user corpus attempt — failed
- User reports the Pidgin/Yoruba phrases appeared in Chinese. Current settings had no language_profile override (Mixed/Auto), multilingual Base selected, direct mode by default.
- This is a failed speech-accuracy result, not a passed language check. Exact output was not independently captured before the user report; do not fabricate a transcript or word-error rate.
- Corrective work: Home language control now opens the language picker directly; explicit Yoruba remains yo; Pidgin now supplies supported en as an English-based recognition hint plus its cloud Pidgin prompt. This is not a claim of native Pidgin support or guaranteed accuracy. Named profiles now override stale legacy language sets, including after upgrades.
- Regression: expected Pidgin en mapping failed (RED) before implementation; standalone suite passed 13/13 afterwards. Full changed-source build and device retest running/pending.

## Explicit profiles and compact Home
- Current native device runner PASS: 8 tests, including a new test proving named Yoruba/Pidgin profiles override stale Chinese legacy settings (yo/en respectively), while Auto clears the forced code. This validates configuration routing, not real Yoruba/Pidgin recognition accuracy.
- Compact Home debug APK installed and visually inspected: plain menu button, language and Direct/Cleanup controls in one row, date-grouped text history below. No repeated introductory text when history exists.
- Home history independently confirms the user's failed Auto attempt contains CJK output. The exact personal transcript and screenshots remain outside Git; no fabricated accuracy metric.
- Manual language-picker UX confirmation and explicit-profile speech retries requested; pending user result.

- Compact Home/language source 110990e: full local checks PASS in 3m28s; 103 unit tests and 8 physical-device integration tests pass. Local full-history secret scan clean. Current staged production-signed APK verified; 36044061 bytes; SHA-256 D8C8B9DFF8955F1BBDDBCEDB97D05F2094068639C63970A2623B979CAD94D079. Earlier staged artifact hashes are superseded by this one. Actual explicit-profile speech accuracy remains pending.

## Recovery, full Yoruba marks, and Small evaluation
- Confirmed the missing overlay followed instrumentation force-stopping the app: Android exit-info reports USER REQUESTED / finished instrumentation, not a new application crash. Rebinding restored the button. Device runner cleanup now explicitly launches WisprLite, cycles only its accessibility component and returns to the test field. Other accessibility services are preserved.
- Fixed watchdog reading the wrong preference-file spelling for the paused state.
- Extended device tests verify persisted Insights totals, required Settings controls, and microphone release when Background service is paused. Android filesystem expiry test verifies old WAV/TMP removal, fresh WAV preservation, unrelated-file preservation and audio-off cleanup. Latest runner: 9/9 PASS; logs .tools/device-tests/20261031-085207.
- Actual startup WorkManager sweep removed both provisioned synthetic WAV and abandoned TMP fixtures under audio-off policy. Before provisioning, the recordings directory was absent after consented successful dictations. No original user audio was saved.
- Unicode regression: long decomposed Yoruba output was rejected by the cleanup length guard. RED reproduced; fixed by comparing mark-free content without modifying returned text. Full local checks PASS in 3m57s; 104 unit tests, zero failures. Existing unrelated-rewrite rejection tests also pass.
- Packaged Apache-2.0, attribution, and ONNX Runtime MIT/third-party notices (pinned v1.28.2 source referenced by sherpa v1.13.8's Android build). Repackaging/lint PASS in 1m30s; verified license entries inside signed APK.
- Official Small archive: 639387718 bytes; local SHA256 486A46AFBB7BA798507190FFE02FEA2DD726049AF212E774537EFAC6AFB210A6. Publisher API supplies no digest. Full tar structure inspected; staged install and real inference PASS, 715.439s including initial extraction. Known English 8.055s fixture: 20077ms model load+decode. Temporary archive copies removed from phone after installation; installed Base/Small model directories preserved.
- Selected Small with explicit Yoruba and confirmed ready/visible overlay before user retry. User reports poor recognition and missing expected marks again: Small also FAILS this user's Yoruba accuracy requirement. Actual service timing: 9697ms for 3s audio. No success claim for Yoruba/Pidgin/mixed accuracy.
- New provider research: Intron's current official API language list explicitly covers Pidgin-English (pcm), Yoruba-English (yo), and English (en): https://docs.voice.intron.io/docs/stt/supported-languages . Sahara v2.5 is a candidate to test, not a measured WisprLite result. The current app client implements OpenAI-compatible transcription; Intron's protocol needs separate integration before claiming support.

### Pixel 6 and final regression checks, 2026-10-31
- Pixel 6 / Android 17: complete device runner 15/15 PASS; earlier 12/12 PASS at 150% font (original 0.85 restored). Includes microphone interruption, native/WebView insertion, permission denial, missing model, duplicate download and insufficient-space checks. Does not establish live Yoruba/Pidgin accuracy.
- Fixed redundant focus action that disrupted paste into an already-focused WebView editor. Light/dark Home, drawer and Settings captures inspected. Long Unicode transcript/copy test passed without history/counter mutation.
- Real multilingual Base known-English WAV (8.055s): load+decode 3779ms. Airplane mode on/Wi-Fi off English inference PASS; radios restored.
- Optimized release code installed with stable development certificate to preserve data: cold launch 156ms; immediate PSS 71848KB/RSS 185764KB, accessibility service inactive during measurement. One sample, not a battery/background benchmark. Restored debug build afterward; enabled own service and microphone permission, preserved other services. Empty dictation helper opened and floating control visually verified.
- Full local checks PASS in 3m16s: 107 unit tests, debug/test/release APKs, debug/release lint. Added real interrupted loopback HTTP download and corrupt archive/preserved existing install checks. Small download free-space budget now includes simultaneous compressed and extracted data.
- Cloud provider/cleanup live calls and Pixel live Yoruba/Pidgin trials remain unverified. Generic Base/Small failed earlier user Yoruba quality trials on Redmi.

Unused feature removal: full build/lint/release PASS (3m47s), remaining 95 unit tests PASS. Deleted 12 tests belonging to removed command/updater features. Fresh complete Pixel device runner 15/15 PASS, logs .tools/device-tests/20261031-105815. Release APK 36075391 bytes. Removed 786 lines including unreachable updater/migration/installer and disabled whole-field voice transformations; normal transcription, cleanup and encrypted backup remain.

### Backup picker regression and Redmi handoff, 2026-10-31
- User took Pixel and explicitly directed further testing to Redmi; only Redmi 12 targeted afterward. Updated app installed without clearing data.
- Real Android picker cancellation reproduced retained pending passphrase/snapshot (RED). Cancellation now drops both references; activity destruction and incomplete export state also clear pending values before returning. No claim of overwriting immutable String memory.
- Redmi cancellation test accounts for the filename keyboard consuming the first Back press. Initial combined direct instrumentation run: save/restore passed, cancellation assertion occurred before picker exit; retained as failed evidence, not counted as green. Final separate suites use established MIUI foreground harness.
- Actual CreateDocument save and OpenDocument restore passed with synthetic Unicode preference `Ẹ káàárọ̀, Ṣọlá. How you dey?`, no secrets/recordings included. Restored test preference removed afterward; only the two exact synthetic exported files were removed through ADB.
- Final Redmi complete runner: 17/17 PASS, logs .tools/device-tests/20261031-111017. Includes backup cancellation/roundtrip, model availability, long Unicode history, light/dark appearance, rotation, native/WebView insertion, microphone interruption and denial. Own service restored after tests.
- Full product checks PASS 3m33s (95 unit tests, debug/test/release builds, debug/release lint); final expanded test APK plus debug lint PASS 47s. No live cloud request or new Yoruba quality success is implied.

### Background recovery limitation, 2026-10-31
- Correction: the prior runner restored its initial accessibility enabled state; before this recovery check the Redmi had only Attenora enabled, not WisprLite. The earlier claim that a floating service had been restored was too broad. No arbitrary app process was killed: the initial precondition correctly rejected the missing WisprLite PID.
- Explicitly enabled only WisprLite alongside existing services and verified a bound running service (PID 28696). Backgrounded it, then sent SIGKILL through its own UID using run-as, without force-stop/data deletion. No new PID appeared in four samples over 12 seconds; subsequent inspection still showed a crashed/disconnected accessibility service with its secure toggle enabled.
- Forced only its scheduled watchdog job 64 via JobScheduler; command accepted, but job remained ready and no active own watchdog notification or process was observed. Channel existence is not notification delivery evidence. Automatic recovery/notification is unverified on this MIUI setup, not passed.
- Explicit Main launch followed by own accessibility off/on restored bound service (PID 28963); other service entries preserved, empty dictation field reopened. No claim that an ordinary app can toggle its own secure accessibility setting.

Latest full Redmi runner with clipboard fallback: 18/18 PASS, logs .tools/device-tests/20261031-131656. Sensitive Unicode clipboard fallback and unchanged recording counters verified when no editable field exists; previous clipboard restored. Test APK/debug lint PASS54s. Only new synthetic backup document removed by exact filename. Actual own service bound after suite (verified via dumpsys accessibility). No product changes since backup full build.

### Circular dotted bubble and free placement, 2026-10-31
- User approved soft teal, dotted voice-wave inside a circle (no chat tail/W), volume-responsive motion, tap/hold on one bubble, and free placement with only Bubble size remaining in Settings.
- Native circular dot Drawable and matching launcher/app vectors implemented with no new dependencies. Recording dot heights use existing measured microphone peaks; processing uses a separate dot loop respecting system animation disablement. No new recognition model is implied.
- Removed height/left-side controls. Drag release no longer snaps to an edge; both screen-relative coordinates persist. Legacy settings remain a fallback for existing installations. Size still clamps the bubble inside the screen.
- Gesture controller separates taps, holds and drags. Hold begins after 350ms; release routes to stop/transcribe, cancellation interrupts without sending audio. Interrupting a pending hold removes its timer, preventing a later microphone start. Hold haptics respect the existing vibration preference.
- Initial listener test exposed a missing bubble-ready precondition, then an overly short wait for hold activation. Both failures retained in ignored logs. Final real-listener test PASS 5.492s: tap start, hold start, cancellation, interrupted pending hold, free drag without edge snap, saved coordinates, unchanged history counts. Captures were cancelled, not uploaded; fresh user speech-to-text through hold release remains a separate trial.
- Full final product checks PASS 3m40s: 100 unit tests, debug/test/release artifacts, debug/release lint. Entire final Redmi runner 19/19 PASS, logs .tools/device-tests/20261031-182335. App-only light/dark screenshots and actual visible circular bubble inspected; screenshots stay ignored, not public source. Own service binding verified and empty test field reopened.
- Existing Yoruba/Pidgin accuracy and MIUI background-kill limitations remain unresolved.

### Modern tool UI and inline actions, 2026-10-31
- User selected Modern tool styling and the custom Signal icon family. Native branded header/drawer, transcript surfaces, numeric Insights panels, filled dictionary editor and icon/subtitle Settings rows implemented. No new runtime dependencies. Light/dark neutral surfaces replace inherited lavender; inactive-control outlines retain readable contrast.
- Enlarged the logo ring and waveform and removed the bubble icon's internal padding. Replaced the inherited startup artwork and black background with the circular WisprLite mark and teal theme-aware splash. Actual Redmi cold-start video/frame and visible enlarged bubble inspected; captures remain ignored/private.
- Follow-up requirements implemented: live size preview at the slider; language first in Settings and removed from Home; inline card copy/cleanup/delete and full-text expansion instead of the old options dialog; original/cleaned selection remains recoverable; right-side slide-in drawer.
- Updated long-transcript device check verifies exact full original and cleaned copying even from a collapsed preview, inline expansion, main-window focus (no separate detail dialog), available actions and unchanged persisted counters/history. The synthetic record is never stored in the real history database.
- New preview/layout device check verifies Home has no language picker, language is the first Settings action, the drawer settles anchored on the right, size changes keep the preview circular and visibly change its rendered pixels, and programmatic checks preserve saved size. Small/large native preview captures inspected.
- Full final product checks PASS 4m47s: 100 unit tests, debug/test/release builds and debug/release lint. Redmi complete device runner 20/20 PASS (.tools/device-tests/20261031-191650); latest expanded capture checks plus debug lint PASS1m12s, and complete 20/20 PASS at 150% text (.tools/device-tests/20261031-192343). Original font scale 0.9 restored. Normal-size capture rerun passed appearance and preview checks (.tools/device-tests/20261031-192758).
- The user reported the initial no-speech message did not recur and dictation worked in Google search. This UI change does not claim to fix Yoruba/Pidgin model accuracy, validate live cloud cleanup, or solve MIUI recovery.

### Compact models and official publisher badges, 2026-10-31
- User selected official provider logos: unchanged OpenAI, NVIDIA and Moonshine organization marks now identify model families. Remaining Settings, download and delete controls use native Signal vectors. Asset provenance is documented separately.
- Catalog adds Whisper Tiny multilingual (116,204,861-byte download) and English (118,071,777-byte download), while retaining every previous archive identifier. Rows separate download and actual installed size and label English-only packs. No active model preference was changed by fixture installation.
- New Whisper staging removes only redundant float encoder/decoder files after finding and validating a matching nonempty int8 pair. Existing Base/Small directories are untouched. Tests cover retained weights/tokens, missing/empty pairs and mismatched prefixes.
- Full product checks PASS8m59s: 103 unit tests, debug/test/release APKs and debug/release lint. Production signature verified; release APK36,087,683 bytes, SHA256 `7F6DF86B2C5C7C2C7D37DEF970045C2736105B98DA326EDACA50CCC09A313A8D`.
- Actual Redmi Tiny multilingual install plus inference PASS165.376s total; synthetic8.055s English audio processed in5794ms. Tiny English install plus inference PASS161.883s total; same audio processed in4985ms. Native int8 encoder/decoder and tokens retained, redundant float files absent. This is a short English smoke test, not a comparative accuracy benchmark or a Yoruba/Pidgin acceptance pass.
- Settings explains HTTPS OpenAI-compatible audio API requirements and a Groq example. No provider key was supplied and no live cloud speech/cleanup call was made.
- Refreshed Redmi complete runner20/20 PASS; logs .tools/device-tests/20261031-202950. Official badges and compact installed-size rows visually inspected on the actual device. Cloud toggle temporarily switched only for catalog inspection, then restored; own accessibility binding re-established after UI Automator. Temporary fixture archives and only identified synthetic backup documents removed. Four model packs remain installed; active model selection was not changed.

### Recovery warning lifecycle, 2026-10-31
- Actual WorkManager warning delivery was tested on the disconnected Redmi service. Initial RED: warning remained after intentional pause. Second RED after the first fix: pausing during the five-second settle period recreated the warning.
- Delivery now rechecks current paused/enabled/bound state after settling and shares a lock with cancellation. Intentional pause and service reconnection clear existing warnings immediately. No claim of automatic MIUI service recovery is made.
- New instrumentation executes the real worker, verifies visible notification presence and later absence, exercises pause while work is running, and restores preference value/presence in nested cleanup. A device that automatically rebinds cannot establish the disconnected fixture and skips that specific scenario; a skipped fixture is not a delivery acceptance pass. Redmi completed all three worker executions (10.629s), not a fixture skip.
- Full product checks PASS3m50s:103 unit tests, debug/test/release builds, debug/release lint. First full device run timed out waiting for Android's document picker, although launch was accepted by the system. No product fix was inferred from that single timeout. Complete rerun21/21 PASS, logs .tools/device-tests/20261031-205536. Own accessibility service bound and original microphone/notification grant states verified afterward. Exact new synthetic backup removed.
- Current production-signed APK36,087,683 bytes; signature verified, SHA256 `AE23670402961512CDD76ED82FA86C9335D460FDAB3F05D1D92394624C85D2DE`. This supersedes the preceding artifact hash.
- User requested an accuracy-first model comparison and is considering Groq versus Google AI Studio. No key supplied and no cloud inference executed. See model-candidates.md; multiple saved speech-provider profiles and a Google audio adapter are not implemented.
