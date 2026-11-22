package com.cliptova.clip;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CaptionThemeStore {
    private final JdbcClient jdbc;
    private final CaptionSettingsJson json;

    CaptionThemeStore(JdbcClient jdbc, CaptionSettingsJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    List<Theme> list() {
        return jdbc.sql("SELECT id, name, caption_settings FROM caption_themes ORDER BY lower(name), id")
                .query(this::readTheme).list();
    }

    Optional<Theme> save(String name, CaptionSettings captions) {
        return jdbc.sql("""
                INSERT INTO caption_themes (name, caption_settings) VALUES (:name, CAST(:captions AS jsonb))
                ON CONFLICT (lower(name)) DO NOTHING
                RETURNING id, name, caption_settings
                """).param("name", name).param("captions", json.encode(captions))
                .query(this::readTheme).optional();
    }

    Optional<Theme> update(UUID id, String name, CaptionSettings captions) {
        return jdbc.sql("""
                UPDATE caption_themes SET name = :name, caption_settings = CAST(:captions AS jsonb)
                WHERE id = :id RETURNING id, name, caption_settings
                """).param("id", id).param("name", name).param("captions", json.encode(captions))
                .query(this::readTheme).optional();
    }

    private Theme readTheme(ResultSet row, int index) throws SQLException {
        return new Theme(row.getObject("id", UUID.class), row.getString("name"),
                json.decode(row.getString("caption_settings")));
    }

    record Theme(UUID id, String name, CaptionSettings captions) {}
}
