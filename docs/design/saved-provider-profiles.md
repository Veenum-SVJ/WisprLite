# Saved provider profiles

Approved by the user: keep multiple named providers and choose transcription and AI cleanup independently, without automatic switching.

Settings keeps Language first, followed by manual transcription settings and Saved providers. The profile picker provides Groq, Google AI Studio and custom starting points. Each profile has a name, HTTPS API base URL, optional speech model, optional cleanup model, speech request format and one encrypted provider key. A profile needs at least one model. Saving a new profile does not select it or enable cloud recording/cleanup. Use for transcription and Use for AI cleanup are separate explicit actions.

Profiles are selected by stable identifiers. Metadata contains no keys; each key is encrypted using the existing Android Keystore mechanism. Configuration and credential resolution share a lock with edits, so a request uses the matching profile data and key. Missing profiles/keys never fall back to another provider. Editing a selected profile updates that selection. Deleting a selected profile is blocked until the user chooses another profile or saves manual settings.

Manual configurations remain available and are not overwritten by profile selection. Saving manual speech or cleanup settings clears only the matching selected-profile identifier. Optional settings backups include profile metadata and offer each profile key separately, unchecked by default, inside the existing passphrase-encrypted envelope. Restored metadata is validated; restored keys are encrypted again on the receiving device.

Whisper-compatible speech uses multipart audio transcription. Google audio chat uses an explicit JSON request with Base64 WAV input and transcription-only instructions. Protocol selection is explicit; errors do not trigger fallback. Google text cleanup uses its compatible chat endpoint. Neither protocol integration is proof of model availability or real language accuracy without user credentials and consented speech trials.

## Model visibility and Settings follow-up

The user reported that only model storage was visible. The cause was hiding the model container whenever cloud mode was selected. Model management and official publisher badges must remain visible in both modes, preserving installed packs and selected local model. Choosing cloud mode controls recognition routing, not whether downloaded models can be viewed.

Custom Signal icons distinguish language, appearance, audio retention, text-history retention and typing speed. These remain native vectors with no icon-font or new runtime dependency.
