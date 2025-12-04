package com.sporty.eventstream.service;

import com.sporty.eventstream.model.entity.EventTaskEntity;
import com.sporty.eventstream.model.entity.EventTaskStatus;
import com.sporty.eventstream.repository.EventTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTaskPersistenceService {

    private final EventTaskRepository repository;

    @Value("${events.task-execution-interval-seconds:10}")
    private int executionIntervalSeconds;


    @Transactional
    public void updateEventStatus(String eventId, boolean live) {
        Instant now = Instant.now();

        EventTaskEntity task = repository.findByEventId(eventId)
                .orElseGet(() -> {
                    EventTaskEntity t = new EventTaskEntity();
                    t.setEventId(eventId);
                    t.setCreatedAt(now);
                    t.setExecutionCount(0L);
                    return t;
                });

        if (live) {
            task.setStatus(EventTaskStatus.ACTIVE);
            task.setNextExecutionTime(now); // schedule immediate execution
        } else {
            task.setStatus(EventTaskStatus.INACTIVE);
            task.setNextExecutionTime(null); // nothing to schedule
        }

        task.setUpdatedAt(now);
        repository.save(task);

        log.info("Event {} status set to {} (task id={})",
                eventId, live ? "ACTIVE" : "INACTIVE", task.getId());
    }

    /**
     * Returns number of tasks released and set nexExecutionTime to Now.
     */
    @Transactional
    public int releaseInProgressTasks(Instant now, Instant stuckThreshold) {
        List<EventTaskEntity> stuckTasks = repository.findStuckInProgressTasks(stuckThreshold);
        if (stuckTasks.isEmpty()) {
            return 0;
        }

        for (EventTaskEntity task : stuckTasks) {
            task.setStatus(EventTaskStatus.ACTIVE);
            task.setNextExecutionTime(now); // retry immediately
            task.setUpdatedAt(now);
            repository.save(task);
        }

        return stuckTasks.size();
    }

    /**
     * Returns list of task IDs to process.
     */
    @Transactional
    public List<Long> claimTasksForProcessing(Instant now, int batchSize) {
        var pageRequest = PageRequest.of(0, batchSize);
        var page = repository.findReadyTasks(EventTaskStatus.ACTIVE, now, pageRequest);

        List<EventTaskEntity> tasks = page.getContent();
        if (tasks.isEmpty()) {
            return List.of();
        }

        if (page.getTotalElements() > batchSize) {
            log.warn("Found {} due tasks, claiming only {} in this run",
                    page.getTotalElements(), batchSize);
        }

        List<Long> taskIds = new ArrayList<>(tasks.size());
        for (EventTaskEntity task : tasks) {
            task.setStatus(EventTaskStatus.IN_PROGRESS);
            task.setLastExecutionTime(now); // mark start time of this attempt
            task.setUpdatedAt(now);
            repository.save(task);
            taskIds.add(task.getId());
        }

        log.debug("Claimed {} tasks for processing", taskIds.size());
        return taskIds;
    }


    @Transactional(readOnly = true)
    public String getEventIdByTaskId(Long taskId) {
        return repository.findById(taskId)
                .map(EventTaskEntity::getEventId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
    }

    /**
     * Mark task as successfully processed and schedule next run.
     */
    @Transactional
    public void markTaskSuccess(Long taskId, Instant executionTime) {
        EventTaskEntity task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        task.setStatus(EventTaskStatus.ACTIVE); // back to ACTIVE for next cycle
        task.setExecutionCount(task.getExecutionCount() + 1);
        task.setLastError(null);
        task.setLastErrorTime(null);
        task.setNextExecutionTime(executionTime.plus(Duration.ofSeconds(executionIntervalSeconds)));
        task.setUpdatedAt(Instant.now());

        repository.save(task);
    }

    /**
     * Mark task as failed but keep it ACTIVE for future retry.
     */
    @Transactional
    public void markTaskError(Long taskId, Instant executionTime, String errorMessage) {
        EventTaskEntity task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        task.setStatus(EventTaskStatus.ACTIVE);
        task.setExecutionCount(task.getExecutionCount() + 1);
        task.setLastError(errorMessage);
        task.setLastErrorTime(executionTime);
        task.setNextExecutionTime(executionTime.plus(Duration.ofSeconds(executionIntervalSeconds)));
        task.setUpdatedAt(Instant.now());

        repository.save(task);
    }
}
