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
