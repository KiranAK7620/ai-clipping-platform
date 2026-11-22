package com.cliptova.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {
    private final UUID projectId = UUID.randomUUID();
    private final MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[] {1});
    private final VideoResponse video = new VideoResponse(UUID.randomUUID(), "demo.mp4", 1, 1.5,
            640, 480, 24.0, "h264", null);
    private final ProjectResponse project = new ProjectResponse(projectId, "Demo", ProjectCategory.EDUCATION,
            Instant.EPOCH, video);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectService service;

    @Test
    void bindsMultipartAndReturnsVideoMetadata() throws Exception {
        when(service.upload(eq(projectId), eq("Demo"), eq(ProjectCategory.EDUCATION), any()))
                .thenReturn(new ProjectService.UploadResult(project, true));

        mvc.perform(multipart("/api/projects").file(file)
                        .param("projectId", projectId.toString()).param("name", "Demo").param("category", "EDUCATION"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.video.width").value(640))
                .andExpect(jsonPath("$.video.storageKey").doesNotExist());
    }

    @Test
    void returnsOkForAnAlreadySavedUpload() throws Exception {
        when(service.upload(eq(projectId), eq("Demo"), eq(ProjectCategory.EDUCATION), any()))
                .thenReturn(new ProjectService.UploadResult(project, false));

        mvc.perform(multipart("/api/projects").file(file)
                        .param("projectId", projectId.toString()).param("name", "Demo").param("category", "EDUCATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()));
    }

    @Test
    void rejectsAnInvalidFormBeforeProcessingBytes() throws Exception {
        mvc.perform(multipart("/api/projects").file(file)
                        .param("projectId", projectId.toString()).param("name", "").param("category", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void listsProjectMetadataAndUploadLimits() throws Exception {
        when(service.list(0)).thenReturn(new ProjectService.ProjectList(List.of(project), 1, false, 536870912, 2, 1, 3));

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedVideos").value(1))
                .andExpect(jsonPath("$.activeJobs").value(2))
                .andExpect(jsonPath("$.completedProjects").value(1))
                .andExpect(jsonPath("$.generatedClips").value(3))
                .andExpect(jsonPath("$.maxUploadBytes").value(536870912))
                .andExpect(jsonPath("$.projects[0].video.durationSeconds").value(1.5));
    }

    @Test
    void loadsAProjectDirectlyForTheWorkspace() throws Exception {
        when(service.get(projectId)).thenReturn(project);

        mvc.perform(get("/api/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.durationSeconds").value(1.5));
    }

    @Test
    void rejectsNegativePageNumbers() throws Exception {
        mvc.perform(get("/api/projects").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
