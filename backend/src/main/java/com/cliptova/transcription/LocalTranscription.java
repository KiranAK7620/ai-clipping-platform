package com.cliptova.transcription;

import com.cliptova.video.OriginalVideoStorage;
import com.cliptova.video.VideoUpload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Component
class LocalTranscription {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTranscription.class);
    private static final long MAX_RESULT_BYTES = 16 * 1024 * 1024;

    private final OriginalVideoStorage storage;
    private final ObjectMapper json;
    private final String ffmpeg;
    private final String whisper;
    private final Path model;
    private final Path hindiModel;
    private final Duration maxVideoDuration;
    private final Duration extractTimeout;
    private final Duration whisperTimeout;
    private final int threads;

    LocalTranscription(OriginalVideoStorage storage, ObjectMapper json,
                       @Value("${cliptova.transcription.ffmpeg}") String ffmpeg,
                       @Value("${cliptova.transcription.whisper}") String whisper,
                       @Value("${cliptova.transcription.model}") Path model,
                       @Value("${cliptova.transcription.hindi-model}") Path hindiModel,
                       @Value("${cliptova.transcription.max-video-duration}") Duration maxVideoDuration,
                       @Value("${cliptova.transcription.extract-timeout}") Duration extractTimeout,
                       @Value("${cliptova.transcription.whisper-timeout}") Duration whisperTimeout,
                       @Value("${cliptova.transcription.threads}") int threads) {
        this.storage = storage;
        this.json = json;
        this.ffmpeg = ffmpeg;
        this.whisper = whisper;
        this.model = model.toAbsolutePath().normalize();
        this.hindiModel = hindiModel.toAbsolutePath().normalize();
        this.maxVideoDuration = maxVideoDuration;
        this.extractTimeout = extractTimeout;
        this.whisperTimeout = whisperTimeout;
        this.threads = threads;
    }

    boolean modelAvailable(TranscriptionLanguage language) {
        return readableModel(modelFor(language));
    }

    private Path modelFor(TranscriptionLanguage language) {
        return language == TranscriptionLanguage.HINDI && readableModel(hindiModel) ? hindiModel : model;
    }

    private static boolean readableModel(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }

    boolean supportsDuration(double seconds) {
        return Double.isFinite(seconds) && seconds > 0 && seconds <= maxVideoDuration.toMillis() / 1000.0;
    }

    Transcript run(TranscriptionStore.Claim claim, Predicate<TranscriptionStatus> maintainLease)
            throws InterruptedException, StepFailed, ClaimLost {
        Path selectedModel = modelFor(claim.language());
        if (!readableModel(selectedModel)) {
            throw new StepFailed("The local Whisper model is missing. Check transcription setup and retry.");
        }
        if (!supportsDuration(claim.durationSeconds())) {
            throw new StepFailed("This video exceeds the configured transcription duration limit.");
        }

        Path source;
        try {
            source = storage.findOriginal(claim.storageKey());
        } catch (IOException exception) {
            throw new StepFailed("The original video could not be opened. Check local storage and retry.");
        }

        Path work;
        try {
            work = Files.createTempDirectory("cliptova-transcription-");
        } catch (IOException exception) {
            throw new StepFailed("Temporary audio storage could not be created.");
        }
        Path audio = work.resolve("audio.wav");
        Path output = work.resolve("transcript");
        Path result = work.resolve("transcript.json");

        try {
            long maxAudioBytes = maxVideoDuration.toMillis() * 32 + 4096;
            runStep(extractionCommand(source, audio, maxAudioBytes + 32768), extractTimeout,
                    TranscriptionStatus.EXTRACTING_AUDIO, maintainLease, "FFmpeg could not extract the audio.");
            if (Files.size(audio) > maxAudioBytes) {
                throw new StepFailed("The audio exceeds the configured transcription duration limit.");
            }

            runStep(List.of(whisper, "-m", selectedModel.toString(), "-f", audio.toString(), "-l", claim.language().code(),
                            "-t", Integer.toString(threads), "-oj", "-of", output.toString(), "-np"),
                    whisperTimeout, TranscriptionStatus.TRANSCRIBING, maintainLease,
                    "Whisper could not transcribe the audio. Check the local CLI and model, then retry.");
            if (Files.size(result) > MAX_RESULT_BYTES) {
                throw new StepFailed("The transcript output exceeds the supported size.");
            }
            String decoded = new String(Files.readAllBytes(result), StandardCharsets.UTF_8);
            return WhisperOutput.parse(json.readTree(decoded), Math.round(claim.durationSeconds() * 1000));
        } catch (IOException exception) {
            throw new StepFailed("The audio or transcript output could not be read.");
        } finally {
            removeTemporaryFiles(result, audio, work);
        }
    }

    private List<String> extractionCommand(Path source, Path audio, long maxBytes) {
        String demuxer = VideoUpload.describe(source.getFileName().toString()).demuxer();
        var command = new ArrayList<>(List.of(ffmpeg, "-nostdin", "-v", "error", "-protocol_whitelist", "file",
                "-max_streams", "64", "-max_alloc", "67108864", "-f", demuxer));
        if (demuxer.equals("mov")) {
            command.addAll(List.of("-enable_drefs", "0", "-use_absolute_path", "0"));
        }
        command.addAll(List.of("-i", source.toString(), "-map", "0:a:0", "-vn", "-sn", "-dn",
                "-af", "aresample=16000:async=1:first_pts=0",
                "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "-fs", Long.toString(maxBytes),
                "-n", audio.toString()));
        return command;
    }

    private static void runStep(List<String> command, Duration timeout, TranscriptionStatus stage,
                                Predicate<TranscriptionStatus> maintainLease, String message)
            throws InterruptedException, StepFailed, ClaimLost {
        if (!maintainLease.test(stage)) {
            throw new ClaimLost();
        }

        Process child;
        try {
            child = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException exception) {
            throw new StepFailed(message);
        }
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new StepFailed(stage == TranscriptionStatus.EXTRACTING_AUDIO
                            ? "Audio extraction timed out." : "Transcription timed out.");
                }
                if (child.waitFor(Math.min(remaining, TimeUnit.SECONDS.toNanos(2)), TimeUnit.NANOSECONDS)) {
                    break;
                }
                if (!maintainLease.test(stage)) {
                    throw new ClaimLost();
                }
            }
            if (child.exitValue() != 0) {
                throw new StepFailed(message);
            }
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(2, TimeUnit.SECONDS);
            }
        }
    }

    private static void removeTemporaryFiles(Path... paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                LOGGER.warn("Could not clean up a temporary transcription file.");
            }
        }
    }

    static class StepFailed extends Exception {
        StepFailed(String message) {
            super(message);
        }
    }

    static class ClaimLost extends Exception {}
}
