CREATE TABLE highlight_candidates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transcript_id uuid NOT NULL REFERENCES transcripts (id),
    rank integer NOT NULL CHECK (rank > 0),
    start_ms bigint NOT NULL CHECK (start_ms >= 0),
    end_ms bigint NOT NULL CHECK (end_ms > start_ms),
    title varchar(120) NOT NULL,
    hook varchar(240) NOT NULL,
    description varchar(400) NOT NULL,
    category varchar(32) NOT NULL,
    reason varchar(400) NOT NULL,
    quality_score integer NOT NULL CHECK (quality_score BETWEEN 0 AND 100),
    decision varchar(12) NOT NULL DEFAULT 'PENDING' CHECK (decision IN ('PENDING', 'SELECTED', 'REJECTED')),
    UNIQUE (transcript_id, rank)
);
