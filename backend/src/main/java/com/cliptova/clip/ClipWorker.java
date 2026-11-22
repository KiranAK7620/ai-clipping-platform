package com.cliptova.clip;

import com.cliptova.transcription.TranscriptionStore;
import com.cliptova.video.OriginalVideoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
class ClipWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipWorker.class);
    private final ClipStore clips;
    private final TranscriptionStore transcripts;
    private final OriginalVideoStorage originals;
    private final ClipFiles files;
    private final ClipRenderer renderer;

    ClipWorker(ClipStore clips, TranscriptionStore transcripts, OriginalVideoStorage originals,
               ClipFiles files, ClipRenderer renderer) {
        this.clips = clips;
        this.transcripts = transcripts;
        this.originals = originals;
        this.files = files;
        this.renderer = renderer;
    }

    @Scheduled(fixedDelay = 1500)
    void processNext() {
        clips.claim().ifPresent(this::render);
    }

    private void render(ClipStore.Claim claim) {
        Path stage = null;
        try {
            var original = originals.findOriginal(claim.storageKey());
            stage = files.stage();
            var captions = claim.captions().enabled()
                    ? ClipCaptions.write(stage, transcripts.readSegments(claim.transcriptId()), claim) : null;
            var output = stage.resolve("clip.mp4");
            renderer.render(original, captions, output, claim, () -> clips.renew(claim));
            String key = files.publish(claim, output);
            if (!clips.finish(claim, key, null)) {
                files.discard(key);
            }
        } catch (ClipRenderer.LeaseLost exception) {
            // A reclaimed job belongs to the worker holding its current lease.
        } catch (ClipRenderer.RenderFailed exception) {
            clips.finish(claim, null, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Clip render {} failed with {}", claim.id(), exception.getClass().getSimpleName());
            clips.finish(claim, null, "Could not create the clip. Check FFmpeg and local storage, then retry.");
        } finally {
            if (stage != null) files.clearStage(stage);
        }
    }
}
