package com.cliptova.project;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

interface ProjectRepository extends JpaRepository<Project, UUID> {
    @EntityGraph(attributePaths = "video")
    Slice<Project> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query(value = "SELECT count(*) FROM videos", nativeQuery = true)
    long countUploadedVideos();

    @Query(value = """
            SELECT (SELECT count(*) FROM transcription_jobs WHERE status IN ('QUEUED', 'EXTRACTING_AUDIO', 'TRANSCRIBING'))
                + (SELECT count(*) FROM clips WHERE status IN ('QUEUED', 'RENDERING'))
                + (SELECT count(*) FROM automatic_clip_runs WHERE status = 'ANALYZING')
            """, nativeQuery = true)
    long countActiveJobs();

    @Query(value = "SELECT count(*) FROM clips WHERE status = 'READY'", nativeQuery = true)
    long countGeneratedClips();

    @Query(value = "SELECT count(DISTINCT project_id) FROM clips WHERE status = 'READY'", nativeQuery = true)
    long countCompletedProjects();
}
