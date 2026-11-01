# Insights charts and complete model removal — 2026-11-01

Status: optimized version 0.1.1 is installed on Pixel and Redmi, test components are removed, and the [public APK is published](https://github.com/Veenum-SVJ/WisprLite/releases/tag/v0.1.1-preview). The 0.1.0 preview predates these changes.

Verification: 122 unit tests and debug/release lint passed; the complete final build passed in 3m45s. Pixel model-control/chart assertions passed in 2.701s; light/dark appearance passed in 2.747s. The rendered dark Insights screenshot was visually reviewed. Redmi's attempted test-helper installation was cancelled and is not a passed check.

The approval service temporarily blocked cleanup/signing because its usage limit was reached. It recovered after its stated reset; normal approved work resumed. Optimized deployment and publication subsequently completed through the normal approval flow. The latest version also includes the [clean Settings layout](clean-settings.md).

The user requested graphical Insights with details beneath, and cloud-only use without retaining local models.

The selected local model previously hid its Delete button. Every installed model now shows Delete in both cloud and local modes, including the selected/only model. Confirmation remains required; deletion is refused while dictation is recording or processing. Deleting a model reloads the local engine and refreshes the storage summary. Missing local models do not trigger a cloud fallback. Users may delete every pack and explicitly enable cloud transcription.

Saved providers support Groq for transcription and either Groq or Google AI Studio for cleanup. Select a saved profile's Use for transcription / Use for AI cleanup action independently. Direct mode skips cleanup. Saving keys alone does not enable cloud recording or cleanup.

Insights keeps Today, Last 7 days and All time, each with a native donut chart and the words, recordings, speaking duration and estimated time saved beneath. Teal indicates estimated time saved and grey indicates speaking time. Estimates use the configured typing speed and exclude model/network processing; no measured speedup is claimed. Empty periods show an empty ring. Text labels provide the same information without relying on color or chart accessibility. No chart dependency, background telemetry, schema change or model download is introduced.

Design follows the existing warm light/dark Signal style and the UI/UX Pro Max skill's chart/accessibility guidance. The device regression checks that every installed model's Delete action is visible in both modes, including the selected model, and that all three Insights charts have estimated-time details. Tests preserve the user's providers, models and history.
