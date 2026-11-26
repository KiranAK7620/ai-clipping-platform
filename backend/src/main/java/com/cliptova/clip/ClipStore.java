package com.cliptova.clip;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ClipStore {
    private final JdbcClient jdbc;
    private final CaptionSettingsJson json;

    ClipStore(JdbcClient jdbc, CaptionSettingsJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Clip readClip(ResultSet row, int index) throws SQLException {
        return new Clip(row.getObject("id", UUID.class), row.getObject("highlight_id", UUID.class),
                row.getString("title"), row.getLong("start_ms"), row.getLong("end_ms"),
                json.decode(row.getString("caption_settings")), Clip.Status.valueOf(row.getString("status")), row.getString("error"));
    }

    @Transactional
    Clip enqueue(UUID project, UUID highlight, Clip.Duration duration, CaptionSettings captions) {
        var source = findSource(project, highlight).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Highlight not found."));
        if (!source.currentRevision() || !"SELECTED".equals(source.decision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Select a highlight from the current transcript first.");
        }
        var range = duration.resolve(source.startMs(), source.endMs(), source.durationMs());
        if (!range.validFor(source.durationMs())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Choose a valid range of up to 60 seconds or use a duration preset.");
        }
        return insert(project, highlight, source, range, captions);
    }

    // Invoked only by the automatic batch transaction. Manual export still requires an explicit selection.
    Optional<Clip> enqueueAutomatic(UUID project, UUID highlight, Clip.Duration duration, CaptionSettings captions,
                                    List<Clip.Range> existing) {
        var source = findSource(project, highlight).orElse(null);
        if (source == null || "REJECTED".equals(source.decision())) return Optional.empty();
        var range = duration.resolve(source.startMs(), source.endMs(), source.durationMs());
        if (!range.validFor(source.durationMs()) || existing.stream().anyMatch(range::mostlyOverlaps)) {
            return Optional.empty();
        }
        return Optional.of(insert(project, highlight, source, range, captions));
    }

    private Optional<Source> findSource(UUID project, UUID highlight) {
        return jdbc.sql("""
                SELECT h.title, h.start_ms, h.end_ms, h.decision, v.duration_seconds,
                    t.revision = (SELECT max(revision) FROM transcripts WHERE job_id = t.job_id) AS current_revision
                FROM highlight_candidates h
                JOIN transcripts t ON t.id = h.transcript_id
                JOIN transcription_jobs j ON j.id = t.job_id
                JOIN videos v ON v.id = j.video_id
                WHERE v.project_id = :project AND h.id = :highlight
                FOR UPDATE OF h
                """).param("project", project).param("highlight", highlight).query((row, index) -> new Source(
                        row.getString("title"), row.getLong("start_ms"), row.getLong("end_ms"),
                        (long) Math.floor(row.getDouble("duration_seconds") * 1000),
                        row.getBoolean("current_revision"), row.getString("decision"))).optional();
    }

    private Clip insert(UUID project, UUID highlight, Source source, Clip.Range range, CaptionSettings captions) {
        return jdbc.sql("""
                INSERT INTO clips AS existing (project_id, highlight_id, title, start_ms, end_ms, caption_settings, status)
                VALUES (:project, :highlight, :title, :start, :end, CAST(:captions AS jsonb), 'QUEUED')
                ON CONFLICT ON CONSTRAINT clips_render_options_key
                DO UPDATE SET status = CASE WHEN existing.status = 'FAILED' THEN 'QUEUED' ELSE existing.status END,
                    queued_at = CASE WHEN existing.status = 'FAILED' THEN now() ELSE existing.queued_at END,
                    error = NULL
                RETURNING *
                """).param("project", project).param("highlight", highlight).param("title", source.title())
                .param("start", range.startMs()).param("end", range.endMs()).param("captions", json.encode(captions))
                .query(this::readClip).single();
    }

    List<Clip> list(UUID project) {
        return jdbc.sql("SELECT * FROM clips WHERE project_id = :project ORDER BY created_at DESC, id DESC")
                .param("project", project).query(this::readClip).list();
    }

    Optional<String> findFile(UUID project, UUID clip) {
        return jdbc.sql("SELECT file_key FROM clips WHERE project_id = :project AND id = :clip AND status = 'READY'")
                .param("project", project).param("clip", clip).query(String.class).optional();
    }

    Optional<Claim> claim() {
        return jdbc.sql("""
                WITH pending AS (
                    SELECT id FROM clips WHERE status = 'QUEUED' OR (status = 'RENDERING' AND lease_until <= now())
                    ORDER BY queued_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE clips c SET status = 'RENDERING', lease_token = gen_random_uuid(),
                    lease_until = now() + interval '30 seconds'
                FROM pending, highlight_candidates h, transcripts t, transcription_jobs j, videos v
                WHERE c.id = pending.id AND h.id = c.highlight_id AND t.id = h.transcript_id
                    AND t.job_id = j.id AND v.id = j.video_id
                RETURNING c.id, c.lease_token, h.transcript_id, v.storage_key,
                    c.start_ms, c.end_ms, c.caption_settings
                """).query((row, index) -> new Claim(row.getObject("id", UUID.class),
                        row.getObject("lease_token", UUID.class), row.getObject("transcript_id", UUID.class),
                        row.getString("storage_key"), row.getLong("start_ms"), row.getLong("end_ms"),
                        json.decode(row.getString("caption_settings")))).optional();
    }

    boolean renew(Claim claim) {
        return jdbc.sql("""
                UPDATE clips SET lease_until = now() + interval '30 seconds'
                WHERE id = :id AND lease_token = :token AND lease_until > now()
                """).param("id", claim.id()).param("token", claim.token()).update() == 1;
    }

    boolean finish(Claim claim, String file, String error) {
        return jdbc.sql("""
                UPDATE clips SET status = :status, file_key = :file, error = :error, lease_token = NULL, lease_until = NULL
                WHERE id = :id AND lease_token = :token AND lease_until > now()
                """).param("id", claim.id()).param("token", claim.token()).param("file", file)
                .param("status", file != null ? "READY" : "FAILED").param("error", error).update() == 1;
    }

    record Claim(UUID id, UUID token, UUID transcriptId, String storageKey, long startMs, long endMs,
                 CaptionSettings captions) {}
    private record Source(String title, long startMs, long endMs, long durationMs, boolean currentRevision, String decision) {}
}
