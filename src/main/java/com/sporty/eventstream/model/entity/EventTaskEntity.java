package com.sporty.eventstream.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "EVENT_TASK")
@Getter
@Setter
public class EventTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventTaskStatus status;

    @Column(name = "next_execution_time")
    private Instant nextExecutionTime;

    @Column(name = "last_execution_time")
    private Instant lastExecutionTime;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "execution_count")
    private Long executionCount = 0L;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "last_error_time")
    private Instant lastErrorTime;
}
