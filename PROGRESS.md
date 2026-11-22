# ClipTova progress tracker

Last updated **2026-11-22 16:31 IST (UTC+05:30)**

**Current status** · `sprint-1` includes video upload, local transcription, and AI Highlights with an interactive preview. Phase 4 Clip Generation is next.

## Completed milestones

| Completed (IST) | Milestone | Result |
| --- | --- | --- |
| 2026-11-21 21:42 | Sprint 1 / Phase 0 · Foundation | Pushed `dddf77c`. React dashboard, Spring Boot health API, PostgreSQL and Flyway V1 are working. All 9 backend tests and both production builds passed. |
| 2026-11-21 21:55 | Phase 1 · Video upload | Added upload progress, local original storage, FFprobe metadata and Flyway V2. Browser upload and retry passed, along with 19 backend tests and both builds. |
| 2026-11-21 22:03 | Real upload verification | Confirmed the 8:08 video in project **test** was saved and its metadata matched the project record. |
| 2026-11-21 22:14 | Phase 2 · Local transcription | Added FFmpeg audio extraction, whisper.cpp, timestamped transcripts, cancellation, retry and Flyway V3. Browser verification with an English sample, 26 backend tests and both builds passed. |
| 2026-11-21 22:51 | Hindi / Hinglish retest and transcript revisions | Whisper Large v3 Turbo produced Devanagari Hindi and retained English terms on **test**. Revision 3 contains 129 ordered segments through 00:08:07.880. Flyway V4 preserved earlier revisions, and the original video was retained. All 27 backend tests and the frontend production build passed. |
| 2026-11-22 16:03 | Phase 3 · AI Highlights | Added local Ollama with Qwen, ranked transcript-grounded suggestions, video preview, editable timing, and saved review decisions. Verified six suggestions on **test**, Flyway V5, 30 backend tests, and the frontend build. |
| 2026-11-22 16:06 | Source timecodes | Added HH:MM:SS for video durations, transcript timestamps, and editable highlight ranges. Saved cut points retain millisecond precision. |
| 2026-11-22 16:10 | Clip preview controls | The preview shows elapsed time and duration within the highlight. Verified playback, replay, and seeking for the 10-second clip in **test**. |
| 2026-11-22 16:15 | Interactive clip player | Added on-video playback controls, keyboard scrubbing, five-second jumps, mute, and full screen. Verified the controls on **test**. |
| 2026-11-22 16:31 | Phase 1–3 code review | Fixed a rounding edge case at the end of a source video. All 31 backend tests and the frontend production build passed. |

## To do

Open items reviewed **2026-11-22 16:31 IST**. Roadmap phases are listed in delivery order.

- [ ] Review the full **test** transcript for wording, code-switching and timestamp accuracy. The Hindi / Hinglish retest was spot-checked.
- [ ] Evaluate Marathi on a representative Marathi recording and measure transcription accuracy and processing time on longer recordings.
- [ ] **Phase 4 · Clip Generation** — Add FFmpeg trimming, 9:16 output, subtitles, 30/45/60-second presets, preview and download.
- [ ] **Phase 5 · Automatic Clipping** — Rank and select top candidates, generate multiple clips and show progress.
- [ ] **Phase 6 · AI Admin Panel** — Add provider and model configuration, API key management, routing, fallback, and usage and cost monitoring.
- [ ] **Phase 7 · Quality and Private Beta** — Test podcast, gaming and education footage across supported languages. Measure timestamp and subtitle accuracy, clip quality, processing time and cost.

Update the snapshot timestamp when editing this file. Record each completed milestone with its IST timestamp and verification result.

[Product roadmap](Plan.md#13-development-roadmap) · [Development log](docs/DEVELOPMENT_LOG.md)
