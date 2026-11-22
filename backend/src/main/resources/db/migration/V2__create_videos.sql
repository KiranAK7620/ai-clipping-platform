CREATE TABLE videos (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL UNIQUE REFERENCES projects (id),
    original_name varchar(255) NOT NULL,
    storage_key varchar(48) NOT NULL UNIQUE,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    duration_seconds double precision NOT NULL CHECK (duration_seconds > 0),
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    frame_rate double precision CHECK (frame_rate > 0),
    video_codec varchar(64) NOT NULL,
    audio_codec varchar(64)
);
