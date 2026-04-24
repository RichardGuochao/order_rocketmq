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
