package com.cliptova.project;

import java.time.Instant;
import java.util.UUID;

record ProjectResponse(UUID id, String name, ProjectCategory category, Instant createdAt, VideoResponse video) {}
