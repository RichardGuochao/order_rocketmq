# Order Processing System

A high-reliability order processing system built with Spring Boot and RocketMQ, implementing the **Transactional Outbox Pattern** for guaranteed message delivery.

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  REST API   │────▶│   MySQL     │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                   │
                           │                   │ (transactional)
                           ▼                   ▼
                    ┌─────────────┐     ┌─────────────┐
                    │  RocketMQ   │◀────│  Outbox     │
                    │  Broker     │     │  Table      │
                    └─────────────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  Consumer   │
                    └─────────────┘
```

## Tech Stack

| Component        | Technology                |
|------------------|---------------------------|
| Language         | Java 17                   |
| Framework        | Spring Boot 3.2.4         |
| Message Broker   | Apache RocketMQ 5.1.4      |
| Database         | MySQL 8.x                 |
| ORM              | Spring Data JPA / Hibernate |
| Build Tool       | Maven 3.9                 |
| Containerization | Docker, Helm              |
| Deployment       | Kubernetes                |

## Key Features

### Transactional Outbox Pattern

Guarantees exactly-once message delivery by storing events in an outbox table within the same transaction as business data:

1. Order + Outbox event written in single DB transaction
2. After commit, message sent to RocketMQ via local transaction
3. Failed messages are retried by `OutboxRetryScheduler`

### Order State Machine

Valid state transitions:
```
CREATED ──▶ PAID ──▶ PROCESSING ──▶ SHIPPED ──▶ COMPLETED
    │           │
    └───────────┴─────────────────────────────▶ CANCELLED
```

### Message Consumer

- Consumes order events from RocketMQ
- Idempotent processing via `OrderEventLog` deduplication
- Supports: `ORDER_CREATED`, `ORDER_STATUS_CHANGED`

## API Endpoints

| Method | Endpoint                      | Description           |
|--------|-------------------------------|-----------------------|
| POST   | `/api/v1/orders`              | Create new order      |
| GET    | `/api/v1/orders/{orderNo}`    | Get order by number   |
| GET    | `/api/v1/orders`              | List orders (paginated)|
| PUT    | `/api/v1/orders/{orderNo}/status` | Update order status |

### Create Order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-2024-001",
    "amount": 299.99
  }'
```

### Update Order Status

```bash
curl -X PUT http://localhost:8080/api/v1/orders/ORD-2024-001/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PAID"}'
```

## Configuration

### Environment Variables

| Variable                        | Default                                      | Description           |
|---------------------------------|----------------------------------------------|-----------------------|
| `DB_HOST`                       | `mysql.database.svc.cluster.local`            | MySQL host            |
| `DB_PORT`                       | `3306`                                       | MySQL port            |
| `DB_NAME`                       | `order`                                      | Database name         |
| `DB_USERNAME`                   | `root`                                       | Database user         |
| `DB_PASSWORD`                   | `root`                                       | Database password     |
| `ROCKETMQ_NAMESERVER`           | `rocketmq-namesrv.middleware.svc.cluster.local:9876` | RocketMQ nameserver |
| `ROCKETMQ_PRODUCER_GROUP`       | `order-producer-group`                       | Producer group        |
| `ROCKETMQ_CONSUMER_GROUP`       | `order-consumer-group`                       | Consumer group        |
| `ROCKETMQ_TOPIC_ORDER_EVENTS`   | `order-topic`                                | Order events topic    |
| `SERVER_PORT`                   | `8080`                                       | Application port      |
| `SPRING_PROFILES_ACTIVE`        | `default`                                    | Spring profile        |

### Database Connection Pool

- **Maximum pool size**: 20
- **Minimum idle**: 5
- **Connection timeout**: 30s

## Project Structure

```
order-processing-system/
├── src/main/java/com/example/order/
│   ├── OrderApplication.java              # Main entry point
│   ├── config/
│   │   └── RocketMQConfig.java           # RocketMQ configuration
│   ├── consumer/
│   │   └── OrderEventConsumer.java       # Event message consumer
│   ├── controller/
│   │   └── OrderController.java          # REST API endpoints
│   ├── dto/
│   │   ├── ApiResponse.java
│   │   ├── CreateOrderRequest.java
│   │   ├── OrderDTO.java
│   │   └── UpdateStatusRequest.java
│   ├── entity/
│   │   ├── Order.java                    # Order entity
│   │   ├── OrderEventLog.java            # Event deduplication log
│   │   └── OrderOutbox.java              # Outbox pattern table
│   ├── enums/
│   │   └── OrderStatus.java              # Order status enum
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java    # Global error handler
│   │   └── ServiceException.java          # Custom exceptions
│   ├── repository/
│   │   ├── OrderRepository.java
│   │   ├── OrderEventLogRepository.java
│   │   └── OrderOutboxRepository.java
│   └── service/
│       ├── OrderService.java             # Core order logic
│       ├── OrderOutboxPublisher.java     # Outbox publisher
│       └── OutboxRetryScheduler.java     # Failed message retry
├── src/main/resources/
│   ├── application.yml                   # Main configuration
│   └── application-default.yml           # Default profile config
├── src/test/
│   ├── java/com/example/order/
│   │   ├── consumer/OrderEventConsumerTest.java
│   │   └── service/OrderServiceTest.java
│   └── resources/application-test.yml
├── helm/order-processing/                # Kubernetes Helm chart
├── scripts/init.sql                      # Database initialization
├── Dockerfile                            # Multi-stage Docker build
└── pom.xml                               # Maven dependencies
```

## Database Schema

### orders

| Column      | Type           | Constraints                |
|-------------|----------------|---------------------------|
| id          | BIGINT         | PRIMARY KEY, AUTO_INCREMENT |
| order_no    | VARCHAR(64)    | UNIQUE, NOT NULL          |
| status      | VARCHAR(32)    | NOT NULL                  |
| amount      | DECIMAL(12,2) | NOT NULL                  |
| created_at  | TIMESTAMP      |                           |
| updated_at  | TIMESTAMP      |                           |
| version     | BIGINT         | Optimistic lock           |

### order_outbox

| Column          | Type      | Constraints                |
|-----------------|-----------|---------------------------|
| id              | BIGINT    | PRIMARY KEY, AUTO_INCREMENT |
| aggregate_type  | VARCHAR(64) | NOT NULL                |
| aggregate_id    | VARCHAR(64) | NOT NULL                |
| event_type      | VARCHAR(64) | NOT NULL                |
| payload         | JSON      | NOT NULL                  |
| status          | VARCHAR(32) | NOT NULL                |
| created_at      | TIMESTAMP |                           |
| published_at    | TIMESTAMP |                           |

### order_event_log

| Column      | Type         | Constraints                |
|-------------|--------------|---------------------------|
| id          | BIGINT       | PRIMARY KEY, AUTO_INCREMENT |
| event_id    | VARCHAR(64)  | UNIQUE, NOT NULL          |
| order_no    | VARCHAR(64)  | NOT NULL                  |
| event_type  | VARCHAR(64)  | NOT NULL                  |
| created_at  | TIMESTAMP    |                           |

## Building & Running

### Local Development

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/order-processing-system-1.0.0-SNAPSHOT.jar
```

### Docker

```bash
# Build image
docker build -t order-processing-system:1.0.0 ./order-processing-system

# Run container
docker run -p 8080:8080 \
  -e DB_HOST=localhost \
  -e DB_PASSWORD=secret \
  -e ROCKETMQ_NAMESERVER=localhost:9876 \
  order-processing-system:1.0.0
```

### Kubernetes (Helm)

```bash
# Install
helm install order-processing ./helm/order-processing

# Upgrade
helm upgrade order-processing ./helm/order-processing

# Uninstall
helm uninstall order-processing
```

#### Helm Values

| Key                    | Default                                    |
|------------------------|--------------------------------------------|
| `replicaCount`         | 2                                          |
| `image.repository`     | `guochaorichard/order-processing`          |
| `image.tag`            | `1.0.0.20260423`                           |
| `service.type`         | `ClusterIP`                                |
| `service.port`         | `8080`                                     |
| `resources.limits.cpu`| `1000m`                                    |
| `resources.limits.memory` | `1Gi`                                 |

## Testing

```bash
mvn test
```

Tests use H2 in-memory database with `application-test.yml` profile.

## Health Checks

- **Liveness**: `GET /actuator/health`
- **Readiness**: Includes DB and RocketMQ connectivity checks

## Event Message Format

```json
{
  "eventId": "uuid",
  "eventType": "ORDER_CREATED",
  "orderNo": "ORD-2024-001",
  "timestamp": "2024-04-24T10:30:00",
  "data": {
    "orderNo": "ORD-2024-001",
    "amount": "299.99",
    "status": "CREATED"
  }
}
```

## License

MIT
