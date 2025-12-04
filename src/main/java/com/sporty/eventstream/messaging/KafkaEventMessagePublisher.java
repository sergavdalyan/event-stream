package com.sporty.eventstream.messaging;

import com.sporty.eventstream.exception.KafkaPublishException;
import com.sporty.eventstream.model.kafka.EventScoreMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous Kafka publisher with retry logic and timeout protection.
 * Ensures transactional integrity: only returns successfully if message is confirmed delivered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventMessagePublisher {

    private final KafkaTemplate<String, EventScoreMessage> kafkaTemplate;

    @Value("${events.kafka.topic:live-events-scores}")
    private String topic;

    @Value("${events.kafka.max-retries:3}")
    private int maxRetries;

    @Value("${events.kafka.send-timeout-ms:5000}")
    private long sendTimeoutMs;

    /**
     * Publishes a message to Kafka synchronously with retry and timeout.
     * 
     * @param message the message to publish
     * @throws KafkaPublishException if publishing fails after all retry attempts
     */
    public void publish(EventScoreMessage message) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                SendResult<String, EventScoreMessage> result = kafkaTemplate
                        .send(topic, message.eventId(), message)
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

                // Success - log and return
                log.info("Successfully published event {} to topic {} partition {} offset {} (attempt {}/{})",
                        message,
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        attempt,
                        maxRetries
                );
                return;

            } catch (TimeoutException e) {
                lastException = e;
                log.warn("Timeout publishing event {} on attempt {}/{} (timeout={}ms)",
                        message.eventId(), attempt, maxRetries, sendTimeoutMs, e);

            } catch (InterruptedException e) {
                lastException = e;
                log.warn("Interrupted while publishing event {} on attempt {}/{}",
                        message.eventId(), attempt, maxRetries, e);
                Thread.currentThread().interrupt(); // Restore interrupt status
                break; // Don't retry on interrupt

            } catch (ExecutionException e) {
                lastException = e;
                log.warn("Failed to publish event {} on attempt {}/{}: {}",
                        message.eventId(), attempt, maxRetries, e.getMessage(), e);
            }

            // Apply backoff before retry (unless it's the last attempt)
            if (attempt < maxRetries) {
                long backoffMs = 200L * attempt;
                log.debug("Backing off for {}ms before retry", backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All retries exhausted
        String errorMsg = String.format(
                "Failed to publish event %s to Kafka after %d attempts",
                message.eventId(),
                attempt
        );
        log.error(errorMsg, lastException);
        throw new KafkaPublishException(errorMsg, lastException);
    }
}
