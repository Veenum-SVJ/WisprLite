# WisprLite

Minimal Android dictation with cloud and offline speech recognition.

Implementation in progress. See docs/superpowers/specs/2026-10-30-wisprlite-design.md and docs/superpowers/plans/2026-10-30-wisprlite.md.

## Provenance
Adapted from EdiBianco/OpenWhispr v4.0.0, commit 7f41114c1028190749b3bfeed721c3aef05d56fe, and its phone-whisper foundation. Original Apache-2.0 license retained in LICENSE. Upstream documentation is preserved under docs/upstream.

## Build
JDK 17 and Android SDK platform 35 are required. Run ./gradlew testDebugUnitTest assembleDebug lintDebug. Models download separately; never commit API keys or recordings.
