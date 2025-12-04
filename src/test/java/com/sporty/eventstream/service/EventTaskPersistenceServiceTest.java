package com.sporty.eventstream.service;

import com.sporty.eventstream.model.entity.EventTaskEntity;
import com.sporty.eventstream.model.entity.EventTaskStatus;
import com.sporty.eventstream.repository.EventTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventTaskPersistenceServiceTest {

    @Mock
    private EventTaskRepository repository;

    @InjectMocks
    private EventTaskPersistenceService persistenceService;

    @Test
    @DisplayName("Should create new task when event does not exist")
    void shouldCreateNewTaskWhenEventDoesNotExist() {
        // Given
        String eventId = "event-123";
        when(repository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        persistenceService.updateEventStatus(eventId, true);

        // Then
        ArgumentCaptor<EventTaskEntity> taskCaptor = ArgumentCaptor.forClass(EventTaskEntity.class);
        verify(repository).save(taskCaptor.capture());
        
        EventTaskEntity savedTask = taskCaptor.getValue();
        assertThat(savedTask.getEventId()).isEqualTo(eventId);
        assertThat(savedTask.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(savedTask.getExecutionCount()).isEqualTo(0L);
        assertThat(savedTask.getCreatedAt()).isNotNull();
        assertThat(savedTask.getNextExecutionTime()).isNotNull();
    }

    @Test
    @DisplayName("Should update existing task to ACTIVE when live is true")
    void shouldUpdateExistingTaskToActive() {
        // Given
        String eventId = "event-123";
        EventTaskEntity existingTask = createTask(1L, eventId, EventTaskStatus.INACTIVE);
        when(repository.findByEventId(eventId)).thenReturn(Optional.of(existingTask));
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        persistenceService.updateEventStatus(eventId, true);

        // Then
        ArgumentCaptor<EventTaskEntity> taskCaptor = ArgumentCaptor.forClass(EventTaskEntity.class);
        verify(repository).save(taskCaptor.capture());
        
        EventTaskEntity savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(savedTask.getNextExecutionTime()).isNotNull();
    }

    @Test
    @DisplayName("Should update existing task to INACTIVE when live is false")
    void shouldUpdateExistingTaskToInactive() {
        // Given
        String eventId = "event-123";
        EventTaskEntity existingTask = createTask(1L, eventId, EventTaskStatus.ACTIVE);
        when(repository.findByEventId(eventId)).thenReturn(Optional.of(existingTask));
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        persistenceService.updateEventStatus(eventId, false);

        // Then
        ArgumentCaptor<EventTaskEntity> taskCaptor = ArgumentCaptor.forClass(EventTaskEntity.class);
        verify(repository).save(taskCaptor.capture());
        
        EventTaskEntity savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo(EventTaskStatus.INACTIVE);
        assertThat(savedTask.getNextExecutionTime()).isNull();
    }

    @Test
    @DisplayName("Should release stuck IN_PROGRESS tasks")
    void shouldReleaseStuckInProgressTasks() {
        // Given
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(30);
        
        EventTaskEntity task1 = createTask(1L, "event-1", EventTaskStatus.IN_PROGRESS);
        EventTaskEntity task2 = createTask(2L, "event-2", EventTaskStatus.IN_PROGRESS);
        List<EventTaskEntity> stuckTasks = Arrays.asList(task1, task2);
        
        when(repository.findStuckInProgressTasks(threshold)).thenReturn(stuckTasks);
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        int released = persistenceService.releaseInProgressTasks(now, threshold);

        // Then
        assertThat(released).isEqualTo(2);
        verify(repository, times(2)).save(any(EventTaskEntity.class));
        
        assertThat(task1.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(task2.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(task1.getNextExecutionTime()).isEqualTo(now);
        assertThat(task2.getNextExecutionTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should return 0 when no stuck tasks found")
    void shouldReturnZeroWhenNoStuckTasks() {
        // Given
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(30);
        when(repository.findStuckInProgressTasks(threshold)).thenReturn(Collections.emptyList());

        // When
        int released = persistenceService.releaseInProgressTasks(now, threshold);

        // Then
        assertThat(released).isEqualTo(0);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should claim tasks and mark them as IN_PROGRESS")
    void shouldClaimTasksAndMarkAsInProgress() {
        // Given
        Instant now = Instant.now();
        int batchSize = 10;
        
        EventTaskEntity task1 = createTask(1L, "event-1", EventTaskStatus.ACTIVE);
        EventTaskEntity task2 = createTask(2L, "event-2", EventTaskStatus.ACTIVE);
        List<EventTaskEntity> tasks = Arrays.asList(task1, task2);
        Page<EventTaskEntity> page = new PageImpl<>(tasks);
        
        when(repository.findReadyTasks(eq(EventTaskStatus.ACTIVE), eq(now), any(PageRequest.class)))
                .thenReturn(page);
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<Long> taskIds = persistenceService.claimTasksForProcessing(now, batchSize);

        // Then
        assertThat(taskIds).containsExactly(1L, 2L);
        assertThat(task1.getStatus()).isEqualTo(EventTaskStatus.IN_PROGRESS);
        assertThat(task2.getStatus()).isEqualTo(EventTaskStatus.IN_PROGRESS);
        assertThat(task1.getLastExecutionTime()).isEqualTo(now);
        assertThat(task2.getLastExecutionTime()).isEqualTo(now);
        verify(repository, times(2)).save(any(EventTaskEntity.class));
    }

    @Test
    @DisplayName("Should return empty list when no tasks ready for processing")
    void shouldReturnEmptyListWhenNoTasksReady() {
        // Given
        Instant now = Instant.now();
        int batchSize = 10;
        Page<EventTaskEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        
        when(repository.findReadyTasks(eq(EventTaskStatus.ACTIVE), eq(now), any(PageRequest.class)))
                .thenReturn(emptyPage);

        // When
        List<Long> taskIds = persistenceService.claimTasksForProcessing(now, batchSize);

        // Then
        assertThat(taskIds).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should get event ID by task ID")
    void shouldGetEventIdByTaskId() {
        // Given
        Long taskId = 1L;
        String eventId = "event-123";
        EventTaskEntity task = createTask(taskId, eventId, EventTaskStatus.ACTIVE);
        when(repository.findById(taskId)).thenReturn(Optional.of(task));

        // When
        String result = persistenceService.getEventIdByTaskId(taskId);

        // Then
        assertThat(result).isEqualTo(eventId);
    }

    @Test
    @DisplayName("Should throw exception when task not found by ID")
    void shouldThrowExceptionWhenTaskNotFoundById() {
        // Given
        Long taskId = 999L;
        when(repository.findById(taskId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> persistenceService.getEventIdByTaskId(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task not found: 999");
    }

    @Test
    @DisplayName("Should mark task as success and schedule next execution")
    void shouldMarkTaskSuccess() {
        // Given
        Long taskId = 1L;
        Instant executionTime = Instant.now();
        EventTaskEntity task = createTask(taskId, "event-1", EventTaskStatus.IN_PROGRESS);
        task.setExecutionCount(5L);
        task.setLastError("Previous error");
        
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        persistenceService.markTaskSuccess(taskId, executionTime);

        // Then
        ArgumentCaptor<EventTaskEntity> taskCaptor = ArgumentCaptor.forClass(EventTaskEntity.class);
        verify(repository).save(taskCaptor.capture());
        
        EventTaskEntity savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(savedTask.getExecutionCount()).isEqualTo(6L);
        assertThat(savedTask.getLastError()).isNull();
        assertThat(savedTask.getLastErrorTime()).isNull();
    }

    @Test
    @DisplayName("Should mark task as error and schedule retry")
    void shouldMarkTaskError() {
        // Given
        Long taskId = 1L;
        Instant executionTime = Instant.now();
        String errorMessage = "API Error";
        EventTaskEntity task = createTask(taskId, "event-1", EventTaskStatus.IN_PROGRESS);
        task.setExecutionCount(5L);
        
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(repository.save(any(EventTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        persistenceService.markTaskError(taskId, executionTime, errorMessage);

        // Then
        ArgumentCaptor<EventTaskEntity> taskCaptor = ArgumentCaptor.forClass(EventTaskEntity.class);
        verify(repository).save(taskCaptor.capture());
        
        EventTaskEntity savedTask = taskCaptor.getValue();
        assertThat(savedTask.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(savedTask.getExecutionCount()).isEqualTo(6L);
        assertThat(savedTask.getLastError()).isEqualTo(errorMessage);
        assertThat(savedTask.getLastErrorTime()).isEqualTo(executionTime);
    }

    @Test
    @DisplayName("Should throw exception when marking success for non-existent task")
    void shouldThrowExceptionWhenMarkingSuccessForNonExistentTask() {
        // Given
        Long taskId = 999L;
        when(repository.findById(taskId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> persistenceService.markTaskSuccess(taskId, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task not found: 999");
    }

    @Test
    @DisplayName("Should throw exception when marking error for non-existent task")
    void shouldThrowExceptionWhenMarkingErrorForNonExistentTask() {
        // Given
        Long taskId = 999L;
        when(repository.findById(taskId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> persistenceService.markTaskError(taskId, Instant.now(), "error"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task not found: 999");
    }

    private EventTaskEntity createTask(Long id, String eventId, EventTaskStatus status) {
        EventTaskEntity task = new EventTaskEntity();
        task.setId(id);
        task.setEventId(eventId);
        task.setStatus(status);
        task.setCreatedAt(Instant.now());
        task.setExecutionCount(0L);
        return task;
    }
}

