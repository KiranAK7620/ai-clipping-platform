package com.cliptova.clip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
class ClipFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipFiles.class);
    private static final String UUID_PATTERN = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final Pattern KEY = Pattern.compile(UUID_PATTERN + "-" + UUID_PATTERN + "\\.mp4");
    private final Path directory;

    ClipFiles(@Value("${cliptova.clips.directory}") Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    Path stage() throws IOException {
        Files.createDirectories(directory);
        return Files.createTempDirectory(directory, ".render-");
    }

    String publish(ClipStore.Claim claim, Path staged) throws IOException {
        String key = claim.id() + "-" + claim.token() + ".mp4";
        Files.move(staged, directory.resolve(key));
        return key;
    }

    Optional<Path> find(String key) {
        if (!KEY.matcher(key).matches()) return Optional.empty();
        Path file = directory.resolve(key);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Optional.of(file) : Optional.empty();
    }

    void discard(String key) {
        find(key).ifPresent(this::delete);
    }

    void clearStage(Path stage) {
        try (var files = Files.newDirectoryStream(stage)) {
            files.forEach(this::delete);
        } catch (IOException exception) {
            LOGGER.warn("Could not enumerate a ClipTova render staging directory.");
        }
        delete(stage);
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.warn("Could not clean up a ClipTova render file.");
        }
    }
}
