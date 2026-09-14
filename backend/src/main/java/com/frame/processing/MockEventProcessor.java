package com.frame.processing;

import com.frame.domain.entity.EventType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulates video analysis: sleeps through realistic-looking stages and emits plausible
 * timestamped events scaled to the video's real (client-reported) duration. No ffmpeg/AI
 * involved — this exists purely so the rest of the system (async pipeline, WS progress, timeline
 * UI) can be built and reviewed end-to-end before a real analyzer is wired up behind the same
 * {@link EventProcessor} interface.
 */
@Component
@ConditionalOnProperty(prefix = "frame.processing", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockEventProcessor implements EventProcessor {

    private static final List<String> CHAPTER_TITLES =
            List.of("Introduction", "Main Content", "Deep Dive", "Q&A", "Wrap-up");
    private static final List<String> AUDIO_EVENT_LABELS =
            List.of("Music starts", "Applause detected", "Laughter detected", "Background noise", "Silence");
    private static final List<String> OCR_SNIPPETS =
            List.of("Agenda", "Slide 1: Overview", "Key Metric: +24%", "Thank you!", "Contact: team@frame.dev");

    @Value("${frame.processing.mock.stage-delay-ms:1200}")
    private long stageDelayMs;

    @Override
    public void process(ProcessingContext ctx) throws ProcessingException {
        double durationSeconds = ctx.durationSeconds() > 0 ? ctx.durationSeconds() : 120.0;
        long durationMs = (long) (durationSeconds * 1000);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        sleep(stageDelayMs);
        ctx.reportProgress(10, "Analyzing video");

        sleep(stageDelayMs);
        ctx.reportProgress(30, "Detecting scenes and chapters");
        generateScenesAndChapters(ctx, durationMs, random);

        sleep(stageDelayMs);
        ctx.reportProgress(55, "Extracting audio events and speakers");
        generateAudioAndSpeakers(ctx, durationMs, random);

        sleep(stageDelayMs);
        ctx.reportProgress(80, "Running OCR on frames");
        generateOcrText(ctx, durationMs, random);

        sleep(stageDelayMs / 2);
        ctx.reportProgress(100, "Finalizing");
    }

    private void generateScenesAndChapters(ProcessingContext ctx, long durationMs, ThreadLocalRandom random) {
        long cursor = 0;
        int sceneNumber = 1;
        while (cursor < durationMs) {
            long gap = 8_000 + random.nextLong(0, 7_000);
            cursor = Math.min(cursor + gap, durationMs);
            ctx.emitEvent(EventDraft.instant(
                    EventType.SCENE_CHANGE, cursor, "Scene " + sceneNumber++, Map.of(), 0.7 + random.nextDouble() * 0.29));
        }

        int chapterCount = Math.max(2, Math.min(CHAPTER_TITLES.size(), (int) (durationMs / 20_000)));
        long chapterLength = durationMs / chapterCount;
        for (int i = 0; i < chapterCount; i++) {
            long start = i * chapterLength;
            long end = (i == chapterCount - 1) ? durationMs : (i + 1) * chapterLength;
            ctx.emitEvent(EventDraft.range(EventType.CHAPTER, start, end, CHAPTER_TITLES.get(i),
                    "Auto-detected chapter segment"));
        }
    }

    private void generateAudioAndSpeakers(ProcessingContext ctx, long durationMs, ThreadLocalRandom random) {
        long cursor = 0;
        while (cursor < durationMs) {
            cursor = Math.min(cursor + 15_000 + random.nextLong(0, 20_000), durationMs);
            String label = AUDIO_EVENT_LABELS.get(random.nextInt(AUDIO_EVENT_LABELS.size()));
            ctx.emitEvent(EventDraft.instant(
                    EventType.AUDIO_EVENT, cursor, label, Map.of("category", "audio"), 0.6 + random.nextDouble() * 0.35));
        }

        long speakerCursor = 0;
        char speaker = 'A';
        while (speakerCursor < durationMs) {
            long segmentLength = 10_000 + random.nextLong(0, 25_000);
            long end = Math.min(speakerCursor + segmentLength, durationMs);
            ctx.emitEvent(EventDraft.range(
                    EventType.SPEAKER_CHANGE, speakerCursor, end, "Speaker " + speaker, null));
            speaker = speaker == 'A' ? 'B' : 'A';
            speakerCursor = end;
        }
    }

    private void generateOcrText(ProcessingContext ctx, long durationMs, ThreadLocalRandom random) {
        int occurrences = Math.max(1, (int) (durationMs / 25_000));
        for (int i = 0; i < occurrences; i++) {
            long ts = Math.min(durationMs - 1, random.nextLong(0, Math.max(1, durationMs)));
            String text = OCR_SNIPPETS.get(random.nextInt(OCR_SNIPPETS.size()));
            ctx.emitEvent(EventDraft.instant(
                    EventType.OCR_TEXT, ts, text,
                    Map.of("text", text, "boundingBox", Map.of("x", 0.1, "y", 0.1, "w", 0.4, "h", 0.08)),
                    0.75 + random.nextDouble() * 0.24));
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
