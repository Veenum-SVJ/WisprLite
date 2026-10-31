# Requirements audit — open until evidence is complete

The goal remains the full approved PRD/design and seven-task plan. This file records outstanding work; passing builds are not treated as proof of speech accuracy.

| Requirement | Evidence / current state |
|---|---|
| Public repository, provenance, separate app identity | Created Veenum-SVJ/WisprLite; upstream pinned and attributed; distinct app ID installed alongside original |
| Home/Insights/Dictionary/Settings drawer | Implemented; Home and drawer observed on Redmi; startup/drawer/Back and Unicode draft across Activity recreation passed; Settings/Insights walkthrough remains |
| Configurable transcription and separate cleanup providers | Request tests and credential-isolation instrumentation pass; actual cloud request/cleanup needs configured provider |
| Existing local models, no automatic cloud fallback | Runtime integrated; real multilingual Base English test passed with airplane mode on and Wi-Fi off; radios restored |
| English/Yoruba/Pidgin/mixed, full Yoruba writing | Profiles/hints/preservation rules implemented; user-language accuracy corpus not yet evaluated |
| Dictionary and history, originals plus cleaned text | Implemented; Unicode/exact-once/pagination/expiry instrumentation passed |
| Insights and configurable estimate | Implemented; calculation tests pass; screen contents still need device walkthrough |
| Audio off by default, optional expiry | Policy tests pass; worker implemented; live recording/expiry lifecycle still needs device checks |
| Background recording and late-result cancellation | Review fixes implemented; session tests pass; real microphone interrupt/release and cross-app insertion integration passed; full speech gesture, pause and restart checks remain |
| Model downloads validated and atomically installed | Archive tests pass; real Base installation and inference passed on Redmi |
| Appearance, accessibility and large fonts | Theme/control code implemented; Activity recreation and 150% font navigation/draft checks passed; Android 15 system-bar overlap fixed and visually confirmed; light-theme/other-screen checks remain |
| Backup/restore and key protection | Existing encrypted backup retained, keys opt-in; credential separation device test passes; encrypted-file/device-Keystore round trip passed; document-picker UI round trip outstanding |
| Lightweight release and performance | Shrunk unsigned release built (about 36 MB); initial debug PSS measured; optimized APK runtime sampled: 1294ms cold launch, 75212KB idle PSS; updated layout build and inference/background measurements remain |
| Tests, lint, release build | CI and local full build passed for recorded revisions; current full local build passed: 103 unit tests, debug/test APKs, lint debug/release and signed release |
| Pixel 6 and Redmi 12 validation | Redmi connected and in use; Pixel 6 not yet connected/tested |
| Signed APK and final report/source push | Production-signed release built and signature verified; final artifact handoff and complete report pending |
