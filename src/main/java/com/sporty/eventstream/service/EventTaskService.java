package com.sporty.eventstream.service;

import com.sporty.eventstream.model.event.TaskProcessingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTaskService {

    private final EventTaskPersistenceService persistence;
    private final ApplicationEventPublisher eventPublisher;


    @Value("${events.task-processor-batch-size:100}")
    private int batchSize;

    @Value("${events.task-in-progress-timeout-seconds:30}")
    private int inProgressTimeoutSeconds;

    public void updateEventStatus(String eventId, boolean live) {
        persistence.updateEventStatus(eventId, live);
    }

    /**
     * - Releases stuck IN_PROGRESS tasks
     * - Get all  ACTIVE tasks as IN_PROGRESS and publish event
     * @return number of tasks processed
     */
    public int processDueTasks() {
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
            return 0;
        }

        log.info("Claimed {} tasks, publishing async events for processing", taskIds.size());

        for (Long taskId : taskIds) {
            String eventId;
            try {
                eventId = persistence.getEventIdByTaskId(taskId);

                // Publish event - listener will process asynchronously
                TaskProcessingEvent event = new TaskProcessingEvent(taskId, eventId, now);
                eventPublisher.publishEvent(event);

                log.debug("Published async event for task {} (event {})", taskId, eventId);
            } catch (Exception e) {
                log.error("Failed to publish event for task {}: {}", taskId, e.getMessage(), e);
                // Mark as error so it can be retried
                persistence.markTaskError(taskId, now, "Failed to publish event: " + e.getMessage());
            }
        }

        return taskIds.size();
    }
}
