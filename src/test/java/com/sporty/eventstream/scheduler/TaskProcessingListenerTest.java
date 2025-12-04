package com.sporty.eventstream.scheduler;

import com.sporty.eventstream.client.ExternalServiceEventScoreClient;
import com.sporty.eventstream.messaging.KafkaEventMessagePublisher;
import com.sporty.eventstream.model.event.TaskProcessingEvent;
import com.sporty.eventstream.model.kafka.EventScoreMessage;
import com.sporty.eventstream.service.EventTaskPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskProcessingListenerTest {

    @Mock
    private EventTaskPersistenceService persistence;

    @Mock
    private ExternalServiceEventScoreClient serviceEventClient;

    @Mock
    private KafkaEventMessagePublisher eventScorePublisher;

    @InjectMocks
    private TaskProcessingListener listener;

    @Captor
    private ArgumentCaptor<EventScoreMessage> messageCaptor;

    private static final Long TASK_ID = 123L;
    private static final String EVENT_ID = "event-456";
    private static final String SCORE = "3-2";
    private Instant processingTime;

    @BeforeEach
    void setUp() {
        processingTime = Instant.now();
    }

    @Test
    void shouldSuccessfullyProcessTask() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        when(serviceEventClient.fetchScore(EVENT_ID)).thenReturn(SCORE);

        // When
        listener.handleTaskProcessing(event);

        verify(serviceEventClient).fetchScore(EVENT_ID);
        verify(eventScorePublisher).publish(messageCaptor.capture());
        EventScoreMessage publishedMessage = messageCaptor.getValue();
        assertThat(publishedMessage.eventId()).isEqualTo(EVENT_ID);
        assertThat(publishedMessage.score()).isEqualTo(SCORE);
        assertThat(publishedMessage.timestamp()).isEqualTo(processingTime);
        verify(persistence).markTaskSuccess(TASK_ID, processingTime);
        verify(persistence, never()).markTaskError(any(), any(), any());
    }

    @Test
    void shouldHandleExternalServiceFailure() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        String errorMessage = "Connection timeout";
        when(serviceEventClient.fetchScore(EVENT_ID))
                .thenThrow(new RestClientException(errorMessage));

        // When
        listener.handleTaskProcessing(event);

        verify(serviceEventClient).fetchScore(EVENT_ID);
        verify(eventScorePublisher, never()).publish(any());
        verify(persistence).markTaskError(TASK_ID, processingTime, errorMessage);
        verify(persistence, never()).markTaskSuccess(any(), any());
    }

    @Test
    void shouldHandleKafkaPublishFailure() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        String errorMessage = "Kafka broker not available";
        when(serviceEventClient.fetchScore(EVENT_ID)).thenReturn(SCORE);
        doThrow(new RuntimeException(errorMessage))
                .when(eventScorePublisher).publish(any(EventScoreMessage.class));

        // When
        listener.handleTaskProcessing(event);

        verify(serviceEventClient).fetchScore(EVENT_ID);
        verify(eventScorePublisher).publish(any(EventScoreMessage.class));
        verify(persistence).markTaskError(TASK_ID, processingTime, errorMessage);
        verify(persistence, never()).markTaskSuccess(any(), any());
    }

    @Test
    void shouldHandlePersistenceFailureOnSuccess() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        String errorMessage = "Database connection failed";
        when(serviceEventClient.fetchScore(EVENT_ID)).thenReturn(SCORE);
        doThrow(new RuntimeException(errorMessage))
                .when(persistence).markTaskSuccess(TASK_ID, processingTime);

        // When
        listener.handleTaskProcessing(event);

        verify(serviceEventClient).fetchScore(EVENT_ID);
        verify(eventScorePublisher).publish(any(EventScoreMessage.class));
        verify(persistence).markTaskSuccess(TASK_ID, processingTime);
        verify(persistence).markTaskError(TASK_ID, processingTime, errorMessage);
    }

    @Test
    void shouldTruncateLongErrorMessages() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        String longErrorMessage = "Error: " + "X".repeat(1500);
        when(serviceEventClient.fetchScore(EVENT_ID))
                .thenThrow(new RuntimeException(longErrorMessage));

        // When
        listener.handleTaskProcessing(event);

        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistence).markTaskError(eq(TASK_ID), eq(processingTime), errorMessageCaptor.capture());
        String capturedErrorMessage = errorMessageCaptor.getValue();
        assertThat(capturedErrorMessage).hasSize(1000);
        assertThat(capturedErrorMessage).startsWith("Error: XXX");
    }

    @Test
    void shouldHandleNullErrorMessage() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        when(serviceEventClient.fetchScore(EVENT_ID))
                .thenThrow(new RuntimeException((String) null));

        // When
        listener.handleTaskProcessing(event);

        verify(persistence).markTaskError(TASK_ID, processingTime, null);
    }

    @Test
    void shouldProcessCorrectEventData() {
        // Given
        Long customTaskId = 999L;
        String customEventId = "custom-event-123";
        Instant customTime = Instant.parse("2023-12-01T10:00:00Z");
        String customScore = "5-0";

        TaskProcessingEvent event = new TaskProcessingEvent(customTaskId, customEventId, customTime);
        when(serviceEventClient.fetchScore(customEventId)).thenReturn(customScore);

        // When
        listener.handleTaskProcessing(event);

        verify(serviceEventClient).fetchScore(customEventId);
        verify(eventScorePublisher).publish(messageCaptor.capture());
        EventScoreMessage message = messageCaptor.getValue();
        assertThat(message.eventId()).isEqualTo(customEventId);
        assertThat(message.score()).isEqualTo(customScore);
        assertThat(message.timestamp()).isEqualTo(customTime);
        verify(persistence).markTaskSuccess(customTaskId, customTime);
    }

    @Test
    void shouldHandleMultipleFailures() {
        // Given
        TaskProcessingEvent event = new TaskProcessingEvent(TASK_ID, EVENT_ID, processingTime);
        when(serviceEventClient.fetchScore(EVENT_ID)).thenReturn(SCORE);
        doThrow(new RuntimeException("Kafka failed"))
                .when(eventScorePublisher).publish(any(EventScoreMessage.class));

        // When
        listener.handleTaskProcessing(event);

        verify(persistence).markTaskError(eq(TASK_ID), eq(processingTime), eq("Kafka failed"));
    }
}

