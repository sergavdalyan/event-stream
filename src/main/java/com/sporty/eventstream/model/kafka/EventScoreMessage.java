package com.sporty.eventstream.model.kafka;

import java.time.Instant;

public record EventScoreMessage(String eventId, String score, Instant timestamp) {
}
