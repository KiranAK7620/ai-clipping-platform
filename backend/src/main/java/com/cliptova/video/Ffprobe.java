package com.cliptova.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class Ffprobe {
    private static final long MAX_OUTPUT_BYTES = 64 * 1024;
    private static final String FIELDS =
            "format=duration:stream=codec_type,codec_name,width,height,avg_frame_rate:stream_disposition=attached_pic";

    private final ObjectMapper json;
    private final String executable;
    private final Duration timeout;

    public Ffprobe(ObjectMapper json, @Value("${cliptova.ffprobe.executable}") String executable,
                   @Value("${cliptova.ffprobe.timeout}") Duration timeout) {
        this.json = json;
        this.executable = executable;
        this.timeout = timeout;
    }

    public VideoMetadata inspect(Path source, String demuxer) {
        try {
            Path output = Files.createTempFile("cliptova-ffprobe-", ".json");
            try {
                runProbe(source, demuxer, output);
                if (Files.size(output) > MAX_OUTPUT_BYTES) {
                    throw invalidVideo();
                }
                return readMetadata(json.readTree(output.toFile()));
            } finally {
                Files.deleteIfExists(output);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The metadata check was interrupted.");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Video metadata could not be checked. Verify FFprobe is installed and retry.");
        }
    }

    private void runProbe(Path source, String demuxer, Path output) throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<>(List.of(executable, "-v", "error", "-protocol_whitelist", "file",
                "-max_streams", "64", "-max_alloc", "67108864", "-f", demuxer));
        if (demuxer.equals("mov")) {
            arguments.addAll(List.of("-enable_drefs", "0", "-use_absolute_path", "0"));
        }
        arguments.addAll(List.of("-show_entries", FIELDS, "-of", "json", "-i", source.toString()));
        Process process = new ProcessBuilder(arguments)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(output.toFile())
                .start();

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The metadata check took too long.");
            }
            if (process.exitValue() != 0) {
                throw invalidVideo();
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static VideoMetadata readMetadata(JsonNode data) {
        if (data == null) {
            throw invalidVideo();
        }
        JsonNode video = null;
        String audioCodec = null;
        for (JsonNode stream : data.path("streams")) {
            String type = stream.path("codec_type").asText();
            if (video == null && type.equals("video") && stream.path("disposition").path("attached_pic").asInt() == 0) {
                video = stream;
            }
            if (audioCodec == null && type.equals("audio")) {
                audioCodec = codec(stream);
            }
        }

        double duration = data.path("format").path("duration").asDouble(0);
        if (video == null || !Double.isFinite(duration) || duration <= 0) {
            throw invalidVideo();
        }

        int width = video.path("width").asInt();
        int height = video.path("height").asInt();
        String videoCodec = codec(video);
        if (width <= 0 || height <= 0 || videoCodec == null) {
            throw invalidVideo();
        }
        return new VideoMetadata(duration, width, height, frameRate(video.path("avg_frame_rate").asText()), videoCodec,
                audioCodec);
    }

    private static String codec(JsonNode stream) {
        String name = stream.path("codec_name").asText();
        return name.matches("[a-zA-Z0-9_]{1,64}") ? name : null;
    }

    private static Double frameRate(String rate) {
        String[] fraction = rate.split("/", 2);
        if (fraction.length != 2) {
            return null;
        }
        try {
            double framesPerSecond = Double.parseDouble(fraction[0]) / Double.parseDouble(fraction[1]);
            return Double.isFinite(framesPerSecond) && framesPerSecond > 0 ? framesPerSecond : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static ResponseStatusException invalidVideo() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "The file could not be read as a video with a duration and resolution.");
    }
}
