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
