# Order Processing System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Spring Boot order processing system using Transactional Outbox Pattern with RocketMQ for MySQL-RocketMQ consistency, deployable via Helm to local Kubernetes.

**Architecture:** Spring Boot 3.x + Spring Data JPA + RocketMQ TransactionalMessageProducer. MySQL stores orders and outbox events in one DB transaction; RocketMQ transaction ensures broker commit matches local outcome. Outbox retry scheduler handles failures. Consumer idempotency via event_id dedup table.

**Tech Stack:** Spring Boot 3.2, Spring Data JPA, RocketMQ 5.x Client, MySQL 8, Helm 3, JUnit 5, Kubernetes (local)

---

## File Structure

All files created under base path: `/Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system/`

```
pom.xml
src/main/java/com/example/order/
    OrderApplication.java
    controller/OrderController.java
    service/OrderService.java
    service/OrderOutboxPublisher.java
    service/OutboxRetryScheduler.java
    consumer/OrderEventConsumer.java
    entity/Order.java
    entity/OrderOutbox.java
    entity/OrderEventLog.java
    repository/OrderRepository.java
    repository/OrderOutboxRepository.java
    repository/OrderEventLogRepository.java
    dto/CreateOrderRequest.java
    dto/UpdateStatusRequest.java
    dto/ApiResponse.java
    dto/OrderDTO.java
    enums/OrderStatus.java
    config/RocketMQConfig.java
    exception/ServiceException.java
    exception/GlobalExceptionHandler.java
src/main/resources/
    application.yml
scripts/init.sql
helm/order-processing/
    Chart.yaml
    values.yaml
    .helmignore
    templates/deployment.yaml
    templates/service.yaml
    templates/configmap.yaml
    templates/secret.yaml
```

---

## Task 1: Maven Project Setup (pom.xml)

**Files:**
- Create: `order-processing-system/pom.xml`

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.4</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>order-processing-system</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>order-processing-system</name>
    <description>Order Processing System with Transactional Outbox and RocketMQ</description>

    <properties>
        <java.version>17</java.version>
        <rocketmq.version>5.1.4</rocketmq.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- MySQL Connector -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- RocketMQ -->
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-client</artifactId>
            <version>${rocketmq.version}</version>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- H2 for testing -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Commit**

```bash
cd /Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system
git init && git add pom.xml && git commit -m "feat: add Maven project with Spring Boot 3.2, RocketMQ 5.x, MySQL, JPA dependencies"
```

---

## Task 2: Application Main Class

**Files:**
- Create: `src/main/java/com/example/order/OrderApplication.java`

- [ ] **Step 1: Create OrderApplication.java**

```java
package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/OrderApplication.java
git commit -m "feat: add OrderApplication main class with @EnableScheduling and @EnableTransactionManagement"
```

---

## Task 3: Application Configuration (application.yml)

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-default.yml` (fallback profile)

- [ ] **Step 1: Create application.yml**

```yaml
spring:
  application:
    name: order-processing-system
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:default}
  datasource:
    url: jdbc:mysql://${DB_HOST:mysql.database.svc.cluster.local}:${DB_PORT:3306}/${DB_NAME:order_db}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true

server:
  port: ${SERVER_PORT:8080}

rocketmq:
  name-server: ${ROCKETMQ_NAMESERVER:rocketmq.middleware.svc.cluster.local:9876}
  producer:
    group: order-producer-group
    send-timeout: 3000
    retry-times-when-send-failed: 2
  consumer:
    group: order-consumer-group
  topics:
    order-events: order-topic

logging:
  level:
    com.example.order: DEBUG
    org.apache.rocketmq: INFO
```

- [ ] **Step 2: Create application-default.yml**

```yaml
# Default profile - values used when no explicit profile is active
spring:
  datasource:
    url: jdbc:mysql://mysql.database.svc.cluster.local:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: validate

rocketmq:
  name-server: rocketmq.middleware.svc.cluster.local:9876
  producer:
    group: order-producer-group
    send-timeout: 3000
    retry-times-when-send-failed: 2
  consumer:
    group: order-consumer-group
  topics:
    order-events: order-topic
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application.yml src/main/resources/application-default.yml
git commit -m "feat: add Spring Boot application.yml with MySQL and RocketMQ configuration"
```

---

## Task 4: Database Schema DDL

**Files:**
- Create: `scripts/init.sql`

- [ ] **Step 1: Create init.sql**

```sql
-- Order Processing System Database Schema

CREATE DATABASE IF NOT EXISTS order_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE order_db;

-- Order主表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT 'Business order number',
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    amount DECIMAL(12,2) NOT NULL COMMENT 'Order total amount',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    INDEX idx_order_no (order_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order主表';

-- 事务发件箱，保证MySQL与RocketMQ一致性
CREATE TABLE IF NOT EXISTS order_outbox (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务发件箱';

-- 事件消费日志，用于幂等性
CREATE TABLE IF NOT EXISTS order_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Idempotency key',
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件消费日志';
```

- [ ] **Step 2: Commit**

```bash
git add scripts/init.sql
git commit -m "feat: add MySQL schema DDL for orders, order_outbox, order_event_log tables"
```

---

## Task 5: Enum and Entity Classes

**Files:**
- Create: `src/main/java/com/example/order/enums/OrderStatus.java`
- Create: `src/main/java/com/example/order/entity/Order.java`
- Create: `src/main/java/com/example/order/entity/OrderOutbox.java`
- Create: `src/main/java/com/example/order/entity/OrderEventLog.java`

- [ ] **Step 1: Create OrderStatus.java**

```java
package com.example.order.enums;

public enum OrderStatus {
    CREATED,
    PAID,
    PROCESSING,
    SHIPPED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED;
            case PAID -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
```

- [ ] **Step 2: Create Order.java**

```java
package com.example.order.entity;

import com.example.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = OrderStatus.CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create OrderOutbox.java**

```java
package com.example.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = STATUS_PENDING;
        }
    }
}
```

- [ ] **Step 4: Create OrderEventLog.java**

```java
package com.example.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_event_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "processed_at", updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/order/enums/OrderStatus.java \
        src/main/java/com/example/order/entity/Order.java \
        src/main/java/com/example/order/entity/OrderOutbox.java \
        src/main/java/com/example/order/entity/OrderEventLog.java
git commit -m "feat: add OrderStatus enum and JPA entities (Order, OrderOutbox, OrderEventLog)"
```

---

## Task 6: Repository Interfaces

**Files:**
- Create: `src/main/java/com/example/order/repository/OrderRepository.java`
- Create: `src/main/java/com/example/order/repository/OrderOutboxRepository.java`
- Create: `src/main/java/com/example/order/repository/OrderEventLogRepository.java`

- [ ] **Step 1: Create OrderRepository.java**

```java
package com.example.order.repository;

import com.example.order.entity.Order;
import com.example.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    boolean existsByOrderNo(String orderNo);
}
```

- [ ] **Step 2: Create OrderOutboxRepository.java**

```java
package com.example.order.repository;

import com.example.order.entity.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {

    @Query("SELECT o FROM OrderOutbox o WHERE o.status IN :statuses AND o.createdAt < :threshold ORDER BY o.createdAt ASC")
    List<OrderOutbox> findPendingOutboxes(
            @Param("statuses") List<String> statuses,
            @Param("threshold") LocalDateTime threshold
    );

    @Modifying
    @Query("UPDATE OrderOutbox o SET o.status = :status, o.publishedAt = :publishedAt WHERE o.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("publishedAt") LocalDateTime publishedAt);
}
```

- [ ] **Step 3: Create OrderEventLogRepository.java**

```java
package com.example.order.repository;

import com.example.order.entity.OrderEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderEventLogRepository extends JpaRepository<OrderEventLog, Long> {

    boolean existsByEventId(String eventId);
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/order/repository/OrderRepository.java \
        src/main/java/com/example/order/repository/OrderOutboxRepository.java \
        src/main/java/com/example/order/repository/OrderEventLogRepository.java
git commit -m "feat: add Spring Data JPA repository interfaces"
```

---

## Task 7: DTOs and ApiResponse

**Files:**
- Create: `src/main/java/com/example/order/dto/CreateOrderRequest.java`
- Create: `src/main/java/com/example/order/dto/UpdateStatusRequest.java`
- Create: `src/main/java/com/example/order/dto/OrderDTO.java`
- Create: `src/main/java/com/example/order/dto/ApiResponse.java`

- [ ] **Step 1: Create CreateOrderRequest.java**

```java
package com.example.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "orderNo is required")
    private String orderNo;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;
}
```

- [ ] **Step 2: Create UpdateStatusRequest.java**

```java
package com.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @NotBlank(message = "status is required")
    private String status;
}
```

- [ ] **Step 3: Create OrderDTO.java**

```java
package com.example.order.dto;

import com.example.order.entity.Order;
import com.example.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;
    private String orderNo;
    private OrderStatus status;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderDTO fromEntity(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .amount(order.getAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 4: Create ApiResponse.java**

```java
package com.example.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(0)
                .message("success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/order/dto/CreateOrderRequest.java \
        src/main/java/com/example/order/dto/UpdateStatusRequest.java \
        src/main/java/com/example/order/dto/OrderDTO.java \
        src/main/java/com/example/order/dto/ApiResponse.java
git commit -m "feat: add DTOs (CreateOrderRequest, UpdateStatusRequest, OrderDTO, ApiResponse)"
```

---

## Task 8: RocketMQ Configuration

**Files:**
- Create: `src/main/java/com/example/order/config/RocketMQConfig.java`

- [ ] **Step 1: Create RocketMQConfig.java**

```java
package com.example.order.config;

import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.spring.config.DefaultRocketMQListenerContainerFactory;
import org.apache.rocketmq.spring.config.RocketMQProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    @Value("${rocketmq.consumer.group}")
    private String consumerGroup;

    @Bean
    public TransactionMQProducer transactionMQProducer(TransactionListener transactionListener) {
        TransactionMQProducer producer = new TransactionMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setTransactionListener(transactionListener);
        producer.setSendTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    @Bean
    public RocketMQTemplate rocketMQTemplate(TransactionMQProducer transactionMQProducer) {
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(transactionMQProducer);
        return template;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/config/RocketMQConfig.java
git commit -m "feat: add RocketMQ configuration with TransactionMQProducer and RocketMQTemplate"
```

---

## Task 9: OrderOutboxPublisher (Transactional Message Producer)

**Files:**
- Create: `src/main/java/com/example/order/service/OrderOutboxPublisher.java`

- [ ] **Step 1: Create OrderOutboxPublisher.java**

```java
package com.example.order.service;

import com.example.order.entity.OrderOutbox;
import com.example.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPublisher implements TransactionListener {

    private final OrderOutboxRepository orderOutboxRepository;

    @Value("${rocketmq.topics.order-events}")
    private String orderTopic;

    // Thread-local map to track transaction state per outbox ID
    private final ConcurrentHashMap<String, OrderOutbox> transactionStates = new ConcurrentHashMap<>();

    /**
     * Send message in transaction. This is called after DB commit.
     * Returns the transaction state that will be confirmed by checkTransactionState.
     */
    public void sendMessageInTransaction(OrderOutbox outbox) {
        transactionStates.put(String.valueOf(outbox.getId()), outbox);
    }

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        OrderOutbox outbox = (OrderOutbox) arg;
        try {
            // Update outbox status to COMMITTED within the local transaction
            orderOutboxRepository.updateStatus(
                    outbox.getId(),
                    OrderOutbox.STATUS_COMMITTED,
                    LocalDateTime.now()
            );
            log.debug("Outbox {} committed locally", outbox.getId());
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            log.error("Failed to commit outbox {} locally: {}", outbox.getId(), e.getMessage());
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String keys = msg.getKeys();
        log.debug("Checking transaction state for message key: {}", keys);
        // For our implementation, we rely on the outbox status to determine state
        // If we reach here, the broker couldn't confirm - return UNKNOW to retry
        return LocalTransactionState.UNKNOW;
    }

    /**
     * Update outbox status to indicate failure.
     */
    @Transactional
    public void markFailed(Long outboxId) {
        orderOutboxRepository.updateStatus(
                outboxId,
                OrderOutbox.STATUS_FAILED,
                LocalDateTime.now()
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/service/OrderOutboxPublisher.java
git commit -m "feat: add OrderOutboxPublisher implementing RocketMQ TransactionListener"
```

---

## Task 10: OrderService (Business Logic + Transactional Boundary)

**Files:**
- Create: `src/main/java/com/example/order/service/OrderService.java`

- [ ] **Step 1: Create OrderService.java**

```java
package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDTO;
import com.example.order.dto.UpdateStatusRequest;
import com.example.order.entity.Order;
import com.example.order.entity.OrderOutbox;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.ServiceException;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository orderOutboxRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.topics.order-events}")
    private String orderTopic;

    @Transactional
    public OrderDTO createOrder(CreateOrderRequest request) {
        if (orderRepository.existsByOrderNo(request.getOrderNo())) {
            throw ServiceException.conflict("Order already exists: " + request.getOrderNo());
        }

        Order order = Order.builder()
                .orderNo(request.getOrderNo())
                .amount(request.getAmount())
                .status(OrderStatus.CREATED)
                .build();
        order = orderRepository.save(order);

        // Build outbox event
        String payload = buildEventPayload(order.getOrderNo(), OrderOutbox.EVENT_ORDER_CREATED,
                Map.of("orderNo", order.getOrderNo(), "amount", order.getAmount(), "status", order.getStatus().name()));

        OrderOutbox outbox = OrderOutbox.builder()
                .aggregateType("Order")
                .aggregateId(order.getOrderNo())
                .eventType(OrderOutbox.EVENT_ORDER_CREATED)
                .payload(payload)
                .status(OrderOutbox.STATUS_PENDING)
                .build();
        outbox = orderOutboxRepository.save(outbox);

        // Register synchronization to send message after commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendTransactionMessage(outbox, order.getOrderNo(), "ORDER_CREATED");
            }
        });

        log.info("Order created: {}", order.getOrderNo());
        return OrderDTO.fromEntity(order);
    }

    @Transactional
    public OrderDTO updateOrderStatus(String orderNo, UpdateStatusRequest request) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> ServiceException.notFound("Order not found: " + orderNo));

        OrderStatus newStatus = OrderStatus.valueOf(request.getStatus().toUpperCase());
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw ServiceException.badRequest(
                    "Invalid status transition from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);

        // Build outbox event
        String payload = buildEventPayload(order.getOrderNo(), OrderOutbox.EVENT_ORDER_STATUS_CHANGED,
                Map.of("orderNo", order.getOrderNo(), "oldStatus", order.getStatus().name(),
                        "newStatus", newStatus.name(), "updatedAt", LocalDateTime.now().toString()));

        OrderOutbox outbox = OrderOutbox.builder()
                .aggregateType("Order")
                .aggregateId(order.getOrderNo())
                .eventType(OrderOutbox.EVENT_ORDER_STATUS_CHANGED)
                .payload(payload)
                .status(OrderOutbox.STATUS_PENDING)
                .build();
        outbox = orderOutboxRepository.save(outbox);

        // Register synchronization to send message after commit
        final OrderOutbox finalOutbox = outbox;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendTransactionMessage(finalOutbox, order.getOrderNo(), "ORDER_STATUS_CHANGED");
            }
        });

        log.info("Order status updated: {} -> {}", orderNo, newStatus);
        return OrderDTO.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrder(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> ServiceException.notFound("Order not found: " + orderNo));
        return OrderDTO.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderDTO> listOrders(OrderStatus status, Pageable pageable) {
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return orders.map(OrderDTO::fromEntity);
    }

    private void sendTransactionMessage(OrderOutbox outbox, String orderNo, String tag) {
        String eventId = UUID.randomUUID().toString();
        org.apache.rocketmq.common.message.Message message = new org.apache.rocketmq.common.message.Message(
                orderTopic,
                tag,
                eventId,
                outbox.getPayload().getBytes()
        );
        try {
            rocketMQTemplate.getProducer().sendMessageInTransaction(message, outbox);
            log.debug("Transaction message sent for outbox {}: eventId={}", outbox.getId(), eventId);
        } catch (Exception e) {
            log.error("Failed to send transaction message for outbox {}: {}", outbox.getId(), e.getMessage());
            orderOutboxPublisher.markFailed(outbox.getId());
        }
    }

    private String buildEventPayload(String orderNo, String eventType, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("orderNo", orderNo);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("data", data);
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/service/OrderService.java
git commit -m "feat: add OrderService with transactional outbox pattern for create/update"
```

---

## Task 11: Outbox Retry Scheduler

**Files:**
- Create: `src/main/java/com/example/order/service/OutboxRetryScheduler.java`

- [ ] **Step 1: Create OutboxRetryScheduler.java**

```java
package com.example.order.service;

import com.example.order.entity.OrderOutbox;
import com.example.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRetryScheduler {

    private final OrderOutboxRepository orderOutboxRepository;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.topics.order-events}")
    private String orderTopic;

    @Scheduled(fixedDelayString = "${OUTBOX_RETRY_INTERVAL:30000}")
    @Transactional
    public void retryPendingOutboxes() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<OrderOutbox> pendingOutboxes = orderOutboxRepository.findPendingOutboxes(
                Arrays.asList(OrderOutbox.STATUS_PENDING, OrderOutbox.STATUS_COMMITTED),
                threshold
        );

        if (pendingOutboxes.isEmpty()) {
            return;
        }

        log.info("Retrying {} pending outbox events", pendingOutboxes.size());
        for (OrderOutbox outbox : pendingOutboxes) {
            try {
                String tag = OrderOutbox.EVENT_ORDER_CREATED.equals(outbox.getEventType())
                        ? "ORDER_CREATED" : "ORDER_STATUS_CHANGED";

                org.apache.rocketmq.common.message.Message message = new org.apache.rocketmq.common.message.Message(
                        orderTopic,
                        tag,
                        UUID.randomUUID().toString(),
                        outbox.getPayload().getBytes()
                );

                // Synchronous send for retry
                rocketMQTemplate.getProducer().send(message, 3000);
                orderOutboxRepository.updateStatus(outbox.getId(), OrderOutbox.STATUS_PUBLISHED, LocalDateTime.now());
                log.debug("Outbox {} republished successfully", outbox.getId());
            } catch (Exception e) {
                log.error("Failed to republish outbox {}: {}", outbox.getId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/service/OutboxRetryScheduler.java
git commit -m "feat: add OutboxRetryScheduler for retrying stuck outbox events every 30s"
```

---

## Task 12: Exception Handling

**Files:**
- Create: `src/main/java/com/example/order/exception/ServiceException.java`
- Create: `src/main/java/com/example/order/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create ServiceException.java**

```java
package com.example.order.exception;

import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {

    private final int code;
    private final String message;

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public static ServiceException notFound(String message) {
        return new ServiceException(404, message);
    }

    public static ServiceException badRequest(String message) {
        return new ServiceException(400, message);
    }

    public static ServiceException conflict(String message) {
        return new ServiceException(409, message);
    }

    public static ServiceException internal(String message) {
        return new ServiceException(500, message);
    }
}
```

- [ ] **Step 2: Create GlobalExceptionHandler.java**

```java
package com.example.order.exception;

import com.example.order.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceException(ServiceException e) {
        log.warn("Service exception: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = switch (e.getCode()) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 400 -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockException(ObjectOptimisticLockingFailureException e) {
        log.warn("Optimistic lock failure: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "Concurrent modification detected, please retry"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest().body(ApiResponse.error(400, errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Internal server error"));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/order/exception/ServiceException.java \
        src/main/java/com/example/order/exception/GlobalExceptionHandler.java
git commit -m "feat: add ServiceException and GlobalExceptionHandler for consistent error responses"
```

---

## Task 13: OrderController (REST API)

**Files:**
- Create: `src/main/java/com/example/order/controller/OrderController.java`

- [ ] **Step 1: Create OrderController.java**

```java
package com.example.order.controller;

import com.example.order.dto.ApiResponse;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDTO;
import com.example.order.dto.UpdateStatusRequest;
import com.example.order.enums.OrderStatus;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDTO order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order));
    }

    @PutMapping("/{orderNo}/status")
    public ResponseEntity<ApiResponse<OrderDTO>> updateStatus(
            @PathVariable String orderNo,
            @Valid @RequestBody UpdateStatusRequest request) {
        OrderDTO order = orderService.updateOrderStatus(orderNo, request);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<ApiResponse<OrderDTO>> getOrder(@PathVariable String orderNo) {
        OrderDTO order = orderService.getOrder(orderNo);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderDTO>>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        }
        Page<OrderDTO> orders = orderService.listOrders(orderStatus,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/controller/OrderController.java
git commit -m "feat: add OrderController with CRUD REST endpoints"
```

---

## Task 14: OrderEventConsumer

**Files:**
- Create: `src/main/java/com/example/order/consumer/OrderEventConsumer.java`

- [ ] **Step 1: Create OrderEventConsumer.java**

```java
package com.example.order.consumer;

import com.example.order.entity.OrderEventLog;
import com.example.order.repository.OrderEventLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
        topic = "${rocketmq.topics.order-events}",
        consumerGroup = "${rocketmq.consumer.group}"
)
public class OrderEventConsumer implements RocketMQListener<String> {

    private final OrderEventLogRepository orderEventLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String messageBody) {
        log.debug("Received message: {}", messageBody);
        try {
            JsonNode root = objectMapper.readTree(messageBody);
            String eventId = root.get("eventId").asText();
            String eventType = root.get("eventType").asText();
            String orderNo = root.get("orderNo").asText();

            // Idempotency check
            if (orderEventLogRepository.existsByEventId(eventId)) {
                log.info("Duplicate event detected, skipping: eventId={}", eventId);
                return;
            }

            // Process the event
            processEvent(eventType, orderNo, root);

            // Record processed event
            OrderEventLog eventLog = OrderEventLog.builder()
                    .orderNo(orderNo)
                    .eventType(eventType)
                    .eventId(eventId)
                    .build();
            orderEventLogRepository.save(eventLog);

            log.info("Event processed successfully: eventId={}, eventType={}, orderNo={}", eventId, eventType, orderNo);
        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process message", e);
        }
    }

    private void processEvent(String eventType, String orderNo, JsonNode root) {
        switch (eventType) {
            case "ORDER_CREATED" -> handleOrderCreated(orderNo, root);
            case "ORDER_STATUS_CHANGED" -> handleOrderStatusChanged(orderNo, root);
            default -> log.warn("Unknown event type: {}", eventType);
        }
    }

    private void handleOrderCreated(String orderNo, JsonNode root) {
        // Placeholder for downstream processing (e.g., inventory deduction, notification)
        log.info("Handling ORDER_CREATED for order: {}", orderNo);
    }

    private void handleOrderStatusChanged(String orderNo, JsonNode root) {
        // Placeholder for downstream processing
        JsonNode data = root.get("data");
        String newStatus = data.get("newStatus").asText();
        log.info("Handling ORDER_STATUS_CHANGED for order {}: newStatus={}", orderNo, newStatus);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/order/consumer/OrderEventConsumer.java
git commit -m "feat: add OrderEventConsumer with idempotent event processing"
```

---

## Task 15: Helm Chart

**Files:**
- Create: `helm/order-processing/Chart.yaml`
- Create: `helm/order-processing/values.yaml`
- Create: `helm/order-processing/.helmignore`
- Create: `helm/order-processing/templates/deployment.yaml`
- Create: `helm/order-processing/templates/service.yaml`
- Create: `helm/order-processing/templates/configmap.yaml`
- Create: `helm/order-processing/templates/secret.yaml`

- [ ] **Step 1: Create Chart.yaml**

```yaml
apiVersion: v2
name: order-processing
description: Order Processing System with Transactional Outbox and RocketMQ
type: application
version: 1.0.0
appVersion: "1.0.0"
```

- [ ] **Step 2: Create values.yaml**

```yaml
replicaCount: 2

image:
  repository: order-processing
  tag: "1.0.0"
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 8080

resources:
  limits:
    cpu: "1000m"
    memory: "1Gi"
  requests:
    cpu: "500m"
    memory: "512Mi"

config:
  spring:
    profiles:
      active: default
  db:
    host: "mysql.database.svc.cluster.local"
    port: 3306
    name: "order_db"
    username: "root"
    # password set via secret
  rocketmq:
    nameserver: "rocketmq.middleware.svc.cluster.local:9876"
    producer:
      group: "order-producer-group"
    consumer:
      group: "order-consumer-group"
    topics:
      order-events: "order-topic"

secret:
  db:
    password: "root"
  # rocketmq: {}  # add if authentication required

ingress:
  enabled: false
  className: ""
  annotations: {}
  hosts: []
```

- [ ] **Step 3: Create .helmignore**

```yaml
# Patterns to ignore when building packages.
# This supports shell glob matching.
*.tar.gz
*.tmp
.DS_Store
.git/
.gitignore
*.md
charts/
Makefile
```

- [ ] **Step 4: Create templates/deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Chart.Name }}
  labels:
    app: {{ .Chart.Name }}
    app.kubernetes.io/name: {{ .Chart.Name }}
    app.kubernetes.io/instance: {{ .Release.Name }}
    app.kubernetes.io/version: {{ .Chart.AppVersion }}
    app.kubernetes.io/managed-by: {{ .Release.Service }}
    helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: {{ .Chart.Name }}
  template:
    metadata:
      labels:
        app: {{ .Chart.Name }}
        app.kubernetes.io/name: {{ .Chart.Name }}
        app.kubernetes.io/instance: {{ .Release.Name }}
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "{{ .Values.config.spring.profiles.active }}"
            - name: DB_HOST
              value: "{{ .Values.config.db.host }}"
            - name: DB_PORT
              value: "{{ .Values.config.db.port | quote }}"
            - name: DB_NAME
              value: "{{ .Values.config.db.name }}"
            - name: DB_USERNAME
              value: "{{ .Values.config.db.username }}"
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ .Chart.Name }}-db-secret
                  key: password
            - name: ROCKETMQ_NAMESERVER
              value: "{{ .Values.config.rocketmq.nameserver }}"
            - name: ROCKETMQ_PRODUCER_GROUP
              value: "{{ .Values.config.rocketmq.producer.group }}"
            - name: ROCKETMQ_CONSUMER_GROUP
              value: "{{ .Values.config.rocketmq.consumer.group }}"
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 10
            periodSeconds: 5
```

- [ ] **Step 5: Create templates/service.yaml**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Chart.Name }}
  labels:
    app: {{ .Chart.Name }}
    app.kubernetes.io/name: {{ .Chart.Name }}
    app.kubernetes.io/instance: {{ .Release.Name }}
    app.kubernetes.io/managed-by: {{ .Release.Service }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http
      protocol: TCP
      name: http
  selector:
    app: {{ .Chart.Name }}
```

- [ ] **Step 6: Create templates/configmap.yaml**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Chart.Name }}-config
  labels:
    app: {{ .Chart.Name }}
    app.kubernetes.io/instance: {{ .Release.Name }}
    app.kubernetes.io/managed-by: {{ .Release.Service }}
data:
  application.yml: |
    spring:
      application:
        name: order-processing-system
      datasource:
        url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
        driver-class-name: com.mysql.cj.jdbc.Driver
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
      jpa:
        hibernate:
          ddl-auto: validate
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.MySQLDialect
            format_sql: true
    server:
      port: 8080
    rocketmq:
      name-server: ${ROCKETMQ_NAMESERVER}
      producer:
        group: ${ROCKETMQ_PRODUCER_GROUP}
        send-timeout: 3000
        retry-times-when-send-failed: 2
      consumer:
        group: ${ROCKETMQ_CONSUMER_GROUP}
      topics:
        order-events: order-topic
    logging:
      level:
        com.example.order: DEBUG
```

- [ ] **Step 7: Create templates/secret.yaml**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ .Chart.Name }}-db-secret
  labels:
    app: {{ .Chart.Name }}
    app.kubernetes.io/instance: {{ .Release.Name }}
    app.kubernetes.io/managed-by: {{ .Release.Service }}
type: Opaque
stringData:
  password: {{ .Values.secret.db.password | quote }}
```

- [ ] **Step 8: Commit**

```bash
git add helm/order-processing/Chart.yaml \
        helm/order-processing/values.yaml \
        helm/order-processing/.helmignore \
        helm/order-processing/templates/deployment.yaml \
        helm/order-processing/templates/service.yaml \
        helm/order-processing/templates/configmap.yaml \
        helm/order-processing/templates/secret.yaml
git commit -m "feat: add Helm chart for Kubernetes deployment"
```

---

## Task 16: Unit Tests

**Files:**
- Create: `src/test/java/com/example/order/service/OrderServiceTest.java`
- Create: `src/test/java/com/example/order/consumer/OrderEventConsumerTest.java`
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: Create application-test.yml**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  autoconfigure:
    exclude:
      - org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration

rocketmq:
  name-server: localhost:9876
  producer:
    group: order-producer-group
    send-timeout: 3000
    retry-times-when-send-failed: 2
  consumer:
    group: order-consumer-group
  topics:
    order-events: order-topic
```

- [ ] **Step 2: Create OrderServiceTest.java**

```java
package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDTO;
import com.example.order.dto.UpdateStatusRequest;
import com.example.order.entity.Order;
import com.example.order.entity.OrderOutbox;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.ServiceException;
import com.example.order.repository.OrderOutboxRepository;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    private OrderService orderService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        orderService = new OrderService(
                orderRepository,
                orderOutboxRepository,
                rocketMQTemplate,
                orderOutboxPublisher,
                objectMapper
        );
        ReflectionTestUtils.setField(orderService, "orderTopic", "order-topic");
    }

    @Test
    void createOrder_shouldPersistOrderAndOutbox() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNo("ORD001")
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.existsByOrderNo("ORD001")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderOutboxRepository.save(any(OrderOutbox.class))).thenAnswer(invocation -> {
            OrderOutbox o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        // Simulate transaction synchronization being active
        TransactionSynchronizationManager.initSynchronization();

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals("ORD001", result.getOrderNo());
        assertEquals(OrderStatus.CREATED, result.getStatus());
        assertEquals(new BigDecimal("99.99"), result.getAmount());

        verify(orderRepository).save(any(Order.class));
        verify(orderOutboxRepository).save(any(OrderOutbox.class));

        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void createOrder_shouldThrowConflict_whenOrderExists() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNo("ORD001")
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.existsByOrderNo("ORD001")).thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.createOrder(request));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("ORD001"));
    }

    @Test
    void updateOrderStatus_shouldTransitionCorrectly() {
        Order existingOrder = Order.builder()
                .id(1L)
                .orderNo("ORD001")
                .status(OrderStatus.CREATED)
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.findByOrderNo("ORD001")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);
        when(orderOutboxRepository.save(any(OrderOutbox.class))).thenAnswer(inv -> {
            OrderOutbox o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        TransactionSynchronizationManager.initSynchronization();

        UpdateStatusRequest request = new UpdateStatusRequest("PAID");
        OrderDTO result = orderService.updateOrderStatus("ORD001", request);

        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());

        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void updateOrderStatus_shouldRejectInvalidTransition() {
        Order existingOrder = Order.builder()
                .id(1L)
                .orderNo("ORD001")
                .status(OrderStatus.COMPLETED)
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.findByOrderNo("ORD001")).thenReturn(Optional.of(existingOrder));

        UpdateStatusRequest request = new UpdateStatusRequest("PAID");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.updateOrderStatus("ORD001", request));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("Invalid status transition"));
    }

    @Test
    void getOrder_shouldReturnOrder_whenExists() {
        Order order = Order.builder()
                .id(1L)
                .orderNo("ORD001")
                .status(OrderStatus.CREATED)
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.findByOrderNo("ORD001")).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrder("ORD001");

        assertNotNull(result);
        assertEquals("ORD001", result.getOrderNo());
    }

    @Test
    void getOrder_shouldThrowNotFound_whenNotExists() {
        when(orderRepository.findByOrderNo("ORD999")).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.getOrder("ORD999"));

        assertEquals(404, exception.getCode());
    }
}
```

- [ ] **Step 3: Create OrderEventConsumerTest.java**

```java
package com.example.order.consumer;

import com.example.order.entity.OrderEventLog;
import com.example.order.repository.OrderEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private OrderEventLogRepository orderEventLogRepository;

    private OrderEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new OrderEventConsumer(orderEventLogRepository, objectMapper);
    }

    @Test
    void onMessage_shouldProcessOrderCreatedEvent() {
        String eventId = "test-event-001";
        String message = String.format("""
            {
                "eventId": "%s",
                "eventType": "ORDER_CREATED",
                "orderNo": "ORD001",
                "timestamp": "2026-04-13T10:00:00",
                "data": {"orderNo": "ORD001", "amount": 99.99, "status": "CREATED"}
            }
            """, eventId);

        when(orderEventLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(orderEventLogRepository.save(any(OrderEventLog.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.onMessage(message);

        ArgumentCaptor<OrderEventLog> captor = ArgumentCaptor.forClass(OrderEventLog.class);
        verify(orderEventLogRepository).save(captor.capture());

        OrderEventLog saved = captor.getValue();
        assertEquals("ORD001", saved.getOrderNo());
        assertEquals("ORDER_CREATED", saved.getEventType());
        assertEquals(eventId, saved.getEventId());
    }

    @Test
    void onMessage_shouldSkipDuplicateEvent() {
        String eventId = "duplicate-event-001";
        String message = String.format("""
            {
                "eventId": "%s",
                "eventType": "ORDER_CREATED",
                "orderNo": "ORD001",
                "timestamp": "2026-04-13T10:00:00",
                "data": {}
            }
            """, eventId);

        when(orderEventLogRepository.existsByEventId(eventId)).thenReturn(true);

        consumer.onMessage(message);

        verify(orderEventLogRepository, never()).save(any());
    }

    @Test
    void onMessage_shouldProcessStatusChangedEvent() {
        String eventId = "test-event-002";
        String message = String.format("""
            {
                "eventId": "%s",
                "eventType": "ORDER_STATUS_CHANGED",
                "orderNo": "ORD001",
                "timestamp": "2026-04-13T10:00:00",
                "data": {"orderNo": "ORD001", "oldStatus": "CREATED", "newStatus": "PAID"}
            }
            """, eventId);

        when(orderEventLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(orderEventLogRepository.save(any(OrderEventLog.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.onMessage(message);

        ArgumentCaptor<OrderEventLog> captor = ArgumentCaptor.forClass(OrderEventLog.class);
        verify(orderEventLogRepository).save(captor.capture());

        OrderEventLog saved = captor.getValue();
        assertEquals("ORD001", saved.getOrderNo());
        assertEquals("ORDER_STATUS_CHANGED", saved.getEventType());
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/example/order/service/OrderServiceTest.java \
        src/test/java/com/example/order/consumer/OrderEventConsumerTest.java \
        src/test/resources/application-test.yml
git commit -m "test: add OrderServiceTest and OrderEventConsumerTest"
```

---

## Task 17: Build and Verify

- [ ] **Step 1: Verify project compiles**

Run: `cd /Users/guochao/IdeaProjects/order_rabbitmq/order-processing-system && mvn compile -q`

Expected: BUILD SUCCESS (no output on -q)

- [ ] **Step 2: Run unit tests**

Run: `mvn test -q`

Expected: All tests pass, BUILD SUCCESS

- [ ] **Step 3: Commit build verification**

```bash
git add -A && git commit -m "chore: verify project compiles and tests pass"
```

---

## Spec Coverage Checklist

| Spec Section | Task(s) |
|---|---|
| Architecture / Components | Tasks 1-3, 8, 9 |
| Transactional Outbox Pattern | Tasks 9, 10, 11 |
| DB Schema (orders, outbox, event_log) | Task 4 |
| API Endpoints (CRUD) | Task 13 |
| RocketMQ Configuration | Tasks 8, 9, 11, 12 |
| Consumer + Idempotency | Task 14 |
| Outbox Retry Scheduler | Task 11 |
| Error Handling | Task 12 |
| Helm Deployment | Task 15 |
| Unit Tests | Task 16 |

All spec acceptance criteria covered. No placeholder tasks.
