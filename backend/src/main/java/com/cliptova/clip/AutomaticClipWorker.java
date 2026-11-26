package com.cliptova.clip;

import com.cliptova.highlight.HighlightService;
import com.cliptova.transcription.AutomaticTranscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CancellationException;

@Component
class AutomaticClipWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticClipWorker.class);
    private final AutomaticClipStore runs;
    private final AutomaticTranscription transcription;
    private final HighlightService highlights;

    AutomaticClipWorker(AutomaticClipStore runs, AutomaticTranscription transcription, HighlightService highlights) {
        this.runs = runs;
        this.transcription = transcription;
        this.highlights = highlights;
    }

    @Scheduled(fixedDelay = 1000)
    void processNext() {
        runs.claim().ifPresent(this::advance);
    }

    private void advance(AutomaticClipStore.Claim claim) {
        var run = claim.run();
        try {
            switch (run.status()) {
                case QUEUED, TRANSCRIBING -> {
                    boolean ready = transcription.isReady(run.projectId(), run.settings().language(),
                            run.status() == AutomaticClipStore.Status.QUEUED);
                    runs.move(claim, ready ? AutomaticClipStore.Status.ANALYZING : AutomaticClipStore.Status.TRANSCRIBING,
                            null, ready ? 0 : 2);
                }
                case ANALYZING -> runs.queueClips(claim,
                        highlights.generateForAutomatic(run.projectId(), () -> runs.renew(claim)));
                case RENDERING -> checkRenderedClips(claim);
                default -> throw new IllegalStateException("A finished automatic run was claimed.");
            }
        } catch (CancellationException exception) {
            // A reclaimed run belongs to the worker holding its current lease.
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                runs.move(claim, run.status(), "Waiting for the local highlight model.", 3);
            } else {
                runs.move(claim, AutomaticClipStore.Status.FAILED, exception.getReason(), 0);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Automatic clip run {} failed with {}", run.id(), exception.getClass().getSimpleName());
            runs.move(claim, AutomaticClipStore.Status.FAILED, "Automatic clipping failed. Start a new batch to retry.", 0);
        }
    }

    private void checkRenderedClips(AutomaticClipStore.Claim claim) {
        var items = runs.listClips(claim.run().id());
        if (items.stream().anyMatch(item -> item.status() == Clip.Status.QUEUED || item.status() == Clip.Status.RENDERING)) {
            runs.move(claim, AutomaticClipStore.Status.RENDERING, null, 2);
            return;
        }
        long ready = items.stream().filter(item -> item.status() == Clip.Status.READY).count();
        int requested = claim.run().settings().clipCount();
        if (ready == requested) {
            runs.move(claim, AutomaticClipStore.Status.COMPLETED, null, 0);
        } else if (ready == 0) {
            runs.move(claim, AutomaticClipStore.Status.FAILED, "No clips rendered successfully.", 0);
        } else {
            String message = "%d of %d requested clips are ready.".formatted(ready, requested);
            if (items.size() < requested) {
                message += " Fewer distinct highlights were available for these settings.";
            }
            runs.move(claim, AutomaticClipStore.Status.PARTIAL, message, 0);
        }
    }
}
