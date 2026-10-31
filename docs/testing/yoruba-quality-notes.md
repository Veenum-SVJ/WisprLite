# Yoruba recognition and orthography evidence

The user's requirement is full standard Yorùbá letters and tone marks, including within mixed English/Yorùbá dictation. Recognition accuracy and Unicode preservation are separate acceptance checks.

## Current device evidence

- Multilingual Base in Auto failed the consented short-phrase test by producing CJK output.
- Explicit Yoruba selection produced Latin text but still misrecognized the requested phrase and omitted the required marks. This remains a failed accuracy result.
- Exact Unicode storage, draft recreation, insertion and encrypted-backup round trips pass. Cleanup's long-text length guard was found to reject valid decomposed Yoruba marks; regression reproduced and fixed. Comparisons ignore combining marks while returned text remains unchanged.
- Multilingual Small installs and runs on Redmi: English synthetic test passes. First extraction plus inference took 715.439s; 8.055s audio took 20077ms for model loading plus decoding. These are not Yoruba accuracy or steady-state latency results.

## Existing-model research

[Tone in Yoruba ASR (Olusanya, LoResLM 2026)](https://aclanthology.org/2026.loreslm-1.14/) studies tone-marked recognition and reports difficulties across evaluated architectures. A language code alone is not evidence of correct tone recognition.

[Òwe-Voice model card](https://huggingface.co/Haramiday/owe-voice-whisper-large-v3-yoruba) describes an existing Yoruba-adapted Whisper model with tonal orthography, but explicitly limits its evaluation to particular domains/speakers and reports substantially weaker conventional-speech results. It is not a validated Android pack for WisprLite.

[NaijaVox-2.0's publisher model card](https://huggingface.co/Axiveri/NaijaVox-2.0) claims Yoruba/Pidgin and code-switching improvements using adapted Whisper-large-v3. Those are publisher results, not independent WisprLite device results. No runtime compatibility, endpoint availability or user-voice quality has been verified here.

[Yo-ByT5 (2026 preprint)](https://arxiv.org/abs/2610.01634) evaluates dedicated diacritic restoration. Restoration is a separate text task and does not establish that incorrectly recognized words can be recovered reliably. This release's approved cleanup remains a configurable cloud text provider; no additional local cleanup runtime has been added.

Do not claim perfect tones, silently translate mixed speech, or replace missing speech with guessed text. Preserve original ASR beside cleanup, and evaluate actual user speech before changing quality recommendations. No training is required to evaluate these existing models.
