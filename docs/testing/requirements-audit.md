# Requirements audit — open until evidence is complete

The goal remains the full approved PRD/design and seven-task plan. This file records outstanding work; passing builds are not treated as proof of speech accuracy.

| Requirement | Evidence / current state |
|---|---|
| Public repository, provenance, separate app identity | Created Veenum-SVJ/WisprLite; upstream pinned and attributed; distinct app ID installed alongside original |
| Home/Insights/Dictionary/Settings drawer | Implemented; Home and drawer observed on Redmi; startup/drawer/Back and Unicode draft across Activity recreation passed; Settings control rendering and persisted Insights totals verified on Redmi |
| Configurable transcription and separate cleanup providers | Request tests and credential-isolation instrumentation pass; actual cloud request/cleanup needs configured provider |
| Existing local models, no automatic cloud fallback | Runtime integrated; real multilingual Base English test passed with airplane mode on and Wi-Fi off; radios restored |
| English/Yoruba/Pidgin/mixed, full Yoruba writing | Profiles/hints/preservation rules implemented; Base and Small both failed user Yoruba accuracy/diacritic trials; correct language routing/Unicode preservation verified; quality unresolved |
| Dictionary and history, originals plus cleaned text | Implemented; Unicode/exact-once/pagination/expiry instrumentation passed |
| Insights and configurable estimate | Implemented; calculation tests pass; screen contents verified against persisted totals on Redmi |
| Audio off by default, optional expiry | Policy tests pass; worker implemented; Android filesystem expiry and actual startup WorkManager cleanup pass; no retained original audio observed with saving off |
| Background recording and late-result cancellation | Review fixes implemented; session tests pass; real microphone interrupt/release and cross-app insertion integration passed; real user gesture/insertion and pause release verified; test cleanup reconnects service; native and local WebView composer insertion passed on Pixel; broader OS recovery checks remain |
| Model downloads validated and atomically installed | Archive tests pass; real Base and Small staged installation/inference passed on Redmi |
| Appearance, accessibility and large fonts | Theme/control code implemented; Activity recreation and 150% font navigation/draft checks passed; Android 15 system-bar overlap fixed and visually confirmed; light/dark Home, drawer and Settings verified; complete long Unicode transcript/copy test passed on Pixel at 150% font |
| Backup/restore and key protection | Existing encrypted backup retained, keys opt-in; credential separation device test passes; encrypted-file/device-Keystore round trip passed; real Android document-picker save/restore of a synthetic Unicode preference and cancellation passed on Redmi; no real keys exported |
| Lightweight release and performance | Shrunk unsigned release built (about 36 MB); initial debug PSS measured; optimized APK runtime sampled: 1294ms cold launch, 75212KB idle PSS; latest Pixel optimized cold launch 156ms, immediate PSS 71848KB and RSS 185764KB with service inactive; this single sample is not a sustained background benchmark |
| Tests, lint, release build | CI and local full build passed for recorded revisions; current full local build passed: 95 remaining unit tests after deleting 12 tests for removed command/updater features, debug/test APKs, lint debug/release and signed release |
| Pixel 6 and Redmi 12 validation | Redmi and Pixel tested; Pixel Android 17 complete device runner 15/15 passed; latest Redmi complete runner 17/17 passed including real document picker, 12/12 at 150% font; real Pixel English Base inference and airplane-mode inference passed. Live Pixel Yoruba/Pidgin trials remain. |
| Signed APK and final report/source push | Production-signed release built and signature verified; final artifact handoff and complete report pending |
