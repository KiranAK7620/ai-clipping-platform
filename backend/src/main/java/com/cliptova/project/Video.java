package com.cliptova.project;

import com.cliptova.video.VideoMetadata;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "videos")
class Video {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, unique = true, length = 48)
    private String storageKey;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private double durationSeconds;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    private Double frameRate;

    @Column(nullable = false, length = 64)
    private String videoCodec;

    @Column(length = 64)
    private String audioCodec;

    protected Video() {}

    Video(UUID id, Project project, String originalName, String storageKey, long sizeBytes,
          VideoMetadata metadata) {
        this.id = id;
        this.project = project;
        this.originalName = originalName;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.durationSeconds = metadata.durationSeconds();
        this.width = metadata.width();
        this.height = metadata.height();
        this.frameRate = metadata.frameRate();
        this.videoCodec = metadata.videoCodec();
        this.audioCodec = metadata.audioCodec();
    }

    VideoResponse response() {
        return new VideoResponse(id, originalName, sizeBytes, durationSeconds, width, height,
                frameRate, videoCodec, audioCodec);
    }
}
