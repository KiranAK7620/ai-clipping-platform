# ClipTova database

The local application uses PostgreSQL in the existing `postgres-dev` Docker container. It connects to `localhost:5434/clipping_db` as `clipping_user`. The database name is fixed in the JDBC URL so the normal startup configuration cannot target `devdb`.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL hostname |
| `DB_PORT` | `5434` | Host port mapped to PostgreSQL |
| `DB_USER` | `clipping_user` | Application login |
| `DB_PASSWORD` | Required | Application login password |

Use `backend/.env.example` as the shell environment template. Keep the actual password in a local ignored `backend/.env` or supply the variables through your shell.

## Provisioning

Provision the application database and role only with the repository owner's approval. On an already configured machine, reuse the existing login and database. On a fresh machine, an administrator can run the following in `psql` connected to the PostgreSQL maintenance database:

```sql
CREATE ROLE clipping_user LOGIN;
\password clipping_user
CREATE DATABASE clipping_db OWNER clipping_user;
REVOKE CONNECT ON DATABASE clipping_db FROM PUBLIC;
```

Set the application password when prompted and use the same value in `backend/.env`. For this local setup the role owns only its application database and has the access Flyway needs to create schema objects.

## Migrations

On startup, Flyway applies SQL from `backend/src/main/resources/db/migration`. V1 creates `projects`. V2 adds `videos` with one original and its metadata per project. V3 adds `transcription_jobs`, `transcripts`, and ordered `transcript_segments`. V4 keeps successive transcript revisions and assigns revision 1 to existing transcripts. V5 adds ranked highlight candidates, editable ranges, and review decisions for each transcript revision. V6 adds the clip render queue and output file references. V7 saves caption settings on each export and migrates the earlier outlined and boxed styles. V8 stores reusable caption themes in a shared library. Transcript and highlight offsets are stored in milliseconds. Flyway records applied migrations in `public.flyway_schema_history`, and Flyway clean is disabled.

Run these read-only checks while connected to `clipping_db`:

```sql
SELECT current_database(), current_user;
SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;
```

## Local video storage

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `CLIPTOVA_ORIGINALS_DIRECTORY` | `./data/originals` | Directory for staged and saved originals, relative to the backend working directory unless absolute |
| `CLIPTOVA_MAX_VIDEO_SIZE` | `512MB` | Maximum source video size |
| `CLIPTOVA_MAX_REQUEST_SIZE` | `513MB` | Maximum multipart request size, including form overhead |
| `CLIPTOVA_FFPROBE` | `ffprobe` | FFprobe executable on PATH or an absolute path |
| `CLIPTOVA_FFPROBE_TIMEOUT` | `15s` | Time budget for metadata extraction |

When raising the file limit, leave room for multipart overhead in the request limit. Use the same originals directory across backend restarts. To back up projects, copy this directory and back up `clipping_db` together. The default data directory is Git-ignored.

## Local transcription

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `CLIPTOVA_FFMPEG` | `ffmpeg` | FFmpeg executable for audio extraction |
| `CLIPTOVA_WHISPER` | `whisper-cli` | whisper.cpp executable |
| `CLIPTOVA_WHISPER_MODEL` | `./data/models/ggml-base-q5_1.bin` | Local multilingual model relative to the backend working directory unless absolute |
| `CLIPTOVA_WHISPER_HINDI_MODEL` | `./data/models/ggml-large-v3-turbo-q5_0.bin` | Optional model preferred when the spoken language is set to Hindi / Hinglish |
| `CLIPTOVA_TRANSCRIPTION_MAX_DURATION` | `60m` | Maximum source duration and audio size budget |
| `CLIPTOVA_AUDIO_EXTRACT_TIMEOUT` | `3m` | Wall-clock budget for FFmpeg |
| `CLIPTOVA_WHISPER_TIMEOUT` | `45m` | Wall-clock budget for Whisper |
| `CLIPTOVA_WHISPER_THREADS` | `4` | Whisper thread count |

## Clip generation

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `CLIPTOVA_CLIPS_DIRECTORY` | `./data/clips` | Generated MP4 storage, relative to the backend working directory unless absolute |
| `CLIPTOVA_CLIP_RENDER_TIMEOUT` | `5m` | Wall-clock budget for each FFmpeg render |

Back up this directory alongside the originals directory and `clipping_db` to preserve generated exports.
