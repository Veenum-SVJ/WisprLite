# WisprLite redesign handoff

Last checkpoint: 2026-11-01, Africa/Lagos. Planning/reference capture and independent builder review complete; user design review pending. No production Kotlin/resource/Gradle code changed for this new redesign. Prior app 0.1.1 is shipped on both phones, with its original implementation goal completed; this redesign is a NEW scope.

Repo: C:/Users/Veenum/Documents/Projects/WisprFlow clone
Remote: https://github.com/Veenum-SVJ/WisprLite
Baseline branch: implementation; baseline commit: 0c7da0c (revalidate git status/log).
Public baseline APK: https://github.com/Veenum-SVJ/WisprLite/releases/tag/v0.1.1-preview
Baseline APK: 36,215,895 bytes, SHA256 f7ac3ce508d994eb1080c97d73d6ffa9f14c7567311ecedf34787f3350f93bce.

## Read first
- docs/superpowers/specs/2026-11-01-openwhispr-mobile-design.md
- docs/superpowers/plans/2026-11-01-openwhispr-mobile.md
- docs/design/clean-settings.md and docs/testing/daily-use-handoff.md for the shipped baseline.
- New design artifacts under docs/design/openwhispr-redesign/.

## Explicit user decisions
Blue circular logo with thinner white ring and more/smaller white dotted waveform; same blue inside/outside ring. Exact installed desktop logo asset blue is #2056DF. OpenWhispr-inspired Home, Dictionary/Snippets, Insights speaking-activity heatmap and categorized Settings adapted to phone. Keep Home/Insights/Dictionary; no accounts, hotkeys, calendar or extra desktop features.
User answered: transcripts only, NO Notes; snippets are SPOKEN shortcuts expanding saved text; tone/style remembered PER APP with manual override. New feature behavior/details are specified in the design. Review builder proposals rather than treating them as user answers.

## Private references
11 screenshots saved locally in .tools/openwhispr-reference/. Some contain personal history, dictionary, usage figures or masked keys; NEVER publish them or include in commits. Windows reference app is com.gizmolabs.openwhispr, installed at C:/Users/Veenum/AppData/Local/Programs/OpenWhispr. Its resources/src/assets/logo.svg confirmed #2056DF; do not read resources/.env. Reference app was only navigated, not configured, and returned to Home.

## Important engineering constraints
Native Kotlin Views/Material/SQLite/OkHttp/sherpa; Android 11+/arm64; no runtime UI framework or model downloads for cosmetics. HistoryDatabase schema is v1 and onUpgrade currently rejects changes: implement an actual tested v2 migration before snippets/expanded text. Existing encrypted backups are version1 and need backwards compatibility. Direct mode must not trigger AI; snippets must preserve exact saved text through cleanup. Capture destination app before overlay/IME focus changes. Keep keys encrypted and out of chat/logs. Never copy keys between phones without explicit user intent.

Use .tools/android/sdk/platform-tools/adb.exe; recheck devices instead of assuming connected IDs. Do not publish device serials in handoff documents. Workspace GRADLE_USER_HOME=.tools/gradle. JDK17/SDK35/build-tools35.0.0 already available. Build/release instructions live in the prior handoff.
Existing installations use a persistent local sideload signer. Signing materials and passwords are ignored; do not print/copy them or uninstall the app on mismatch. adb install -r preserves data; com.veenum.wisprlite.test is the removable test helper, NOT the real app. Preserve original OpenWhispr and other accessibility services.

Baseline validation: 122 unit tests and final 28-check Pixel device suite passed; earlier web-editor insertion checks were intermittent even though final/focused checks passed. Their underlying cause was NOT definitively resolved. Preserve this limitation and don't weaken assertions to get green.

## Continuation prompt to paste into OpenCode or another coding agent

Continue WisprLite's OpenWhispr-inspired Android redesign in this repository. Read HANDOFF-OPENWHISPR-REDESIGN.md, then the new design and proposed implementation plan, inspect git status and AGENTS.md, and preserve all unrelated user changes. The installed 0.1.1 app is a working baseline; do not confuse its completed goal/tests with this new redesign being complete. Confirm whether the user has approved the full new design; if not, present the existing design artifacts and resolve only material questions before product implementation. Once approved, implement the ENTIRE agreed scope in staged commits: blue fine-dot logo, mobile Home/search, Dictionary/Snippets with spoken expansion, per-app cleanup tone/manual override, Insights activity heatmap, categorized provider/privacy/system settings and explicit update check. Keep native/lightweight architecture and existing dictation behavior. Do not expose API keys, publish private screenshots, download models automatically or clear/uninstall the main app. Run meaningful migration/pipeline/provider/UI/device checks, document actual failures and APK bytes, install authorized data-preserving updates, remove owned test fixtures, and publish only verified source/APKs. Update this handoff after every milestone and before stopping, including exact commits, tests, unfinished tasks and the very next action. Never claim unrun tests or unfinished features are complete.

## Next action
Independent builder review completed: nothing important left. Let the user review the design and visuals before product-code changes. No redesigned app/APK exists yet.
