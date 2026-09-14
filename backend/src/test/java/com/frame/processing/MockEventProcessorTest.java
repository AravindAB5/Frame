package com.frame.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.frame.domain.entity.EventType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MockEventProcessorTest {

    private final MockEventProcessor processor = new MockEventProcessor();

    @Test
    void progressReachesOneHundredAndEventsCoverEveryType() throws ProcessingException {
        RecordingContext ctx = new RecordingContext(90.0);

        processor.process(ctx);

        assertThat(ctx.progressUpdates).isNotEmpty();
        assertThat(ctx.progressUpdates.get(ctx.progressUpdates.size() - 1)).isEqualTo(100);
        assertThat(ctx.progressUpdates).isSorted();

        List<EventType> emittedTypes =
                ctx.events.stream().map(EventDraft::eventType).distinct().collect(Collectors.toList());
        assertThat(emittedTypes).containsExactlyInAnyOrder(
                EventType.SCENE_CHANGE, EventType.CHAPTER, EventType.AUDIO_EVENT,
                EventType.SPEAKER_CHANGE, EventType.OCR_TEXT);
    }

    @Test
    void chaptersSpanTheFullDurationWithNoGapsOrOverlaps() throws ProcessingException {
        double durationSeconds = 60.0;
        RecordingContext ctx = new RecordingContext(durationSeconds);

        processor.process(ctx);

        List<EventDraft> chapters = ctx.events.stream()
                .filter(e -> e.eventType() == EventType.CHAPTER)
                .sorted((a, b) -> Long.compare(a.timestampMs(), b.timestampMs()))
                .toList();

        assertThat(chapters).isNotEmpty();
        assertThat(chapters.get(0).timestampMs()).isEqualTo(0L);
        assertThat(chapters.get(chapters.size() - 1).endTimestampMs()).isEqualTo((long) (durationSeconds * 1000));
        for (int i = 1; i < chapters.size(); i++) {
            assertThat(chapters.get(i).timestampMs()).isEqualTo(chapters.get(i - 1).endTimestampMs());
        }
    }

    @Test
    void fallsBackToADefaultDurationWhenNoneIsProvided() throws ProcessingException {
        RecordingContext ctx = new RecordingContext(0.0);

        processor.process(ctx);

        assertThat(ctx.events).isNotEmpty();
    }

    private static class RecordingContext implements ProcessingContext {

        private final UUID videoId = UUID.randomUUID();
        private final double durationSeconds;
        final List<Integer> progressUpdates = new ArrayList<>();
        final List<EventDraft> events = new ArrayList<>();

        RecordingContext(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        @Override
        public UUID videoId() {
            return videoId;
        }

        @Override
        public double durationSeconds() {
            return durationSeconds;
        }

        @Override
        public void reportProgress(int percent, String stage) {
            progressUpdates.add(percent);
        }

        @Override
        public void emitEvent(EventDraft draft) {
            events.add(draft);
        }
    }
}
