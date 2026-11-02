# OpenWisper 0.2.3 clipboard preview

Dictation and Home transcript copies now use ordinary plain-text clips without the sensitive-content flag, so Android and compatible keyboards can display the actual words rather than password dots. Paste-based insertion and manual fallback retain the complete original text. The change intentionally allows normal clipboard previews.

Settings explains that typing speed is the user's estimated words per minute, used only by Insights to estimate typing time saved. It does not change recognition, processing or insertion speed. The dialog explains the range and purpose.

Pixel integration check passed: an unfocused/noneditable target retains exact Unicode text on a readable clipboard without adding history, and restores the previous clipboard afterward. Debug/test assemblies and debug lint passed. Optimized release signed and 16 KiB alignment verified; Pixel updated to0.2.3/code6 with install -r, launched normally and own accessibility service rebound while other service stayed enabled. Test helper removed; user data retained. APK37,545,488 bytes; SHA-256 b44e24cb62b06b41f15d99efba54afe9c7da545bc4f6b54361a90de8e6bf192c. Release lint result is recorded in the handoff.

The reported approximately 30-second lingering UI remains unclassified: user clarification is pending on whether it is the system clipboard preview, floating bubble/capsule or app feedback. App clipboard fallback feedback is configured for two seconds; focus debounce is300ms and polling500ms. No guessed timeout or global Android accessibility setting has been changed.
