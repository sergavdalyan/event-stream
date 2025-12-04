package com.sporty.eventstream.messaging;

import com.sporty.eventstream.exception.KafkaPublishException;
import com.sporty.eventstream.model.kafka.EventScoreMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous Kafka publisher with retry logic and timeout protection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventMessagePublisher {

    private final KafkaTemplate<String, EventScoreMessage> kafkaTemplate;

    @Value("${events.kafka.topic:live-events-scores}")
    private String topic;

    @Value("${events.kafka.send-timeout-ms:5000}")
    private long sendTimeoutMs;

    /**
     * Publishes a message to Kafka synchronously with retry and timeout.
     *
     * @param message the message to publish
     */
    @Retryable(
            retryFor = KafkaPublishException.class,
            maxAttemptsExpression = "#{${events.kafka.max-retries:3}}",
            backoff = @Backoff(delayExpression = "#{${events.kafka.retry-backoff-ms:200}}", multiplier = 2.0)
    )
    public void publish(EventScoreMessage message) {
        try {
            SendResult<String, EventScoreMessage> result = kafkaTemplate
                    .send(topic, message.eventId(), message)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            log.info("Successfully published event {} to topic {} partition {} offset {}",
                    message.eventId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (TimeoutException | ExecutionException e) {
            throw new KafkaPublishException("Kafka publish failure for event " + message.eventId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("Interrupted while publishing event " + message.eventId(), e);
        }
    }
}
