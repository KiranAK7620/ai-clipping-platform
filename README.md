# ClipTova

**Every long video has a short story.**

ClipTova is a local-first studio for turning long-form videos into short clips. The dashboard supports local uploads, video metadata, timestamped speech transcription, AI highlight selection, and vertical MP4 export.

## Prerequisites

- Java 17 and Maven 3.9.11
- Node.js 22.12+ within the Node 22 release line and npm
- Docker running the existing `postgres-dev` container on host port `5434`
- Application database `clipping_db` and login `clipping_user` as described in [Database setup](docs/DATABASE.md)
- FFprobe on the backend's PATH, or `CLIPTOVA_FFPROBE` set to its executable path

## Run locally

In the first terminal, from the repository root:

```sh
cd backend
# If backend/.env already exists, keep it and skip the copy.
cp -n .env.example .env
# Set DB_PASSWORD in .env before starting.
set -a
. ./.env
set +a
mvn spring-boot:run
```

In the second terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open [ClipTova Studio](http://127.0.0.1:5173). Vite forwards `/api` to the backend on port `8080`. When changing the backend port, set `BACKEND_URL` in the frontend shell before starting Vite.

## Verify

```sh
curl --fail-with-body http://127.0.0.1:8080/api/health
curl --fail-with-body http://127.0.0.1:5173/api/health
mvn -f backend/pom.xml verify
npm --prefix frontend run build
```

`GET /api/health` returns HTTP 200 and `{"status":"UP","database":"UP"}` when PostgreSQL is reachable. If the database becomes unavailable while the API is running, it returns HTTP 503 with both fields set to `DOWN`. A database connection is also required during startup for Flyway.

## Upload a video

Open **New project**, enter a name, choose a category, and drop or browse for an MP4, MOV, MKV, or WebM file you own or have permission to use. The default file limit is 512 MiB. ClipTova shows transfer progress and checks the metadata before the project appears in **Recent projects**. The project shows duration, coded resolution, file size, frame rate, and codecs when reported.

The backend stores the original bytes in `backend/data/originals` when run from the backend directory. Set an absolute `CLIPTOVA_ORIGINALS_DIRECTORY` in `backend/.env` to use a different directory. The [database guide](docs/DATABASE.md) lists the storage, upload, and probe settings.

## Transcribe a video locally

Install FFmpeg and whisper.cpp. On macOS with Homebrew, run `brew install whisper.cpp`. From the repository root, download the multilingual model into the ignored data directory:

```sh
mkdir -p backend/data/models
curl --fail --location --retry 2 \
  'https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin' \
  -o backend/data/models/ggml-base-q5_1.bin.download
printf '%s\n' '422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898  backend/data/models/ggml-base-q5_1.bin.download' |
  shasum -a 256 -c &&
  mv backend/data/models/ggml-base-q5_1.bin.download backend/data/models/ggml-base-q5_1.bin
```

From **Recent projects**, open the workspace for a saved video with audio and choose **Transcript**. Select automatic language detection, English, Hindi / Hinglish, or Marathi, then choose **Start transcription**. The worker runs in the background on this machine. Return to the Transcript view to see the current stage and saved timestamps. Search the transcript or click a timestamp to seek the source video. Queued and running jobs can be cancelled, and failed or cancelled jobs can be retried. For a completed video, choose a language and **Transcribe again**. The previous transcript stays visible while the new attempt runs. The default limit is 60 minutes per video.

For Hindi / Hinglish, ClipTova prefers [Whisper Large v3 Turbo](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-large-v3-turbo-q5_0.bin). Download `ggml-large-v3-turbo-q5_0.bin` to `backend/data/models` or point `CLIPTOVA_WHISPER_HINDI_MODEL` to it. Verify the SHA-256 is `394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2`. If Turbo is unavailable, ClipTova falls back to Base, which may render Hindi in Urdu script or miss code-switching.

See the [whisper.cpp repository](https://github.com/ggml-org/whisper.cpp) and the [Base model file](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-base-q5_1.bin) for runtime and model details.

## Find and select highlights

Start [Ollama](https://ollama.com/) with the local Qwen model available. Run `OLLAMA_NO_CLOUD=1 ollama serve` in a third terminal if Ollama is not already running. On a fresh installation, download the model with `ollama pull qwen3.5:9b`. ClipTova connects to Ollama on loopback port `11434`. Set `CLIPTOVA_HIGHLIGHTS_MODEL` or `CLIPTOVA_OLLAMA_PORT` in `backend/.env` to use a different installed model or local port.

From the project workspace, open **Highlights** on a transcribed video and choose **Generate highlights**. The local model analyzes the saved transcript, which can take several minutes on a longer recording. Review the ranked suggestions, preview their ranges in the original video, and adjust the start and end times if needed. Clip ranges display `HH:MM:SS` from the start of the original video. Saved cuts retain millisecond precision, and you can type a fractional second for a precise edit. **Select** and **Reject** save your decisions for that transcript revision. Preview playback requires a source format the browser can play.

## Generate and download a clip

In **Highlights**, open a suggestion and drag the cut handles, choose **Zoom to cut** for finer adjustments, or expand **Exact timecodes**. Choose **Current cut** for the exact range, or use a **30**, **45**, or **60 seconds** preset. A preset starts at the saved start time when there is enough source footage. Near the end, it shifts the range earlier to fit. A source shorter than the preset uses its full duration.

Turn **Captions** on or off. With captions on, set the font family and size, text and background colors, opacity, and placement. Open **More caption options** for alignment, bold, and outline. **Portrait preview** shows the caption layout before export. Select **Generate clip** when ready. This saves the selection and opens **Exports**, which tracks renders and provides **Preview MP4** and **Download MP4** when ready. The export fits the full source frame over a blurred background, burns in captions from its transcript revision when enabled, and encodes a 720 × 1280 H.264 MP4 with AAC when the source has audio. Each export saves its caption settings, which you can reuse from **Exports**. To reuse a style across projects, open **Captions → Caption themes**, enter a name, and choose **Save theme**. Open the same panel in another project and select the theme to apply it. Use the pencil beside a theme to change its name or caption settings, then choose **Save changes**.

FFmpeg needs the `libx264` and AAC encoders. Caption rendering uses fonts installed on the backend host. Install a font covering your source language if glyphs are missing. Clips are stored in the ignored `backend/data/clips` directory by default.

## Generate clips automatically

Upload a video with audio and open **Automatic clips** in its workspace. Choose one to five clips, a 30-, 45-, or 60-second duration, a language for new transcription, and any caption settings. Select **Generate automatic clips**. The local worker reuses an available transcript and ranked highlights or generates them with Whisper and Ollama, then queues distinct clips for FFmpeg.

**Batch results** shows the current stage and the number of clips ready. You can navigate away and return to check the latest batch. Each ready clip has an MP4 preview and download and also appears in **Exports**. A batch can finish with fewer clips when the source has too few distinct highlights or an export fails.

## Project documentation

- [Product plan](Plan.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Database setup](docs/DATABASE.md)
- [Development log](docs/DEVELOPMENT_LOG.md)
