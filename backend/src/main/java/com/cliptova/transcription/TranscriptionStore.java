package com.cliptova.transcription;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Repository
public class TranscriptionStore {
    private final JdbcClient jdbc;
    private final JdbcTemplate batches;

    TranscriptionStore(JdbcClient jdbc, JdbcTemplate batches) {
        this.jdbc = jdbc;
        this.batches = batches;
    }

    Optional<SourceVideo> source(UUID projectId) {
        return jdbc.sql("""
                SELECT id, storage_key, duration_seconds, audio_codec
                FROM videos WHERE project_id = :project
                """).param("project", projectId)
                .query((row, index) -> new SourceVideo(row.getObject("id", UUID.class),
                        row.getString("storage_key"), row.getDouble("duration_seconds"), row.getString("audio_codec")))
                .optional();
    }

    Optional<JobState> state(UUID videoId) {
        return jdbc.sql("SELECT id, status, error FROM transcription_jobs WHERE video_id = :video")
                .param("video", videoId).query((row, index) -> new JobState(row.getObject("id", UUID.class),
                        TranscriptionStatus.valueOf(row.getString("status")), row.getString("error"))).optional();
    }

    boolean enqueue(UUID videoId, TranscriptionLanguage language, Integer revision) {
        return jdbc.sql("""
                INSERT INTO transcription_jobs (video_id, language, status)
                VALUES (:video, :language, 'QUEUED')
                ON CONFLICT (video_id) DO UPDATE SET status = 'QUEUED', language = EXCLUDED.language,
                    queued_at = now(), error = NULL
                WHERE transcription_jobs.status IN ('FAILED', 'CANCELLED')
                    OR (transcription_jobs.status = 'COMPLETED' AND :revision = (
                        SELECT MAX(revision) FROM transcripts WHERE job_id = transcription_jobs.id
                    ))
                """).param("video", videoId).param("language", language.name())
                .param("revision", revision, Types.INTEGER).update() == 1;
    }

    void cancel(UUID videoId) {
        jdbc.sql("""
                UPDATE transcription_jobs SET status = 'CANCELLED', lease_token = NULL, lease_until = NULL
                WHERE video_id = :video AND status IN ('QUEUED', 'EXTRACTING_AUDIO', 'TRANSCRIBING')
                """).param("video", videoId).update();
    }

    Optional<Claim> claim() {
        return jdbc.sql("""
                WITH pending AS (
                    SELECT id FROM transcription_jobs
                    WHERE status = 'QUEUED'
                        OR (status IN ('EXTRACTING_AUDIO', 'TRANSCRIBING') AND lease_until <= now())
                    ORDER BY queued_at, id
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE transcription_jobs AS job
                SET status = 'EXTRACTING_AUDIO', lease_token = gen_random_uuid(),
                    lease_until = now() + interval '30 seconds'
                FROM pending, videos AS video
                WHERE job.id = pending.id AND video.id = job.video_id
                RETURNING job.id, lease_token, language, video.storage_key, video.duration_seconds
                """).query((row, index) -> new Claim(row.getObject("id", UUID.class),
                        row.getObject("lease_token", UUID.class), TranscriptionLanguage.valueOf(row.getString("language")),
                        row.getString("storage_key"), row.getDouble("duration_seconds"))).optional();
    }

    boolean renew(Claim claim, TranscriptionStatus stage) {
        return jdbc.sql("""
                UPDATE transcription_jobs SET status = :stage, lease_until = now() + interval '30 seconds'
                WHERE id = :id AND lease_token = :token AND lease_until > now()
                """).param("stage", stage.name()).param("id", claim.id()).param("token", claim.token()).update() == 1;
    }

    void fail(Claim claim, String message) {
        finish(claim, TranscriptionStatus.FAILED, message);
    }

    @Transactional
    void complete(Claim claim, Transcript transcript) {
        if (!finish(claim, TranscriptionStatus.COMPLETED, null)) {
            return;
        }
        UUID transcriptId = jdbc.sql("""
                INSERT INTO transcripts (job_id, language, revision)
                SELECT :job, :language, COALESCE(MAX(revision), 0) + 1 FROM transcripts WHERE job_id = :job
                RETURNING id
                """).param("job", claim.id()).param("language", transcript.language())
                .query(UUID.class).single();
        List<Transcript.Segment> segments = transcript.segments();
        if (!segments.isEmpty()) {
            batches.batchUpdate("""
                    INSERT INTO transcript_segments (transcript_id, position, start_ms, end_ms, text)
                    VALUES (?, ?, ?, ?, ?)
                    """, IntStream.range(0, segments.size()).boxed().toList(), 100,
                    (statement, position) -> {
                        var segment = segments.get(position);
                        statement.setObject(1, transcriptId);
                        statement.setInt(2, position);
                        statement.setLong(3, segment.startMs());
                        statement.setLong(4, segment.endMs());
                        statement.setString(5, segment.text());
                    });
        }
    }

    Optional<SavedTranscript> readTranscript(UUID jobId) {
        return jdbc.sql("""
                SELECT id, language, revision FROM transcripts WHERE job_id = :job ORDER BY revision DESC LIMIT 1
                """).param("job", jobId).query((row, index) -> new TranscriptHeader(
                        row.getObject("id", UUID.class), row.getString("language"), row.getInt("revision")))
                .optional().map(this::readSegments);
    }

    private SavedTranscript readSegments(TranscriptHeader header) {
        return new SavedTranscript(header.revision(), new Transcript(header.language(), readSegments(header.id())));
    }

    public List<Transcript.Segment> readSegments(UUID transcriptId) {
        return jdbc.sql("""
                SELECT start_ms, end_ms, text FROM transcript_segments
                WHERE transcript_id = :transcript ORDER BY position
                """).param("transcript", transcriptId).query((row, index) -> new Transcript.Segment(
                        row.getLong("start_ms"), row.getLong("end_ms"), row.getString("text"))).list();
    }

    private boolean finish(Claim claim, TranscriptionStatus status, String message) {
        return jdbc.sql("""
                UPDATE transcription_jobs SET status = :status, error = :error, lease_token = NULL, lease_until = NULL
                WHERE id = :id AND lease_token = :token AND lease_until > now()
                """).param("status", status.name()).param("error", message)
                .param("id", claim.id()).param("token", claim.token()).update() == 1;
    }

    record SourceVideo(UUID id, String storageKey, double durationSeconds, String audioCodec) {}
    record JobState(UUID id, TranscriptionStatus status, String error) {}
    record Claim(UUID id, UUID token, TranscriptionLanguage language, String storageKey, double durationSeconds) {}
    record SavedTranscript(int revision, Transcript transcript) {}
    private record TranscriptHeader(UUID id, String language, int revision) {}
}
