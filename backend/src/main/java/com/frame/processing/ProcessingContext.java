package com.frame.processing;

import java.util.UUID;

/** What an {@link EventProcessor} gets to report progress and emit findings without touching persistence/WS directly. */
public interface ProcessingContext {

    UUID videoId();

    double durationSeconds();

    void reportProgress(int percent, String stage);

    void emitEvent(EventDraft draft);
}
