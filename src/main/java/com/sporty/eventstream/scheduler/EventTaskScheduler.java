package com.sporty.eventstream.scheduler;

import com.sporty.eventstream.logging.TraceIdContext;
import com.sporty.eventstream.service.EventTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventTaskScheduler {

    private final EventTaskService eventTaskService;

    @Scheduled(fixedRateString = "${events.task-processor-interval-ms:1000}")
    public void processTasks() {
        String traceId = TraceIdContext.generate();
        TraceIdContext.setTraceId(traceId);
        
        log.debug("Scheduler tick triggered with traceId: {}", traceId);
        
        try {
            eventTaskService.processDueTasks();
            log.debug("Scheduler tick completed successfully");
        } catch (Exception ex) {
            log.error("Unexpected error in scheduler", ex);
        } finally {
            TraceIdContext.clear();
        }
    }
}
