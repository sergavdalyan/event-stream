package com.sporty.eventstream.scheduler;

import com.sporty.eventstream.client.ExternalServiceEventScoreClient;
import com.sporty.eventstream.logging.TraceIdContext;
import com.sporty.eventstream.messaging.KafkaEventMessagePublisher;
import com.sporty.eventstream.model.event.TaskProcessingEvent;
import com.sporty.eventstream.model.kafka.EventScoreMessage;
import com.sporty.eventstream.service.EventTaskPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Async listener
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProcessingListener {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final EventTaskPersistenceService persistence;
    private final ExternalServiceEventScoreClient serviceEventClient;
    private final KafkaEventMessagePublisher eventScorePublisher;

    /**
     * Process task asynchronously in thread pool
     * Each task runs in parallel on separate thread
     */
    @Async("taskExecutor")
    @EventListener
    public void handleTaskProcessing(TaskProcessingEvent event) {
        TraceIdContext.setTraceId(TraceIdContext.generate());

        Long taskId = event.taskId();
        String eventId = event.eventId();
        Instant processingTime = event.processingTime();

        log.debug("Processing task {} for event {} in thread {}", taskId, eventId, Thread.currentThread().getName());

        try {
            // Call external API
            String score = serviceEventClient.fetchScore(eventId);

            // Publish to Kafka
            EventScoreMessage message = new EventScoreMessage(
                    eventId,
                    score,
                    processingTime
            );
            eventScorePublisher.publish(message);

            // Mark success and schedule next execution
            persistence.markTaskSuccess(taskId, processingTime);

            log.info("Successfully processed task {} for event {}", taskId, eventId);

        } catch (Exception ex) {
            String errorMessage = ex.getMessage();
            if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
                errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
            }

            persistence.markTaskError(taskId, processingTime, errorMessage);

            log.error("Failed to process task {} for event {}: {}", taskId, eventId, ex.getMessage(), ex);
        } finally {
            TraceIdContext.clear();
        }
    }
}

