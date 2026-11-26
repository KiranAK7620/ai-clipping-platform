# ClipTova progress tracker

Last updated **2026-11-26 19:28 IST (UTC+05:30)**

**Roadmap checkpoint.** Phases 0–5 met their acceptance criteria. Phase 6 AI Admin Panel is next, followed by Phase 7 quality testing and private beta.

## What shipped through Phase 4

| Phase | Delivered | Acceptance evidence | Pushed commit |
| --- | --- | --- | --- |
| 0 · Foundation | React dashboard, Spring Boot API, PostgreSQL, Flyway, and local configuration. | Frontend and backend started, the database health endpoint returned `UP`, and Flyway applied the initial schema. | `dddf77c` |
| 1 · Video Upload | Upload progress and retry, local storage for originals, and metadata extraction with FFprobe. | Uploaded a real video and confirmed that the saved file and metadata appeared in the project. | `d4880ab` |
| 2 · Transcription | Local FFmpeg and whisper.cpp processing, timestamped transcripts, searchable segments, and transcript revisions. | Verified timestamped English transcription in the browser and spot-checked a Hindi/Hinglish sample. | `d4880ab` |
| 3 · AI Highlights | Local Ollama/Qwen suggestions ranked from the transcript, editable time ranges, source preview, and saved review decisions. | Reviewed six suggestions on the `test` project and verified preview, timing edits, and selection. | `d4880ab` |
| 4 · Clip Generation | Editing workspace with manual trimming, 30/45/60-second presets, 9:16 MP4s, customizable captions, reusable caption themes, and export preview and download. | Verified 720 × 1280 exports, caption rendering, preview and download, and the original video's checksum. | `72c8d9e` |

## Current local verification

The November 26 smoke check loaded the dashboard and editing workspace in the browser. The API and PostgreSQL were healthy, all 42 backend tests passed, and the frontend production build passed. The saved `test` project returned a completed transcript, six highlights, and nine ready exports. An MP4 preview was served successfully.

Phase 5 testing used two fresh uploads from the existing test video. The full 8:08 source produced a new transcript with 181 segments, eight ranked highlights, and two 30-second clips without manual timestamp selection. Both preview routes responded, both downloads succeeded, and FFprobe confirmed 720 × 1280 H.264/AAC. The 2½-minute excerpt returned a partial result with one of three requested clips. Repeating the same batch request returned the same run, and a second request during an active batch returned HTTP 409.

## Remaining roadmap

| Phase | Status and plan | Acceptance check |
| --- | --- | --- |
| 6 · AI Admin Panel | Add provider and model settings, API key management, task routing, fallback, and usage and cost monitoring. | The owner can switch providers without changing core video-processing logic. |
| 7 · Quality and Private Beta | Test podcast, gaming, and education footage across English, Hindi, Hinglish, and representative Marathi recordings where supported. Measure timestamp and subtitle accuracy, clip and output quality, processing time, and AI cost per video. | Review the quality results before inviting private beta users. Consider public registration, subscriptions, and cloud hosting after this testing. |

Open quality checks include a full Hindi/Hinglish transcript review for wording, code-switching, and timestamp accuracy, plus a representative Marathi test and timing on longer videos.

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
| 2026-11-22 16:46 | Phase 4 · Clip Generation | Added local FFmpeg exports with saved ranges, duration presets, and two caption styles. Verified 10- and 45-second 720 × 1280 MP4s on **test**, including preview, download, and caption timing. All 34 backend tests and the frontend production build passed. |
| 2026-11-22 17:00 | Project editing workspace | Replaced project dialogs with full-page upload, trim, transcript, and export views. Verified drag-to-trim, timeline zoom, transcript seeking, caption styles, and the preset export across desktop and narrow layouts. All 35 backend tests and the frontend build passed. |
| 2026-11-22 17:17 | Caption editor | Added caption toggling, color pickers, typography, opacity, placement, and outlines. Verified custom and caption-free MP4s plus reuse of saved settings. All 37 backend tests and the frontend build passed. |
| 2026-11-22 17:24 | Saved caption themes | Added a shared library of named caption settings. Verified that Amber Serif could be saved in **test** and applied in **Phase 2 English sample** after reload. All 40 backend tests and the frontend build passed. |
| 2026-11-22 17:28 | Caption theme editing | Added in-place editing and renaming for saved themes. Verified cross-project updates, cancel behavior, 42 backend tests, and the frontend build. |
| 2026-11-22 17:39 | Phase 4 release review | Verified 30-, 45-, and 60-second 720 × 1280 H.264/AAC exports, preview and download, theme rendering, and the original video checksum. All 42 backend tests and the frontend build passed. |
| 2026-11-26 19:28 | Phase 5 · Automatic Clipping | Added batch settings, stage and per-clip progress, previews and downloads. Verified Flyway V9, a fresh two-clip batch, a partial batch, repeat-request handling, 42 backend tests, and the frontend build. |

Update the snapshot timestamp when editing this file. Record each completed milestone with its IST timestamp and verification result.

[Product roadmap](Plan.md#13-development-roadmap) · [Development log](docs/DEVELOPMENT_LOG.md)
