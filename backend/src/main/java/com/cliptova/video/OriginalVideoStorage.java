package com.cliptova.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OriginalVideoStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(OriginalVideoStorage.class);
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(mp4|mov|mkv|webm)");

    private final Path root;

    public OriginalVideoStorage(@Value("${cliptova.originals-directory}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path stage(MultipartFile video, String extension) throws IOException {
        Files.createDirectories(root);
        Path staged = Files.createTempFile(root, ".upload-", "." + extension);
        boolean stagedSuccessfully = false;
        try {
            video.transferTo(staged);
            stagedSuccessfully = true;
            return staged;
        } finally {
            if (!stagedSuccessfully) {
                discard(staged);
            }
        }
    }

    public Path save(Path staged, UUID videoId, String extension) throws IOException {
        return Files.move(staged, root.resolve(videoId + "." + extension));
    }

    public Path findOriginal(String storageKey) throws IOException {
        if (!STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IOException("Invalid original storage key");
        }
        Path original = root.resolve(storageKey);
        if (!Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(original.toString());
        }
        return original;
    }

    public void discard(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.warn("Could not remove a ClipTova upload file. Check the originals directory.");
        }
    }
}
