CREATE TABLE projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    category varchar(16) NOT NULL CHECK (category IN ('PODCAST', 'GAMING', 'EDUCATION', 'INTERVIEW')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX projects_created_at_idx ON projects (created_at DESC);
