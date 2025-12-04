package com.sporty.eventstream.controller;

import com.sporty.eventstream.messaging.KafkaEventMessagePublisher;
import com.sporty.eventstream.model.entity.EventTaskEntity;
import com.sporty.eventstream.model.entity.EventTaskStatus;
import com.sporty.eventstream.repository.EventTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:9999"
})
class EventStatusControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @MockBean
    private KafkaEventMessagePublisher kafkaPublisher;

    @BeforeEach
    void setUp() {
        // Clear any existing data
        eventTaskRepository.deleteAll();
    }

    @Test
    void shouldAcceptValidRequestWithLiveTrue() throws Exception {
        // Given
        String eventId = "event-123";
        String requestBody = """
                {
                    "eventId": "event-123",
                    "live": true
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        // Then - verify task was created in database
        Optional<EventTaskEntity> taskOpt = eventTaskRepository.findByEventId(eventId);
        assertThat(taskOpt).isPresent();
        EventTaskEntity task = taskOpt.get();
        assertThat(task.getEventId()).isEqualTo(eventId);
        assertThat(task.getStatus()).isEqualTo(EventTaskStatus.ACTIVE);
        assertThat(task.getNextExecutionTime()).isNotNull();
    }

    @Test
    void shouldAcceptValidRequestWithLiveFalse() throws Exception {
        // Given
        String eventId = "event-456";
        EventTaskEntity existingTask = new EventTaskEntity();
        existingTask.setEventId(eventId);
        existingTask.setStatus(EventTaskStatus.ACTIVE);
        existingTask.setNextExecutionTime(Instant.now().plusSeconds(10));
        existingTask.setCreatedAt(Instant.now());
        existingTask.setUpdatedAt(Instant.now());
        eventTaskRepository.save(existingTask);

        String requestBody = """
                {
                    "eventId": "event-456",
                    "live": false
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());

        // Then
        Optional<EventTaskEntity> taskOpt = eventTaskRepository.findByEventId(eventId);
        assertThat(taskOpt).isPresent();
        EventTaskEntity task = taskOpt.get();
        assertThat(task.getEventId()).isEqualTo(eventId);
        assertThat(task.getStatus()).isEqualTo(EventTaskStatus.INACTIVE);
    }

    @Test
    void shouldRejectRequestWithMissingEventId() throws Exception {
        // Given
        String requestBody = """
                {
                    "live": true
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(eventTaskRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRejectRequestWithEmptyEventId() throws Exception {
        // Given
        String requestBody = """
                {
                    "eventId": "",
                    "live": true
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(eventTaskRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRejectRequestWithNullLiveField() throws Exception {
        // Given
        String requestBody = """
                {
                    "eventId": "event-123",
                    "live": null
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(eventTaskRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRejectRequestWithMissingLiveField() throws Exception {
        // Given
        String requestBody = """
                {
                    "eventId": "event-123"
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(eventTaskRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Should reject request with null eventId")
    void shouldRejectRequestWithNullEventId() throws Exception {
        // Given
        String requestBody = """
                {
                    "eventId": null,
                    "live": true
                }
                """;

        // When
        mockMvc.perform(post("/api/v1/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(eventTaskRepository.findAll()).isEmpty();
    }
}

