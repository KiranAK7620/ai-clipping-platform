ALTER TABLE transcripts
    ADD COLUMN revision integer NOT NULL DEFAULT 1 CHECK (revision > 0),
    DROP CONSTRAINT transcripts_job_id_key,
    ADD CONSTRAINT transcripts_job_revision_key UNIQUE (job_id, revision);
