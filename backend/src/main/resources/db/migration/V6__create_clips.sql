CREATE TABLE clips (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES projects (id),
    highlight_id uuid NOT NULL REFERENCES highlight_candidates (id),
    title varchar(120) NOT NULL,
    start_ms bigint NOT NULL CHECK (start_ms >= 0),
    end_ms bigint NOT NULL CHECK (end_ms > start_ms AND end_ms - start_ms <= 60000),
    caption_style varchar(12) NOT NULL CHECK (caption_style IN ('OUTLINED', 'BOXED')),
    status varchar(12) NOT NULL CHECK (status IN ('QUEUED', 'RENDERING', 'READY', 'FAILED')),
    queued_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    file_key varchar(77) UNIQUE,
    error varchar(240),
    CHECK ((status = 'RENDERING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RENDERING' AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK ((status = 'READY') = (file_key IS NOT NULL)),
    UNIQUE (highlight_id, start_ms, end_ms, caption_style)
);

CREATE INDEX clips_pending_idx ON clips (queued_at, id) WHERE status IN ('QUEUED', 'RENDERING');
CREATE INDEX clips_project_idx ON clips (project_id, created_at DESC, id DESC);
