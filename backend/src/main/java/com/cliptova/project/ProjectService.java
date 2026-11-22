package com.cliptova.project;

import com.cliptova.video.Ffprobe;
import com.cliptova.video.OriginalVideoStorage;
import com.cliptova.video.VideoMetadata;
import com.cliptova.video.VideoUpload;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
class ProjectService {
    private static final int PAGE_SIZE = 20;

    private final ProjectRepository projects;
    private final EntityManager entityManager;
    private final OriginalVideoStorage storage;
    private final Ffprobe ffprobe;
    private final TransactionTemplate transaction;
    private final long maxUploadBytes;

    ProjectService(ProjectRepository projects, EntityManager entityManager, OriginalVideoStorage storage,
                   Ffprobe ffprobe, PlatformTransactionManager transactionManager,
                   @Value("${spring.servlet.multipart.max-file-size}") DataSize maxUploadSize) {
        this.projects = projects;
        this.entityManager = entityManager;
        this.storage = storage;
        this.ffprobe = ffprobe;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxUploadBytes = maxUploadSize.toBytes();
    }

    @Transactional(readOnly = true)
    ProjectList list(int page) {
        var recent = projects.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, PAGE_SIZE));
        return new ProjectList(recent.map(Project::response).getContent(), projects.countUploadedVideos(),
                recent.hasNext(), maxUploadBytes, projects.countActiveJobs(),
                projects.countCompletedProjects(), projects.countGeneratedClips());
    }

    @Transactional(readOnly = true)
    ProjectResponse get(UUID id) {
        return projects.findById(id).map(Project::response).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    UploadResult upload(UUID projectId, String name, ProjectCategory category, MultipartFile file) {
        String projectName = name.strip();
        var existing = projects.findById(projectId);
        if (existing.isPresent()) {
            return reuse(existing.get(), projectName, category);
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a video file with content.");
        }

        VideoUpload upload = VideoUpload.describe(file.getOriginalFilename());
        try {
            Path staged = storage.stage(file, upload.extension());
            try {
                long size = Files.size(staged);
                VideoMetadata metadata = ffprobe.inspect(staged, upload.demuxer());
                return new UploadResult(transaction.execute(status ->
                        persist(projectId, projectName, category, size, upload, staged, metadata)), true);
            } finally {
                storage.discard(staged);
            }
        } catch (DataIntegrityViolationException exception) {
            // Another request may have committed this upload after the initial lookup.
            return projects.findById(projectId).map(project -> reuse(project, projectName, category))
                    .orElseThrow(() -> exception);
        } catch (IOException exception) {
            throw storageUnavailable();
        }
    }

    private ProjectResponse persist(UUID projectId, String name, ProjectCategory category, long size,
                                    VideoUpload upload, Path staged, VideoMetadata metadata) {
        UUID videoId = UUID.randomUUID();
        Path saved;
        try {
            saved = storage.save(staged, videoId, upload.extension());
        } catch (IOException exception) {
            throw storageUnavailable();
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.discard(saved);
                }
            }
        });

        Project project = new Project(projectId, name, category);
        project.attachVideo(videoId, upload.originalName(), saved.getFileName().toString(), size, metadata);
        entityManager.persist(project);
        return project.response();
    }

    private UploadResult reuse(Project project, String name, ProjectCategory category) {
        if (!project.matches(name, category)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This upload ID belongs to a different project.");
        }
        return new UploadResult(project.response(), false);
    }

    private static ResponseStatusException storageUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The original video could not be saved. Check the local video storage and retry.");
    }

    record ProjectList(List<ProjectResponse> projects, long uploadedVideos, boolean hasNext, long maxUploadBytes,
                       long activeJobs, long completedProjects, long generatedClips) {}

    record UploadResult(ProjectResponse project, boolean created) {}
}
