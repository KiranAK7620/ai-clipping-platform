package com.cliptova.highlight;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class HighlightStore {
    private static final RowMapper<Highlight> HIGHLIGHT = (row, index) -> new Highlight(
            row.getObject("id", UUID.class), row.getInt("rank"), row.getLong("start_ms"), row.getLong("end_ms"),
            row.getString("title"), row.getString("hook"), row.getString("description"), row.getString("category"),
            row.getString("reason"), row.getInt("quality_score"), Highlight.Decision.valueOf(row.getString("decision")));
    private final JdbcClient jdbc;

    HighlightStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Source> source(UUID projectId) {
        return jdbc.sql("""
                SELECT p.category, v.storage_key, v.duration_seconds, t.id AS transcript_id, t.revision, t.language
                FROM projects p
                JOIN videos v ON v.project_id = p.id
                LEFT JOIN transcription_jobs j ON j.video_id = v.id
                LEFT JOIN LATERAL (
                    SELECT id, revision, language FROM transcripts WHERE job_id = j.id ORDER BY revision DESC LIMIT 1
                ) t ON true
                WHERE p.id = :project
                """).param("project", projectId).query((row, index) -> new Source(
                        row.getString("category"), row.getString("storage_key"),
                        (long) Math.floor(row.getDouble("duration_seconds") * 1000),
                        row.getObject("transcript_id", UUID.class), row.getObject("revision", Integer.class),
                        row.getString("language"))).optional();
    }

    List<Highlight> list(UUID transcriptId) {
        return jdbc.sql("SELECT * FROM highlight_candidates WHERE transcript_id = :id ORDER BY rank")
                .param("id", transcriptId).query(HIGHLIGHT).list();
    }

    @Transactional
    List<Highlight> save(UUID transcriptId, List<Highlight> ranked) {
        // Serialize writes for this immutable transcript across backend processes.
        jdbc.sql("SELECT id FROM transcripts WHERE id = :id FOR UPDATE")
                .param("id", transcriptId).query(UUID.class).single();
        var existing = list(transcriptId);
        if (!existing.isEmpty()) {
            return existing;
        }
        for (Highlight item : ranked) {
            jdbc.sql("""
                    INSERT INTO highlight_candidates
                        (transcript_id, rank, start_ms, end_ms, title, hook, description, category, reason, quality_score)
                    VALUES (:transcript, :rank, :start, :end, :title, :hook, :description, :category, :reason, :score)
                    """).param("transcript", transcriptId).param("rank", item.rank())
                    .param("start", item.startMs()).param("end", item.endMs()).param("title", item.title())
                    .param("hook", item.hook()).param("description", item.description())
                    .param("category", item.category()).param("reason", item.reason())
                    .param("score", item.qualityScore()).update();
        }
        return list(transcriptId);
    }

    Optional<Highlight> update(UUID transcriptId, UUID id, long startMs, long endMs, Highlight.Decision decision) {
        return jdbc.sql("""
                UPDATE highlight_candidates SET start_ms = :start, end_ms = :end, decision = :decision
                WHERE transcript_id = :transcript AND id = :id
                RETURNING *
                """).param("start", startMs).param("end", endMs).param("decision", decision.name())
                .param("transcript", transcriptId).param("id", id).query(HIGHLIGHT).optional();
    }

    record Source(String category, String storageKey, long durationMs, UUID transcriptId, Integer revision,
                  String language) {}
}
