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
                "data": {"orderNo": "ORD001", "amount": "99.99", "status": "CREATED"}
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
