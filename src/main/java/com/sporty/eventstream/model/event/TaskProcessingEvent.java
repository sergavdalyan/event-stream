package com.sporty.eventstream.model.event;

import java.time.Instant;

public record TaskProcessingEvent
        (Long taskId, String eventId, Instant processingTime) {
}

