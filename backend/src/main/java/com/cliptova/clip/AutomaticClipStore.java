package com.cliptova.clip;

import com.cliptova.highlight.Highlight;
import com.cliptova.transcription.TranscriptionLanguage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

@Repository
class AutomaticClipStore {
    private final JdbcClient jdbc;
    private final CaptionSettingsJson json;
    private final ClipStore clips;
    private final long leaseSeconds;

    AutomaticClipStore(JdbcClient jdbc, CaptionSettingsJson json, ClipStore clips,
                       @Value("${cliptova.highlights.request-timeout}") Duration providerTimeout) {
        this.jdbc = jdbc;
        this.json = json;
        this.clips = clips;
        this.leaseSeconds = Math.max(120, providerTimeout.toSeconds() + 30);
    }

    @Transactional
    Run start(UUID project, UUID requestId, Settings settings) {
        boolean hasVideo = jdbc.sql("SELECT EXISTS (SELECT 1 FROM videos WHERE project_id = :project)")
                .param("project", project).query(Boolean.class).single();
        if (!hasVideo) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No source video was found for this project.");
        }
        jdbc.sql("""
                INSERT INTO automatic_clip_runs (id, project_id, clip_count, duration, language, caption_settings, status)
                VALUES (:id, :project, :count, :duration, :language, CAST(:captions AS jsonb), 'QUEUED')
                ON CONFLICT DO NOTHING
                """).param("id", requestId).param("project", project).param("count", settings.clipCount())
                .param("duration", settings.duration().name()).param("language", settings.language().name())
                .param("captions", json.encode(settings.captions())).update();
        return jdbc.sql("SELECT * FROM automatic_clip_runs WHERE project_id = :project AND id = :id")
                .param("project", project).param("id", requestId).query(this::readRun).optional()
                .map(run -> withClips(run, listClips(run.id()))).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.CONFLICT, "An automatic batch is already active. Refresh its status."));
    }

    Optional<Run> latest(UUID project) {
        return jdbc.sql("""
                SELECT * FROM automatic_clip_runs WHERE project_id = :project
                ORDER BY created_at DESC, id DESC LIMIT 1
                """).param("project", project).query(this::readRun).optional().map(run -> withClips(run, listClips(run.id())));
    }

    Optional<Claim> claim() {
        return jdbc.sql("""
                WITH pending AS (
                    SELECT id FROM automatic_clip_runs
                    WHERE status IN ('QUEUED', 'TRANSCRIBING', 'ANALYZING', 'RENDERING')
                        AND next_check <= now() AND (lease_until IS NULL OR lease_until <= now())
                    ORDER BY next_check, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE automatic_clip_runs r
                SET lease_token = gen_random_uuid(), lease_until = now() + :lease * interval '1 second'
                FROM pending WHERE r.id = pending.id RETURNING r.*
                """).param("lease", leaseSeconds).query((row, index) ->
                        new Claim(readRun(row, index), row.getObject("lease_token", UUID.class))).optional();
    }

    boolean renew(Claim claim) {
        return jdbc.sql("""
                UPDATE automatic_clip_runs SET lease_until = now() + :lease * interval '1 second'
                WHERE id = :id AND lease_token = :token AND lease_until > now()
                """).param("lease", leaseSeconds).param("id", claim.run().id()).param("token", claim.token()).update() == 1;
    }

    void move(Claim claim, Status status, String message, int delaySeconds) {
        jdbc.sql("""
                UPDATE automatic_clip_runs SET status = :status, message = :message, lease_token = NULL, lease_until = NULL,
                    next_check = now() + :delay * interval '1 second'
                WHERE id = :id AND lease_token = :token AND lease_until > now()
                """).param("status", status.name()).param("message", message).param("delay", delaySeconds)
                .param("id", claim.run().id()).param("token", claim.token()).update();
    }

    @Transactional
    void queueClips(Claim claim, List<Highlight> highlights) {
        var run = claim.run();
        // Stage every item and advance the run in one transaction.
        boolean owned = jdbc.sql("""
                SELECT id FROM automatic_clip_runs WHERE id = :id AND lease_token = :token AND lease_until > now()
                FOR UPDATE
                """).param("id", run.id()).param("token", claim.token()).query(UUID.class).optional().isPresent();
        if (!owned) return;

        var ranges = new ArrayList<Clip.Range>();
        for (var highlight : highlights) {
            if (highlight.decision() == Highlight.Decision.REJECTED) continue;
            var clip = clips.enqueueAutomatic(run.projectId(), highlight.id(), run.settings().duration(),
                    run.settings().captions(), ranges);
            if (clip.isEmpty()) continue;
            var item = clip.get();
            ranges.add(new Clip.Range(item.startMs(), item.endMs()));
            jdbc.sql("INSERT INTO automatic_clip_run_items (run_id, clip_id, position) VALUES (:run, :clip, :position)")
                    .param("run", run.id()).param("clip", item.id()).param("position", ranges.size()).update();
            if (ranges.size() == run.settings().clipCount()) break;
        }
        if (!renew(claim)) {
            throw new CancellationException("Automatic run lease expired before queuing completed.");
        }
        move(claim, ranges.isEmpty() ? Status.FAILED : Status.RENDERING,
                ranges.isEmpty() ? "No eligible highlight could be selected." : null, 0);
    }

    List<Clip> listClips(UUID id) {
        return jdbc.sql("""
                SELECT c.* FROM automatic_clip_run_items i JOIN clips c ON c.id = i.clip_id
                WHERE i.run_id = :id ORDER BY i.position
                """).param("id", id).query(clips::readClip).list();
    }

    private Run readRun(ResultSet row, int index) throws SQLException {
        var settings = new Settings(row.getInt("clip_count"), Clip.Duration.valueOf(row.getString("duration")),
                TranscriptionLanguage.valueOf(row.getString("language")), json.decode(row.getString("caption_settings")));
        return new Run(row.getObject("id", UUID.class), row.getObject("project_id", UUID.class), settings,
                Status.valueOf(row.getString("status")), row.getString("message"), List.of());
    }

    private static Run withClips(Run run, List<Clip> items) {
        return new Run(run.id(), run.projectId(), run.settings(), run.status(), run.message(), items);
    }

    enum Status { QUEUED, TRANSCRIBING, ANALYZING, RENDERING, COMPLETED, PARTIAL, FAILED }
    record Settings(int clipCount, Clip.Duration duration, TranscriptionLanguage language, CaptionSettings captions) {}
    record Run(UUID id, UUID projectId, Settings settings, Status status, String message, List<Clip> clips) {}
    record Claim(Run run, UUID token) {}
}
