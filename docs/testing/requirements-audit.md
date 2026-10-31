# Requirements audit — open until evidence is complete

The goal remains the full approved PRD/design and seven-task plan. This file records outstanding work; passing builds are not treated as proof of speech accuracy.

| Requirement | Evidence / current state |
|---|---|
| Public repository, provenance, separate app identity | Created Veenum-SVJ/WisprLite; upstream pinned and attributed; distinct app ID installed alongside original |
| Home/Insights/Dictionary/Settings drawer | Implemented; Home and drawer observed on Redmi; latest controls/rotation still need device walkthrough |
| Configurable transcription and separate cleanup providers | Request tests and credential-isolation instrumentation pass; actual cloud request/cleanup needs configured provider |
| Existing local models, no automatic cloud fallback | Runtime integrated; known multilingual Base installed and real English speech smoke test passed on Redmi |
| English/Yoruba/Pidgin/mixed, full Yoruba writing | Profiles/hints/preservation rules implemented; user-language accuracy corpus not yet evaluated |
| Dictionary and history, originals plus cleaned text | Implemented; Unicode/exact-once/pagination/expiry instrumentation passed |
| Insights and configurable estimate | Implemented; calculation tests pass; screen contents still need device walkthrough |
| Audio off by default, optional expiry | Policy tests pass; worker implemented; live recording/expiry lifecycle still needs device checks |
| Background recording and late-result cancellation | Review fixes implemented; session tests pass; microphone/interrupt/pause integration not yet verified |
| Model downloads validated and atomically installed | Archive tests pass; real model installation is being exercised on Redmi |
| Appearance, accessibility and large fonts | Theme/control code implemented; rotation/large-font and visual checks outstanding |
| Backup/restore and key protection | Existing encrypted backup retained, keys opt-in; credential separation device test passes; latest backup round trip outstanding |
| Lightweight release and performance | Shrunk unsigned release built (about 36 MB); initial debug PSS measured; latest release startup/memory/latency still needs measurement |
| Tests, lint, release build | CI and local full build passed for recorded revisions; current full local build passed: 103 unit tests, debug/test APKs, lint debug/release and signed release |
| Pixel 6 and Redmi 12 validation | Redmi connected and in use; Pixel 6 not yet connected/tested |
| Signed APK and final report/source push | Production-signed release built and signature verified; final artifact handoff and complete report pending |

