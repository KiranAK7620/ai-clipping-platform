# ClipTova development log

## 21 November 2026 · Sprint 1 foundation

- [x] Read the product plan and welcome document, and verified the checkout is on `sprint-1`.
- [x] Verified Java 17.0.21.0.3, Maven 3.9.11, Node.js 22.20.0, npm 10.9.3, Git 2.51.1, FFmpeg 9.0.2 and Docker 29.8.2.
- [x] Built the React, TypeScript and Vite dashboard with Material UI, locally served Inter and React Query. Added section navigation, activity counters, recent-project empty state, and a New Project dialog with four category choices and an upload placeholder.
- [x] Built the Java 17 and Spring Boot 3.5.16 modular monolith with Spring Web, Spring Data JPA, Hibernate and Bean Validation. Added a typed health response, pooled JDBC connection check and HTTP Problem Details.
- [x] With owner approval, created `clipping_db` and `clipping_user` within the existing PostgreSQL 18.6 container at `localhost:5434`. Saved the generated password in the Git-ignored local `backend/.env`. No commands modified `devdb`.
- [x] Applied Flyway V1 to create the UUID-backed projects table. Restarted the packaged application with Flyway 11.20.3 and verified that it validated V1 and reported the schema up to date.
- [x] Passed nine backend tests covering connection success, failure and release, health response codes, and Problem Details for missing routes, unsupported methods and unexpected errors. Passed TypeScript checking and the Vite production build.
- [x] Started Vite and the backend locally. Direct and proxied health requests returned HTTP 200 with both fields `UP`. In the browser, verified live status, category navigation, Gaming preselection, the upload placeholder, and the narrow layout.
- [x] Documented run commands, architecture and database provisioning in the README and `docs/`.

### Local development notes

macOS generates AppleDouble files on this checkout volume. The Maven configuration excludes them from test discovery and the packaged JAR and lets Spring skip them while scanning unpackaged classes. Maven development startup may report one ignored SQL file when the volume creates a sidecar beside the migration. The packaged application validated V1 without that message.

The dashboard uses an empty workspace for its project list and counters. Only the system status is served by the API.

### Next milestone

Sprint 1 implementation and verification are complete. The next milestone is Phase 1 video upload with project creation, local file storage and FFprobe metadata extraction. It is pending owner approval.

## 21 November 2026 · Phase 1 video upload

- [x] Added the project upload form, byte-transfer progress, metadata-checking state, and retry with the same upload ID.
- [x] Added multipart project creation and paginated project listing. The dashboard shows the live upload count and original video metadata.
- [x] Added Flyway V2 for one original video per project in `clipping_db`. The first run applied V2 and a separate backend instance validated both migrations. No commands modified `devdb`.
- [x] Added configurable local storage and upload limits. FFprobe 9.0.2 was already installed, so this milestone added no package dependencies.
- [x] Passed all 19 Maven tests and the TypeScript and Vite production build.
- [x] Uploaded a generated MP4 through the browser and verified its 320 × 180 resolution, 1.5-second duration, 24 fps, file size, H.264 video and AAC audio. The saved file matched the fixture byte for byte and the project was visible from a separate backend instance.
- [x] Verified upload replay returns the original record, a conflicting upload ID returns HTTP 409, invalid files return HTTP 415 or 422, and exceeding the configured file limit returns HTTP 413. Rejected uploads left no project records.
- [x] Updated local setup, storage settings and API documentation.

The synthetic **Phase 1 smoke test** project and its original are available in the local workspace. Upload retries resend the source file. Metadata extraction is bounded by the configured timeout. Project status is **Video ready** after the original and metadata are saved.

Phase 1 is complete. The next milestone is Phase 2 transcription with local Whisper and timestamped segments, pending owner approval.

## 21 November 2026 · Phase 2 local transcription

- [x] Installed whisper.cpp 1.9.5 through Homebrew and verified the multilingual base Q5 model checksum. The model is stored in the ignored local data directory.
- [x] Applied Flyway V3 in `clipping_db` for durable work, transcript headers, and ordered segments. The worker extracts mono PCM using FFmpeg, runs Whisper locally, and commits text and timestamps together.
- [x] Added project transcript controls for language, status, cancellation, and retry. The dashboard counts queued and running jobs.
- [x] Passed all 26 Maven tests and the TypeScript and Vite production build.
- [x] Started transcription in the browser using a generated English video. Automatic detection selected English. The browser displayed both timestamp ranges, 00:00.000–00:07.400 and 00:07.400–00:09.680. The saved original remained byte-identical and a repeated start request returned the existing transcript.
- [x] Verified cancellation and retry against the synthetic Phase 1 clip. Its test job remains cancelled, with no transcript or active work.
- [x] Updated startup, storage, runtime configuration, and API documentation.

The generated **Phase 2 English sample** remains in Recent projects. Hindi, Hinglish, Marathi, and longer recordings still need accuracy and performance evaluation. The next milestone is Phase 3 AI Highlights, pending owner approval.

## 21 November 2026 · Hindi and Hinglish retest

- The original Hindi-labelled transcript rendered Hindi speech in Urdu script. The Base model still did that when Hindi and a Devanagari prompt were supplied explicitly.
- On the same 75-second excerpt, Whisper Small produced repeated text. Whisper Large v3 Turbo produced Devanagari for Hindi and retained English terms. The tested model is checksum-verified and Git-ignored. Hindi / Hinglish now prefers Turbo when installed.
- Flyway V4 preserves earlier transcript revisions. Verified on the synthetic English project that a rerun keeps the previous result visible and that a duplicate request for a completed revision does not enqueue another job.
- Passed 27 backend tests and the frontend production build.
- Reran the full 8:08 test video using Hindi / Hinglish. Revision 3 contains 129 ordered segments through 00:08:07.880. Verified Devanagari and Latin text in the dashboard and confirmed the original video checksum was unchanged. Earlier transcript revisions remain recorded.

## 22 November 2026 · Phase 3 AI Highlights

- [2026-11-22 16:03 IST] Integrated the local Ollama provider using Qwen 3.5 9B and structured candidate suggestions. The backend maps segment indices to saved timestamps, rejects invalid ranges and suggestions over 60 seconds, deduplicates overlaps, and ranks the results.
- [x] Applied Flyway V5 to store candidates and review decisions per transcript revision.
- [x] Verified six suggestions on the current revision of **test**. In the browser, preview playback worked, selections and timing edits persisted on reopen, and the verification edit and selection were cleared. The source endpoint returned HTTP 206 for a byte-range request.
- [x] Passed all 30 backend tests and the frontend production build. Left the frontend, backend, and local Ollama running.

Phase 4 Clip Generation is the next roadmap milestone.
