package com.example.order.service;

import com.example.order.entity.OrderOutbox;
import com.example.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

                org.apache.rocketmq.common.message.Message message =
                        new org.apache.rocketmq.common.message.Message(
                                orderTopic,
                                tag,
                                UUID.randomUUID().toString(),
                                outbox.getPayload().getBytes()
                        );

                rocketMQTemplate.getProducer().send(message, 3000);
                // Use save() instead of updateStatus() to avoid @Modifying transaction issues
                OrderOutbox toUpdate = orderOutboxRepository.findById(outbox.getId()).orElse(null);
                if (toUpdate != null) {
                    toUpdate.setStatus(OrderOutbox.STATUS_PUBLISHED);
                    toUpdate.setPublishedAt(LocalDateTime.now());
                    orderOutboxRepository.save(toUpdate);
                }
                log.debug("Outbox {} republished successfully", outbox.getId());
            } catch (Exception e) {
                log.error("Failed to republish outbox {}: {}", outbox.getId(), e.getMessage());
            }
        }
    }
}
