# Clean Settings layout

Status, Setup and Service are first, followed by Engine and the dictation language. The remaining controls are grouped into native collapsible sections:

- Providers and cleanup: manual speech settings, saved profiles, language help, cleanup provider/switch/instructions and optional manual key override.
- Recording and bubble: sound/vibration and the live bubble-size preview.
- Storage and appearance: one Local models entry with an inline expandable model list, appearance, retention and typing-speed settings.
- Backup and data: encrypted backup/restore, history deletion and statistics reset.
- About: app interface language and GitHub.

On Xiaomi/Redmi, the separate Background autostart shortcut lives beside Setup rather than inside an unrelated group. Section and model-list expansion preferences persist. A refresh does not reopen collapsed models. Expanded/collapsed state is exposed to accessibility services and the custom Signal chevron rotates with the state. No chart/UI dependency or database migration is added. Existing provider profiles, model files and text history are unchanged.

The model-control regression checks the top-level order, a single Local models entry, inline open/close behavior, persistence across refresh, installed-model Delete actions in either engine mode, and the Insights charts. Final build/device evidence will be recorded after verification of this layout.

Final production build: 122 unit tests, debug/release lint and all APK variants passed (3m57s). Pixel targeted model/layout/chart checks and light/dark appearance passed, and screenshots were visually reviewed. The subsequent complete device suite passed all 28 checks. Two earlier full runs reported successful web paste without a matching accessibility text result; focused cold/native/web trials and the final full run passed. A stable-editor precondition and diagnostics were added to the test, while its exact text assertion remains. The underlying intermittent failure is not claimed resolved, and no production insertion behavior was changed in this update.
