package com.sporty.eventstream.client;

import com.sporty.eventstream.model.response.EventScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
@Slf4j
public class ExternalServiceEventScoreClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ExternalServiceEventScoreClient(RestTemplateBuilder restTemplateBuilder,
                                           @Value("${external.api.base-url}") String baseUrl,
                                           @Value("${external.api.connect-timeout-ms:1000}") long connectTimeoutMs,
                                           @Value("${external.api.read-timeout-ms:2000}") long readTimeoutMs) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100,
                    multiplier = 2.0,
                    maxDelay = 1000)
    )
    public String fetchScore(String eventId) {
        String url = baseUrl + "/events/" + eventId;
        log.debug("Attempting to fetch score for event {} from {}", eventId, url);

        try {
            ResponseEntity<EventScoreResponse> responseEntity =
                    restTemplate.getForEntity(url, EventScoreResponse.class);

            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                log.error("External API returned non-2xx for event {}: status={}",
                        eventId, responseEntity.getStatusCode());
                throw new IllegalStateException("Non-2xx response from external API for event " + eventId);
            }

            EventScoreResponse body = responseEntity.getBody();
            if (body == null || body.currentScore() == null) {
                log.error("External API returned empty/invalid body for event {}", eventId);
                throw new IllegalStateException("Invalid response body from external API for event " + eventId);
            }

            log.debug("Successfully fetched score for event {}: {}", eventId, body.currentScore());
            return body.currentScore();
        } catch (RestClientResponseException e) {
            log.error("External API error for event {}: status={}, body={}",
                    eventId, e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (RestClientException e) {
            log.error("Error calling external API for event {} at URL {}", eventId, url, e);
            throw e;
        }
    }
}
