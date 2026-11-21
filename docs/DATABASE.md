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

On backend startup, Flyway applies versioned SQL from `backend/src/main/resources/db/migration`. The first migration creates the `projects` table with a UUID identifier, name, supported category and creation time. The creation-time index supports future recent-project queries. Flyway records applied migrations in `public.flyway_schema_history`. Add a new migration for subsequent schema changes. Flyway clean is disabled.

Run these read-only checks while connected to `clipping_db`:

```sql
SELECT current_database(), current_user;
SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;
```
