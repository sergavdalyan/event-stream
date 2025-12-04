package com.sporty.eventstream.service;

import com.sporty.eventstream.client.ExternalServiceEventScoreClient;
import com.sporty.eventstream.messaging.KafkaEventMessagePublisher;
import com.sporty.eventstream.model.kafka.EventScoreMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTaskService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final EventTaskPersistenceService persistence;
    private final ExternalServiceEventScoreClient serviceEventClient;
    private final KafkaEventMessagePublisher eventScorePublisher;

    @Value("${events.task-processor-batch-size:100}")
    private int batchSize;

    @Value("${events.task-in-progress-timeout-seconds:30}")
    private int inProgressTimeoutSeconds;

    public void updateEventStatus(String eventId, boolean live) {
        persistence.updateEventStatus(eventId, live);
    }

    /**
     * Called from scheduler
     * - Releases stuck IN_PROGRESS tasks
     * - Claims a batch of ACTIVE tasks as IN_PROGRESS
     * - Processes each task
     * - Marks success/error in small transactional calls
     */
    public void processDueTasks() {
        Instant now = Instant.now();

        // Release IN_PROGRESS tasks
        Instant stuckThreshold = now.minusSeconds(inProgressTimeoutSeconds);
        int released = persistence.releaseInProgressTasks(now, stuckThreshold);
        if (released > 0) {
            log.warn("Released {} stuck IN_PROGRESS tasks for retry", released);
        }

        // Claim tasks for this run
        List<Long> taskIds = persistence.claimTasksForProcessing(now, batchSize);
        if (taskIds.isEmpty()) {
            log.debug("No tasks due for execution at {}", now);
            return;
        }

        log.info("Processing {} tasks", taskIds.size());

        for (Long taskId : taskIds) {
            processSingleTask(taskId, now);
        }
    }

    private void processSingleTask(Long taskId, Instant now) {
        String eventId;
        try {
            eventId = persistence.getEventIdByTaskId(taskId);
        } catch (Exception e) {
            log.error("Failed to fetch task {}: {}", taskId, e.getMessage(), e);
            return;
        }

        try {
            // External REST call
            String score = serviceEventClient.fetchScore(eventId);

            // Send to Kafka
            eventScorePublisher.publish(new EventScoreMessage(eventId, score, Instant.now()));

            // Mark success
            persistence.markTaskSuccess(taskId, now);
            log.info("Successfully processed task for event {}", eventId);

        } catch (Exception ex) {
            String message = truncateErrorMessage(ex.getMessage());
            persistence.markTaskError(taskId, now, message);
            log.error("Error processing task for event {}: {}", eventId, message, ex);
        }
    }

    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
