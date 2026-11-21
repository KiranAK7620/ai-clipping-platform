# ClipTova

**Every long video has a short story.**

ClipTova is a local-first studio for turning long-form videos into short clips. Sprint 1 delivers the dashboard, application shell, backend health check, and PostgreSQL foundation.

## Prerequisites

- Java 17 and Maven 3.9.11
- Node.js 22.12+ within the Node 22 release line and npm
- Docker running the existing `postgres-dev` container on host port `5434`
- Application database `clipping_db` and login `clipping_user` as described in [Database setup](docs/DATABASE.md)
- FFmpeg is part of the checked development environment. The Sprint 1 application does not invoke it.

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

The dashboard starts with zero activity and an empty project list. Its New Project dialog previews category selection and the future upload area. System status is live.

## Project documentation

- [Product plan](Plan.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Database setup](docs/DATABASE.md)
- [Development log](docs/DEVELOPMENT_LOG.md)
