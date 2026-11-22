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
            SELECT count(*) FROM transcription_jobs WHERE status IN ('QUEUED', 'EXTRACTING_AUDIO', 'TRANSCRIBING')
            """, nativeQuery = true)
    long countActiveJobs();
}
