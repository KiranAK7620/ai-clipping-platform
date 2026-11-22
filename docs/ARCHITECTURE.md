# ClipTova architecture

ClipTova uses a modular monolith. One Spring Boot process exposes the API and owns the database connection. Business capabilities can grow in their own packages inside that process. The React application uses the same-origin `/api` path through a Vite proxy during local development.

```text
frontend/
  src/app/                 Studio shell and navigation
  src/features/dashboard/ Dashboard, categories, upload form and project list
  src/features/transcription/ Job controls, status and transcript segments
  src/features/highlights/ Ranked suggestions, preview, timing edits and decisions
  src/services/            Typed API clients and React Query hooks
  src/theme/               Material UI theme with locally served Inter
backend/
  src/main/java/com/cliptova/
    common/                Shared HTTP exception handling
    health/                Health controller, response DTO and connection check
    project/               Project API, service, JPA entities and repository
    transcription/         Job API, database queue, worker and Whisper adapter
    highlight/             LLM adapter, validation, ranking and assisted selection
    video/                 File staging, filename validation and FFprobe
  src/main/resources/
    db/migration/          Flyway schema history
  src/test/                Health, project API and video metadata tests
```

## Frontend

Material UI supplies controls and dialogs. React Query manages health, the paginated project list, upload and transcription state. The upload dialog uses XMLHttpRequest for byte progress. A successful upload refreshes the project list and moves the browser to Recent projects. The transcript dialog polls while its job is queued or running. The uploaded-video and active-job counts come from PostgreSQL.

## Backend

The health controller delegates the PostgreSQL connection test to a component that borrows and validates a pooled JDBC connection. The response uses a typed DTO and cache prevention so each check reflects current connectivity. Other HTTP errors use Spring's Problem Details support, with a sanitized response for unexpected exceptions.

The project module uses Bean Validation at the HTTP boundary and Spring Data JPA for reads. Flyway owns schema changes, and Hibernate validates the mappings. The project service stages the file and probes it before opening a database transaction. It moves the staged file to its final UUID-based name before persisting the project and video together. A known rollback removes the saved file. A process crash or uncertain commit can leave an unreferenced original for manual reconciliation.

FFprobe receives an argument list and a server-generated local path. It uses a restricted set of demuxers, file-only I/O, and a timeout. The stored display name is separate from the storage key. Only project and video metadata are returned by the API.

The transcription worker claims one job at a time per backend instance. Its PostgreSQL lease expires after 30 seconds without renewal, so interrupted jobs can be reclaimed. Cancellation clears the lease and the worker terminates its local child process at the next check. Only the current lease holder can save results. A rerun keeps the previous transcript available. Saving the new revision, its segments, and completed state happens in one transaction.

FFmpeg extracts the first audio track to temporary mono 16 kHz PCM. whisper.cpp reads that file with a local multilingual model and returns JSON timestamps in milliseconds. ClipTova validates those offsets against the source duration and stores ordered segments. The worker cleans up audio and JSON after each normal attempt. Source files are preserved.

The highlights module sends overlapping windows of saved transcript segments to Ollama on loopback. The adapter requests structured JSON with segment indices. The service maps those indices to saved timestamps, rejects invalid ranges, suppresses overlapping suggestions, and ranks the remaining candidates by score and duration. Flyway V5 stores the candidates and review decisions per transcript revision. Original-video preview uses HTTP byte ranges.

Both development servers bind to loopback by default.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/health` | Checks PostgreSQL connectivity. |
| `GET /api/projects?page=0` | Lists up to 20 recent projects with upload and active-job counts, upload limit in bytes, and the next-page indicator. |
| `POST /api/projects` | Accepts multipart `projectId`, `name`, `category` and `file`. Returns HTTP 201 with uploaded metadata, or 200 for a retry with the same project ID, name and category. |
| `GET /api/projects/{projectId}/transcription` | Returns status and the last saved transcript with timestamps and revision number. |
| `POST /api/projects/{projectId}/transcription?language=AUTO` | Queues a new attempt or returns the active or completed job. Pass the current `revision` to rerun a completed job. Supports `AUTO`, `ENGLISH`, `HINDI`, and `MARATHI`. |
| `DELETE /api/projects/{projectId}/transcription` | Cancels a queued or running job and returns its current state. |
| `GET /api/projects/{projectId}/highlights` | Returns the current transcript revision and its saved suggestions. |
| `POST /api/projects/{projectId}/highlights` | Runs local suggestion generation or returns suggestions already saved for the current revision. |
| `PATCH /api/projects/{projectId}/highlights/{id}` | Saves start and end milliseconds and the review decision. |
| `GET /api/projects/{projectId}/highlights/source` | Streams the original video for preview, with byte-range support. |

The client generates one project ID per upload attempt and keeps it across unchanged retries. Reusing an ID with a different project name or category returns HTTP 409. Unsupported extensions return HTTP 415, unreadable videos return HTTP 422, and uploads over the configured limit return HTTP 413.
