package com.cliptova.project;

import com.cliptova.video.VideoMetadata;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
class Project {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectCategory category;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "project", cascade = CascadeType.PERSIST)
    private Video video;

    protected Project() {}

    Project(UUID id, String name, ProjectCategory category) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.createdAt = Instant.now();
    }

    void attachVideo(UUID videoId, String originalName, String storageKey, long sizeBytes, VideoMetadata metadata) {
        this.video = new Video(videoId, this, originalName, storageKey, sizeBytes, metadata);
    }

    ProjectResponse response() {
        return new ProjectResponse(id, name, category, createdAt, video == null ? null : video.response());
    }

    boolean matches(String name, ProjectCategory category) {
        return this.name.equals(name) && this.category == category && video != null;
    }
}
