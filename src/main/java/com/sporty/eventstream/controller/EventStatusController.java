package com.sporty.eventstream.controller;

import com.sporty.eventstream.logging.TraceIdContext;
import com.sporty.eventstream.model.request.EventStatusRequest;
import com.sporty.eventstream.service.EventTaskService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/events")
public class EventStatusController {

    private final EventTaskService eventTaskService;

    @PostMapping("/status")
    public ResponseEntity<Void> updateStatus(@Valid @RequestBody EventStatusRequest request) {
        String traceId = TraceIdContext.currentTraceId().orElse("n/a");
        log.info("traceId={} Received event status update: eventId={}, live={}", traceId, request.eventId(), request.live());
        eventTaskService.updateEventStatus(request.eventId(), request.live());
        log.info("traceId={} Event status update accepted: eventId={}, live={}", traceId, request.eventId(), request.live());
        return ResponseEntity.accepted().build();
    }
}
