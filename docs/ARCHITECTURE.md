# ClipTova architecture

ClipTova uses a modular monolith. One Spring Boot process exposes the API and owns the database connection. Business capabilities can grow in their own packages inside that process. The React application uses the same-origin `/api` path through a Vite proxy during local development.

```text
frontend/
  src/app/                 Studio shell and navigation
  src/features/dashboard/ Dashboard, metrics, categories and project preview
  src/services/            Typed health API client and React Query hook
  src/theme/               Material UI theme with locally served Inter
backend/
  src/main/java/com/cliptova/
    common/                Shared HTTP exception handling
    health/                Health controller, response DTO and connection check
  src/main/resources/
    db/migration/          Flyway schema history
  src/test/                JDBC health and HTTP contract tests
```

## Frontend

Material UI supplies accessible controls and dialogs. CSS handles responsive layout and the timeline illustration. Sidebar links navigate to sections of the dashboard. React Query manages the live health result and refreshes it every 30 seconds while the page is active. The dialog and navigation use local React state, so Sprint 1 has no Zustand store. Project and processing counts represent an empty workspace.

## Backend

The health controller delegates the PostgreSQL connection test to a component that borrows and validates a pooled JDBC connection. The response uses a typed DTO and cache prevention so each check reflects current connectivity. Other HTTP errors use Spring's Problem Details support, with a sanitized response for unexpected exceptions.

Spring Data JPA, Hibernate and Bean Validation are configured for future domain modules. Flyway owns schema changes and Hibernate uses validation mode. Add persistence and HTTP DTOs in their domain module as each capability is implemented.

Both development servers bind to loopback by default. The current API exposes only the local foundation health check. Video uploads, processing, AI providers and authentication belong to later phases of the product plan.
