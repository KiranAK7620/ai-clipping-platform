CREATE TABLE transcription_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id uuid NOT NULL UNIQUE REFERENCES videos (id),
    language varchar(12) NOT NULL CHECK (language IN ('AUTO', 'ENGLISH', 'HINDI', 'MARATHI')),
    status varchar(20) NOT NULL CHECK (status IN
        ('QUEUED', 'EXTRACTING_AUDIO', 'TRANSCRIBING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    queued_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    error varchar(240),
    CHECK ((lease_token IS NOT NULL AND lease_until IS NOT NULL AND status IN ('EXTRACTING_AUDIO', 'TRANSCRIBING'))
        OR (lease_token IS NULL AND lease_until IS NULL AND status NOT IN ('EXTRACTING_AUDIO', 'TRANSCRIBING')))
);

CREATE INDEX transcription_jobs_pending_idx ON transcription_jobs (queued_at, id)
    WHERE status IN ('QUEUED', 'EXTRACTING_AUDIO', 'TRANSCRIBING');

CREATE TABLE transcripts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id uuid NOT NULL UNIQUE REFERENCES transcription_jobs (id),
    language varchar(12),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE transcript_segments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transcript_id uuid NOT NULL REFERENCES transcripts (id),
    position integer NOT NULL CHECK (position >= 0),
    start_ms bigint NOT NULL CHECK (start_ms >= 0),
    end_ms bigint NOT NULL CHECK (end_ms > start_ms),
    text text NOT NULL CHECK (btrim(text) <> ''),
    UNIQUE (transcript_id, position)
);
