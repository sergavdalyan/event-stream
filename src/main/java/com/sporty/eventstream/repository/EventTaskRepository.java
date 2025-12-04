package com.sporty.eventstream.repository;

import com.sporty.eventstream.model.entity.EventTaskEntity;
import com.sporty.eventstream.model.entity.EventTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventTaskRepository extends JpaRepository<EventTaskEntity, Long> {

    Optional<EventTaskEntity> findByEventId(String eventId);

    @Query("""
            select t
            from EventTaskEntity t
            where t.status = :status
              and t.nextExecutionTime <= :now
            order by t.nextExecutionTime asc
            """)
    Page<EventTaskEntity> findReadyTasks(@Param("status") EventTaskStatus status,
                                         @Param("now") Instant now,
                                         Pageable pageable);

    @Query("""
            select t
            from EventTaskEntity t
            where t.status = 'IN_PROGRESS'
              and t.lastExecutionTime < :threshold
            """)
    List<EventTaskEntity> findStuckInProgressTasks(@Param("threshold") Instant threshold);
}
