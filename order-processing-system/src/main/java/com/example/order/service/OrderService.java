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
import org.springframework.beans.factory.annotation.Value;
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

        String payload = buildEventPayload(order.getOrderNo(), OrderOutbox.EVENT_ORDER_CREATED,
                Map.of("orderNo", order.getOrderNo(),
                        "amount", order.getAmount().toString(),
                        "status", order.getStatus().name()));

        OrderOutbox outbox = OrderOutbox.builder()
                .aggregateType("Order")
                .aggregateId(order.getOrderNo())
                .eventType(OrderOutbox.EVENT_ORDER_CREATED)
                .payload(payload)
                .status(OrderOutbox.STATUS_PENDING)
                .build();
        outbox = orderOutboxRepository.save(outbox);

        final OrderOutbox finalOutbox = outbox;
        final String orderNoForEvent = order.getOrderNo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendTransactionMessage(finalOutbox, orderNoForEvent, "ORDER_CREATED");
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

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order = orderRepository.save(order);

        String payload = buildEventPayload(order.getOrderNo(), OrderOutbox.EVENT_ORDER_STATUS_CHANGED,
                Map.of("orderNo", order.getOrderNo(),
                        "oldStatus", oldStatus.name(),
                        "newStatus", newStatus.name(),
                        "updatedAt", LocalDateTime.now().toString()));

        OrderOutbox outbox = OrderOutbox.builder()
                .aggregateType("Order")
                .aggregateId(order.getOrderNo())
                .eventType(OrderOutbox.EVENT_ORDER_STATUS_CHANGED)
                .payload(payload)
                .status(OrderOutbox.STATUS_PENDING)
                .build();
        outbox = orderOutboxRepository.save(outbox);

        final OrderOutbox finalOutbox = outbox;
        final String orderNoForEvent = order.getOrderNo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendTransactionMessage(finalOutbox, orderNoForEvent, "ORDER_STATUS_CHANGED");
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
        org.apache.rocketmq.common.message.Message message =
                new org.apache.rocketmq.common.message.Message(
                        orderTopic,
                        tag,
                        outbox.getId().toString(),
                        outbox.getPayload().getBytes()
                );
        try {
            rocketMQTemplate.getProducer().sendMessageInTransaction(message, outbox);
            log.debug("Transaction message sent for outbox {}: orderNo={}", outbox.getId(), orderNo);
        } catch (Exception e) {
            log.error("Failed to send transaction message for outbox {}: {}", outbox.getId(), e.getMessage());
            orderOutboxPublisher.markFailed(outbox.getId());
        }
    }

    private String buildEventPayload(String orderNo, String eventType, Map<String, String> data) {
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
