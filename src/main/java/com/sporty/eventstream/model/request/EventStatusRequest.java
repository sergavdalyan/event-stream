package com.sporty.eventstream.model.request;

import jakarta.validation.constraints.NotBlank;

public record EventStatusRequest(@NotBlank(message = "eventId must not be blank") String eventId,
                                 boolean live) {
}
