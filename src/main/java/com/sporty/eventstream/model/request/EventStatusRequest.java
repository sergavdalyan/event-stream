package com.sporty.eventstream.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventStatusRequest(@NotBlank(message = "eventId must not be blank") String eventId,
                                 @NotNull(message = "live must not be null")
                                 Boolean live) {
}
