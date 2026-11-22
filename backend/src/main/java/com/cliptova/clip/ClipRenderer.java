package com.cliptova.clip;

import com.cliptova.video.VideoUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Component
class ClipRenderer {
    private static final String FRAMING = """
            [0:V:0]fps=30,scale=w='trunc(iw*sar/2)*2':h=ih,setsar=1,split[back][front];
            [back]scale=180:320:force_original_aspect_ratio=increase:force_divisible_by=2,
            crop=180:320,boxblur=12:1,scale=720:1280,eq=brightness=-0.23:saturation=0.65[background];
            [front]scale=720:1280:force_original_aspect_ratio=decrease:force_divisible_by=2[foreground];
            [background][foreground]overlay=(W-w)/2:(H-h)/2[framed];
            """;

    private final String ffmpeg;
    private final Duration timeout;

    ClipRenderer(@Value("${cliptova.transcription.ffmpeg}") String ffmpeg,
                 @Value("${cliptova.clips.render-timeout}") Duration timeout) {
        this.ffmpeg = ffmpeg;
        this.timeout = timeout;
    }

    void render(Path original, Path captions, Path output, ClipStore.Claim clip, BooleanSupplier renew)
            throws IOException, InterruptedException, RenderFailed, LeaseLost {
        String demuxer = VideoUpload.describe(original.getFileName().toString()).demuxer();
        var args = new ArrayList<>(List.of(ffmpeg, "-nostdin", "-v", "error", "-filter_complex_threads", "2",
                "-protocol_whitelist", "file", "-max_streams", "64", "-max_alloc", "67108864", "-f", demuxer));
        if (demuxer.equals("mov")) {
            args.addAll(List.of("-enable_drefs", "0", "-use_absolute_path", "0"));
        }
        args.addAll(List.of("-ss", seconds(clip.startMs()), "-i", original.toString()));
        String filters = FRAMING.strip().replace("\n", "");
        if (captions != null) {
            args.addAll(List.of("-protocol_whitelist", "file", "-f", "concat", "-safe", "0", "-i", captions.toString()));
            filters += "[1:v]format=rgba[captions];[framed][captions]overlay=0:"
                    + clip.captions().position().overlayY() + "[styled];[styled]";
        } else {
            filters += "[framed]";
        }
        args.addAll(List.of("-filter_complex", filters + "setsar=1,format=yuv420p[out]",
                "-map", "[out]", "-map", "0:a:0?",
                "-t", seconds(clip.endMs() - clip.startMs()), "-c:v", "libx264", "-preset", "veryfast",
                "-crf", "20", "-threads:v", "4", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
                "-map_metadata", "-1", "-map_chapters", "-1", "-n", output.toString()));
        if (!renew.getAsBoolean()) throw new LeaseLost();

        var process = new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new RenderFailed("Clip rendering timed out. Try again.");
                if (process.waitFor(Math.min(remaining, TimeUnit.SECONDS.toNanos(2)), TimeUnit.NANOSECONDS)) break;
                if (!renew.getAsBoolean()) throw new LeaseLost();
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new RenderFailed("FFmpeg could not render this clip. Check that the source is playable.");
            }
            if (!renew.getAsBoolean()) throw new LeaseLost();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        }
    }

    private static String seconds(long milliseconds) {
        return String.format(Locale.ROOT, "%.3f", milliseconds / 1000.0);
    }

    static class RenderFailed extends Exception {
        RenderFailed(String message) { super(message); }
    }

    static class LeaseLost extends Exception {}
}
