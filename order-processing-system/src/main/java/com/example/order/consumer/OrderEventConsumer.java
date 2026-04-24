package com.example.order.consumer;

import com.example.order.entity.OrderEventLog;
import com.example.order.repository.OrderEventLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Slf4j
// @Profile("!consumer")
@RocketMQMessageListener(
       topic = "${rocketmq.topics.order-events}",
       consumerGroup = "${rocketmq.consumer.group}"
)
public class OrderEventConsumer implements RocketMQListener<String> {

    private final OrderEventLogRepository orderEventLogRepository;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderEventLogRepository orderEventLogRepository, ObjectMapper objectMapper) {
        this.orderEventLogRepository = orderEventLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String messageBody) {
        log.debug("Received message: {}", messageBody);
        try {
            JsonNode root = objectMapper.readTree(messageBody);
            String eventId = root.get("eventId").asText();
            String eventType = root.get("eventType").asText();
            String orderNo = root.get("orderNo").asText();

            if (orderEventLogRepository.existsByEventId(eventId)) {
                log.info("Duplicate event detected, skipping: eventId={}", eventId);
                return;
            }

            processEvent(eventType, orderNo, root);

            OrderEventLog eventLog = OrderEventLog.builder()
                    .orderNo(orderNo)
                    .eventType(eventType)
                    .eventId(eventId)
                    .build();
            orderEventLogRepository.save(eventLog);

            log.info("Event processed: eventId={}, eventType={}, orderNo={}", eventId, eventType, orderNo);
        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process message", e);
        }
    }

    private void processEvent(String eventType, String orderNo, JsonNode root) {
        switch (eventType) {
            case "ORDER_CREATED" -> handleOrderCreated(orderNo, root);
            case "ORDER_PAID" -> handleOrderPaid(orderNo, root);
            case "ORDER_PROCESSING" -> handleOrderProcessing(orderNo, root);
            case "ORDER_SHIPPED" -> handleOrderShipped(orderNo, root);
            case "ORDER_COMPLETED" -> handleOrderCompleted(orderNo, root);
            case "ORDER_CANCELLED" -> handleOrderCancelled(orderNo, root);
            default -> log.warn("Unknown event type: {}", eventType);
        }
    }

    private void handleOrderCreated(String orderNo, JsonNode root) {
        JsonNode data = root.get("data");
        log.info("Order created: orderNo={}, amount={}", orderNo, data.get("amount").asText());
    }

    private void handleOrderPaid(String orderNo, JsonNode root) {
        JsonNode data = root.get("data");
        log.info("Order paid: orderNo={}, transition={}", orderNo, data.get("transition").asText());
    }

    private void handleOrderProcessing(String orderNo, JsonNode root) {
        JsonNode data = root.get("data");
        log.info("Order processing: orderNo={}, transition={}", orderNo, data.get("transition").asText());
    }

    private void handleOrderShipped(String orderNo, JsonNode root) {
        JsonNode data = root.get("data");
        log.info("Order shipped: orderNo={}, transition={}", orderNo, data.get("transition").asText());
    }

    private void handleOrderCompleted(String orderNo, JsonNode root) {
        JsonNode data = root.get("data");
        log.info("Order completed: orderNo={}, transition={}", orderNo, data.get("transition").asText());
    }

    private void handleOrderCancelled(String orderNo, JsonNode root) {
        JsonNode data = root.get("data");
        log.info("Order cancelled: orderNo={}, transition={}", orderNo, data.get("transition").asText());
    }
}
