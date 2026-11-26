CREATE TABLE automatic_clip_runs (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES projects (id),
    clip_count integer NOT NULL CHECK (clip_count BETWEEN 1 AND 5),
    duration varchar(12) NOT NULL CHECK (duration IN ('SECONDS_30', 'SECONDS_45', 'SECONDS_60')),
    language varchar(12) NOT NULL CHECK (language IN ('AUTO', 'ENGLISH', 'HINDI', 'MARATHI')),
    caption_settings jsonb NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('QUEUED', 'TRANSCRIBING', 'ANALYZING', 'RENDERING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    next_check timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    message varchar(240),
    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK (status IN ('QUEUED', 'TRANSCRIBING', 'ANALYZING', 'RENDERING') OR lease_token IS NULL)
);

CREATE UNIQUE INDEX automatic_clip_runs_active_project_idx ON automatic_clip_runs (project_id)
    WHERE status IN ('QUEUED', 'TRANSCRIBING', 'ANALYZING', 'RENDERING');
CREATE INDEX automatic_clip_runs_project_idx ON automatic_clip_runs (project_id, created_at DESC, id DESC);

CREATE TABLE automatic_clip_run_items (
    run_id uuid NOT NULL REFERENCES automatic_clip_runs (id),
    clip_id uuid NOT NULL REFERENCES clips (id),
    position integer NOT NULL CHECK (position > 0),
    PRIMARY KEY (run_id, clip_id),
    UNIQUE (run_id, position)
);
