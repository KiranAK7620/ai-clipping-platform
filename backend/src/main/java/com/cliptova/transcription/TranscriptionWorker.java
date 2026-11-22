package com.cliptova.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class TranscriptionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranscriptionWorker.class);

    private final TranscriptionStore jobs;
    private final LocalTranscription engine;

    TranscriptionWorker(TranscriptionStore jobs, LocalTranscription engine) {
        this.jobs = jobs;
        this.engine = engine;
    }

    @Scheduled(fixedDelay = 1500)
    void processNextJob() {
        jobs.claim().ifPresent(this::process);
    }

    private void process(TranscriptionStore.Claim claim) {
        try {
            Transcript transcript = engine.run(claim, stage -> jobs.renew(claim, stage));
            jobs.complete(claim, transcript);
        } catch (LocalTranscription.ClaimLost exception) {
            // A cancelled or reclaimed job no longer belongs to this worker.
        } catch (LocalTranscription.StepFailed exception) {
            jobs.fail(claim, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            LOGGER.warn("Transcription job {} failed with {}", claim.id(), exception.getClass().getSimpleName());
            jobs.fail(claim, "Transcription could not be completed. Please retry.");
        }
    }
}
