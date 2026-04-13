# Order Processing System — Transactional Outbox + RocketMQ Design

**Date:** 2026-04-13
**Status:** Approved

---

## 1. Overview

A Spring Boot order processing system that integrates MySQL and RocketMQ using the **Transactional Outbox Pattern** to achieve high data consistency. MySQL and RocketMQ are deployed on local Kubernetes. The application itself is deployable via Helm onto local Kubernetes.

---

## 2. Architecture

### 2.1 System Components

| Layer | Component | Description |
|---|---|---|
| API | `OrderController` | REST endpoints for order lifecycle |
| Service | `OrderService` | Business logic, transactional boundary |
| Persistence | `OrderRepository`, `OrderOutboxRepository` | Spring Data JPA repositories |
| Messaging | `TransactionalOutboxPublisher` | Wraps RocketMQ `TransactionalMessageProducer` |
| Messaging | `OrderEventConsumer` | Consumes order events from RocketMQ |
| Entity | `Order`, `OrderOutbox`, `OrderEventLog` | JPA entities |

### 2.2 Component Interaction

```
Client
  │
  ▼
OrderController ──▶ OrderService
                          │
              ┌───────────┴───────────┐
              │   DB Transaction     │
              │  ┌──────────────┐    │
              │  │ orders (CRUD)│    │
              │  │ order_outbox │    │
              │  └──────────────┘    │
              └──────────────────────┘
                          │
              TransactionalOutboxPublisher
              (RocketMQ sendMessageInTransaction)
                          │
                          ▼
                   RocketMQ Broker
                   (order-topic)
                          │
                          ▼
                   OrderEventConsumer
```

### 2.3 Consistency Model

The **Transactional Outbox Pattern** ensures that MySQL writes and RocketMQ publishes are atomic from the application's perspective:

1. Within a single DB transaction, the order is persisted AND an outbox event is written.
2. A separate publisher uses RocketMQ's transaction support to ensure the broker commit matches the local transaction outcome.
3. Consumer idempotency via `event_id` prevents duplicate processing.

---

## 3. Data Flow

### 3.1 Create Order

1. `POST /api/v1/orders` with order payload
2. `OrderService.createOrder()` starts DB transaction
3. Persists `orders` row (status `CREATED`)
4. Persists `order_outbox` row (status `PENDING`, event_type `ORDER_CREATED`)
5. DB transaction commits
6. `TransactionalOutboxPublisher` calls `sendMessageInTransaction()`
   - RocketMQ broker prepares the message
   - Local transaction callback executes — outbox status updated to `COMMITTED` or `FAILED`
   - Broker commit/rollback based on callback result
7. On success, outbox status → `PUBLISHED`

### 3.2 Update Order Status

1. `PUT /api/v1/orders/{orderNo}/status` with new status
2. `OrderService.updateOrderStatus()` validates current status
3. Starts DB transaction
4. Updates `orders.status`, sets `version` for optimistic locking
5. Persists `order_outbox` row (event_type `ORDER_STATUS_CHANGED`)
6. Same transactional publish flow as create

### 3.3 Event Consumption

1. `OrderEventConsumer` listens on `order-topic`
2. On message receipt, checks `event_id` in `order_event_log`
3. If already processed (duplicate), skip and ack
4. If new, process the event, insert `order_event_log` entry
5. Ack the message

---

## 4. Database Schema

### 4.1 `orders` Table

```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT 'Business order number',
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    amount DECIMAL(12,2) NOT NULL COMMENT 'Order total amount',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    INDEX idx_order_no (order_no),
    INDEX idx_status (status)
) COMMENT 'Order主表';
```

### 4.2 `order_outbox` Table

```sql
CREATE TABLE order_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL COMMENT 'Aggregate type: Order',
    aggregate_id VARCHAR(64) NOT NULL COMMENT 'order_no',
    event_type VARCHAR(64) NOT NULL COMMENT 'Event type: ORDER_CREATED, ORDER_STATUS_CHANGED',
    payload JSON NOT NULL COMMENT 'Event payload',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, COMMITTED, FAILED, PUBLISHED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_aggregate (aggregate_type, aggregate_id)
) COMMENT '事务发件箱，保证MySQL与RocketMQ一致性';
```

### 4.3 `order_event_log` Table

```sql
CREATE TABLE order_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Idempotency key',
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) COMMENT '事件消费日志，用于幂等性';
```

### 4.4 Order Status Enum

| Status | Description |
|---|---|
| `CREATED` | Order placed, awaiting payment |
| `PAID` | Payment confirmed |
| `PROCESSING` | Order being processed |
| `SHIPPED` | Order shipped |
| `COMPLETED` | Order completed |
| `CANCELLED` | Order cancelled |

---

## 5. API Specification

### 5.1 Create Order

**Request:**
```http
POST /api/v1/orders
Content-Type: application/json

{
    "orderNo": "ORD202604130001",
    "amount": 199.99
}
```

**Response (201):**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": 1,
        "orderNo": "ORD202604130001",
        "status": "CREATED",
        "amount": 199.99,
        "createdAt": "2026-04-13T10:30:00Z"
    }
}
```

### 5.2 Update Order Status

**Request:**
```http
PUT /api/v1/orders/{orderNo}/status
Content-Type: application/json

{
    "status": "PAID"
}
```

**Response (200):**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": 1,
        "orderNo": "ORD202604130001",
        "status": "PAID",
        "amount": 199.99,
        "updatedAt": "2026-04-13T10:35:00Z"
    }
}
```

### 5.3 Get Order

**Request:**
```http
GET /api/v1/orders/{orderNo}
```

**Response (200):**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": 1,
        "orderNo": "ORD202604130001",
        "status": "PAID",
        "amount": 199.99,
        "createdAt": "2026-04-13T10:30:00Z",
        "updatedAt": "2026-04-13T10:35:00Z"
    }
}
```

### 5.4 List Orders (Paginated)

**Request:**
```http
GET /api/v1/orders?page=0&size=20&status=CREATED
```

**Response (200):**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "content": [...],
        "totalElements": 100,
        "totalPages": 5,
        "page": 0,
        "size": 20
    }
}
```

---

## 6. RocketMQ Configuration

### 6.1 Producer Configuration

- **NameServer:** `rocketmq.middleware.svc.cluster.local:9876`
- **Producer Group:** `order-producer-group`
- **Send Timeout:** 3000ms
- **Retry times when send failed:** 2

### 6.2 Consumer Configuration

- **Consumer Group:** `order-consumer-group`
- **Topics subscribed:** `order-topic`
- **Subscription:** `*` (all tags)
- **Concurrency:** 3 consumers

### 6.3 Topic and Tags

| Topic | Tags | Description |
|---|---|---|
| `order-topic` | `ORDER_CREATED` | New order created |
| `order-topic` | `ORDER_STATUS_CHANGED` | Order status updated |

---

## 7. Helm Deployment

### 7.1 Chart Structure

```
order-processing/
├── Chart.yaml
├── values.yaml
├── templates/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml       # application.yml
│   ├── secret.yaml          # DB credentials
│   └── ingress.yaml         # optional
└── .helmignore
```

### 7.2 Values Override

All environment-specific values (MySQL URL, RocketMQ addresses, credentials) are configured via `values.yaml` or `--set` flags. No hardcoded values in templates.

---

## 8. Project Structure

```
order-processing-system/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/example/order/
│       │   ├── OrderApplication.java
│       │   ├── controller/
│       │   │   └── OrderController.java
│       │   ├── service/
│       │   │   ├── OrderService.java
│       │   │   └── OrderOutboxPublisher.java
│       │   ├── consumer/
│       │   │   └── OrderEventConsumer.java
│       │   ├── entity/
│       │   │   ├── Order.java
│       │   │   ├── OrderOutbox.java
│       │   │   └── OrderEventLog.java
│       │   ├── repository/
│       │   │   ├── OrderRepository.java
│       │   │   ├── OrderOutboxRepository.java
│       │   │   └── OrderEventLogRepository.java
│       │   ├── dto/
│       │   │   ├── CreateOrderRequest.java
│       │   │   ├── UpdateStatusRequest.java
│       │   │   └── ApiResponse.java
│       │   ├── enums/
│       │   │   └── OrderStatus.java
│       │   └── config/
│       │       └── RocketMQConfig.java
│       └── resources/
│           └── application.yml
├── src/test/java/...
├── scripts/
│   └── init.sql              # Schema DDL
└── helm/
    └── order-processing/
        ├── Chart.yaml
        ├── values.yaml
        └── templates/
            └── ...
```

---

## 9. Error Handling

| Scenario | Handling |
|---|---|
| MySQL connection failure | Spring retry, throw `ServiceException` |
| RocketMQ broker unreachable | TransactionalMessageProducer retries, outbox stays `PENDING` |
| Duplicate event consumed | `OrderEventLog` check — skip if `event_id` exists |
| Optimistic lock failure | `OptimisticLockException` → return 409 Conflict |
| Invalid status transition | Business rule check in `OrderService` → return 400 |

---

## 10. Outbox Polling (Scheduled Retry)

A `@Scheduled` job runs every **30 seconds** to retry publishing outbox events that are stuck in `PENDING` or `COMMITTED` status:

1. Query `order_outbox` where `status IN ('PENDING', 'COMMITTED') AND created_at < NOW() - INTERVAL 30 SECOND`
2. For each row, call RocketMQ producer to resend
3. On success, update `status = 'PUBLISHED'`, set `published_at`

This handles cases where RocketMQ transaction callback failed after DB commit.

---

## 11. Acceptance Criteria

- [ ] Order create API persists to MySQL and publishes to RocketMQ atomically
- [ ] Order status update API follows the same transactional outbox pattern
- [ ] Consumer correctly processes events and handles duplicates idempotently
- [ ] Outbox retry mechanism handles broker failures
- [ ] Helm chart deploys the application to local Kubernetes
- [ ] All configurations externalized via values.yaml (no hardcoding)
- [ ] Unit tests cover OrderService and transactional logic
