# Event Stream Service

Spring Boot service that polls live event scores from an external API and publishes them to Kafka.  
It exposes a small REST API to start/stop monitoring events and stores monitoring tasks in H2.

## What this service does

- **Polls external scores**: Calls a mock external HTTP API for live event scores.
- **Publishes to Kafka**: Sends score updates to a Kafka topic (`live-events-scores`) as JSON messages.
- **Manages event tasks**: Persists event monitoring tasks in H2 and periodically picks them up for processing.
- **Exposes REST API**: Single control endpoint to start/stop monitoring specific events.
- **Documents API**: Swagger UI available for trying the API in the browser.

## Prerequisites

- **Java 17+**
- **Maven**
- **Docker & Docker Compose** (for Kafka and Docker-based runs)

## Setup & Run

### Local development (Maven + local Kafka)

```bash
# Start Kafka & Zookeeper
docker-compose up -d kafka zookeeper

# Build and run the application
./mvnw clean package
./mvnw spring-boot:run
```

**Access (local profile)**:
- **App**: `http://localhost:8088`
- **Swagger UI**: `http://localhost:8088/swagger-ui.html`
- **H2 Console**: `http://localhost:8088/h2-console`  
  - JDBC URL: `jdbc:h2:file:./data/eventdb`  
  - User: `sa`  
  - Password: (empty)

Kafka is expected at `localhost:9092` (see `application.yml`).

### Docker (all-in-one)

```bash
# Build image and start app + Kafka stack
docker-compose up --build
```

**Access (Docker setup)**:
- **App**: `http://localhost:8088`
- **Swagger UI**: `http://localhost:8088/swagger-ui.html`
- **H2 Console**: `http://localhost:8088/h2-console` (in-memory DB for docker profile)

In Docker, the app uses:
- **H2 in-memory DB**: `jdbc:h2:mem:eventdb`
- **Kafka bootstrap server**: `kafka:29092` (internal Docker network)

## API usage (main endpoint)

- **Endpoint**: `POST /api/v1/events/status`
- **Purpose**: Start or stop monitoring of a specific event by ID.

```bash
# Start polling an event
curl -X POST http://localhost:8088/api/v1/events/status \
  -H "Content-Type: application/json" \
  -d '{"eventId": "event-123", "live": true}'

# Stop polling an event
curl -X POST http://localhost:8088/api/v1/events/status \
  -H "Content-Type: application/json" \
  -d '{"eventId": "event-123", "live": false}'
```

See **Swagger UI** for complete documentation: `http://localhost:8088/swagger-ui.html`.

## Configuration overview

- **Server port**: `8088` (configured in `application.yml` / `application-docker.yml`)
- **Database**:
  - Local: file-based H2 at `jdbc:h2:file:./data/eventdb`
  - Docker: in-memory H2 at `jdbc:h2:mem:eventdb`
- **Kafka**:
  - Topic: `live-events-scores`
  - Local bootstrap: `localhost:9092`
  - Docker bootstrap: `kafka:29092`

## Running tests

```bash
./mvnw test
```

## High-level architecture

- **Scheduler (`EventTaskScheduler`)**: Periodically selects pending event tasks and publishes `TaskProcessingEvent`.
- **Asynchronous listener (`TaskProcessingListener`)**: Listens for `TaskProcessingEvent` and processes them in a thread pool configured by `AsyncConfig`.
- **External client (`ExternalServiceEventScoreClient`)**: Calls the external score API.
- **Kafka publisher (`KafkaEventMessagePublisher`)**: Publishes score updates to the configured Kafka topic.
This design uses Spring events with `@Async` to decouple scheduling from processing, improve scalability, and keep processing logic testable and failure-isolated.

