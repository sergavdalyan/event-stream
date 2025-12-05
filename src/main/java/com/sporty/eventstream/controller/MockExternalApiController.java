package com.sporty.eventstream.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Simple mock external API endpoint returning a JSON payload for an event.
 */
@Slf4j
@RestController
@RequestMapping("/mock-api")
public class MockExternalApiController {

    private static final Random RANDOM = new Random();

    @GetMapping("/events/{eventId}")
    public Map<String, Object> getMockEvent(@PathVariable String eventId) {
        int home = RANDOM.nextInt(5);
        int away = RANDOM.nextInt(5);
        String score = home + ":" + away;

        Map<String, Object> body = new HashMap<>();
        body.put("eventId", eventId);
        body.put("currentScore", score);

        log.info("Mock external API returning score {} for event {}", score, eventId);
        return body;
    }
}


