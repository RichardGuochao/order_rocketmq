package com.example.order.entity;

import com.example.order.enums.OrderStatus;
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
    // Specific status change events - each transition has a distinct event type
    public static final String EVENT_ORDER_PAID = "ORDER_PAID";
    public static final String EVENT_ORDER_PROCESSING = "ORDER_PROCESSING";
    public static final String EVENT_ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String EVENT_ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";

    public static String getStatusEventType(OrderStatus status) {
        return switch (status) {
            case CREATED -> EVENT_ORDER_CREATED;
            case PAID -> EVENT_ORDER_PAID;
            case PROCESSING -> EVENT_ORDER_PROCESSING;
            case SHIPPED -> EVENT_ORDER_SHIPPED;
            case COMPLETED -> EVENT_ORDER_COMPLETED;
            case CANCELLED -> EVENT_ORDER_CANCELLED;
        };
    }

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
