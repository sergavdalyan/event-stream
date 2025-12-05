package com.sporty.eventstream.scheduler;

import com.sporty.eventstream.logging.TraceIdContext;
import com.sporty.eventstream.service.EventTaskService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventTaskScheduler {

    private final EventTaskService eventTaskService;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Value("${events.task-processor-interval-min-ms:100}")
    private long minIntervalMs;

    @Value("${events.task-processor-interval-max-ms:5000}")
    private long maxIntervalMs;

    @Value("${events.task-processor-batch-size:100}")
    private int batchSize;

    @Value("${events.task-execution-interval-seconds:10}")
    private int executionIntervalSeconds;

    private final AtomicLong currentIntervalMs = new AtomicLong(1000); // Start with 1 second
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> scheduledFuture;

    @PostConstruct
    public void start() {
        log.info("Starting adaptive scheduler: polling interval {}-{}ms, task execution every {}s",
                minIntervalMs, maxIntervalMs, executionIntervalSeconds);
        scheduleNext();
    }

    @PreDestroy
    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("Stopped adaptive scheduler");
        }
    }

    private void scheduleNext() {
        long delayMs = currentIntervalMs.get();
        scheduledFuture = taskScheduler.schedule(this::processTasks, Instant.now().plusMillis(delayMs));
    }

    private void processTasks() {
        // Skip if previous execution is still running
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Previous execution still running, skipping");
            scheduleNext();
            return;
        }

        String traceId = TraceIdContext.generate();
        TraceIdContext.setTraceId(traceId);
        long startTime = System.currentTimeMillis();

        try {
            int processed = eventTaskService.processDueTasks();
            long duration = System.currentTimeMillis() - startTime;
            
            adjustInterval(processed, duration);
            
            log.info("traceId={} Processed {} tasks in {}ms, next interval: {}ms",
                    traceId, processed, duration, currentIntervalMs.get());

        } catch (Exception e) {
            log.error("traceId={} Error in scheduler", traceId, e);
            slowDown(500); // Back off on errors
        } finally {
            TraceIdContext.clear();
            isRunning.set(false);
            scheduleNext();
        }
    }

    private void adjustInterval(int processedCount, long durationMs) {
        long current = currentIntervalMs.get();
        long newInterval;

        if (processedCount == 0) {
            // No work
            newInterval = Math.min(maxIntervalMs, current + 500);
        } else if (processedCount >= batchSize) {
            // Full batch
            newInterval = Math.max(minIntervalMs, current - 200);
        } else if (processedCount < batchSize / 2) {
            // Light load
            newInterval = Math.min(maxIntervalMs, current + 100);
        } else {
            // Medium load - keep current pace
            newInterval = current;
        }

        // if processing took longer than interval, increase it
        if (durationMs > newInterval) {
            newInterval = Math.min(maxIntervalMs, durationMs + 100);
            log.warn("Processing took {}ms, adjusting interval to {}ms", durationMs, newInterval);
        }

        if (newInterval != current) {
            currentIntervalMs.set(newInterval);
            log.debug("Interval: {}ms → {}ms (processed: {}, duration: {}ms)",
                    current, newInterval, processedCount, durationMs);
        }
    }

    private void slowDown(long increaseMs) {
        currentIntervalMs.updateAndGet(current -> Math.min(maxIntervalMs, current + increaseMs));
    }
}
