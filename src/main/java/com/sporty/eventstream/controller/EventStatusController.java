package com.sporty.eventstream.controller;

import com.sporty.eventstream.logging.TraceIdContext;
import com.sporty.eventstream.model.request.EventStatusRequest;
import com.sporty.eventstream.service.EventTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Event Status", description = "API for managing live event status")
public class EventStatusController {

    private final EventTaskService eventTaskService;

    @Operation(
            summary = "Update event live status",
            description = "Updates whether an event is LIVE or NOT LIVE. ",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Event status update request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EventStatusRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Request accepted - event status will be updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request - missing or empty eventId"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/status")
    public ResponseEntity<Void> updateStatus(@Valid @RequestBody EventStatusRequest request) {
        String traceId = TraceIdContext.currentTraceId().orElse("n/a");
        log.info("traceId={} Received event status update: eventId={}, live={}", traceId, request.eventId(), request.live());
        eventTaskService.updateEventStatus(request.eventId(), request.live());
        log.info("traceId={} Event status update accepted: eventId={}, live={}", traceId, request.eventId(), request.live());
        return ResponseEntity.accepted().build();
    }
}
