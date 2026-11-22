CREATE TABLE caption_themes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(60) NOT NULL CHECK (btrim(name) <> ''),
    caption_settings jsonb NOT NULL CHECK (jsonb_typeof(caption_settings) = 'object')
);

CREATE UNIQUE INDEX caption_themes_name_idx ON caption_themes (lower(name));
