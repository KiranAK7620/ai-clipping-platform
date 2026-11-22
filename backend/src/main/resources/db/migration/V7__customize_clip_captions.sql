ALTER TABLE clips ADD COLUMN caption_settings jsonb;

UPDATE clips
SET caption_settings = jsonb_build_object(
    'enabled', true,
    'textColor', '#ffffff',
    'backgroundColor', '#070a14',
    'backgroundOpacity', CASE WHEN caption_style = 'BOXED' THEN 86 ELSE 0 END,
    'fontFamily', 'SANS_SERIF',
    'fontSize', 38,
    'bold', true,
    'position', 'BOTTOM',
    'alignment', 'CENTER',
    'outlineColor', '#070a14',
    'outlineWidth', CASE WHEN caption_style = 'BOXED' THEN 0 ELSE 7 END
);

ALTER TABLE clips
    DROP COLUMN caption_style,
    ALTER COLUMN caption_settings SET NOT NULL,
    ADD CONSTRAINT clips_render_options_key UNIQUE (highlight_id, start_ms, end_ms, caption_settings);
