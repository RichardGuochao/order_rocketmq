package com.example.order.service;

import com.example.order.entity.OrderOutbox;
import com.example.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPublisher implements TransactionListener {

    private final OrderOutboxRepository orderOutboxRepository;

    @Override
    @Transactional
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        OrderOutbox outbox = (OrderOutbox) arg;
        try {
            // Find and update the outbox entity
            OrderOutbox existing = orderOutboxRepository.findById(outbox.getId()).orElse(null);
            if (existing != null) {
                existing.setStatus(OrderOutbox.STATUS_COMMITTED);
                existing.setPublishedAt(LocalDateTime.now());
                orderOutboxRepository.save(existing);
            }
            log.debug("Outbox {} committed locally via save", outbox.getId());
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            log.error("Failed to commit outbox {} locally: {}", outbox.getId(), e.getMessage());
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String keys = msg.getKeys();
        log.warn("checkLocalTransaction called for message key: {} - returning UNKNOW to retry", keys);
        return LocalTransactionState.UNKNOW;
    }

    @Transactional
    public void markFailed(Long outboxId) {
        try {
            OrderOutbox existing = orderOutboxRepository.findById(outboxId).orElse(null);
            if (existing != null) {
                existing.setStatus(OrderOutbox.STATUS_FAILED);
                orderOutboxRepository.save(existing);
            }
        } catch (Exception e) {
            log.error("Failed to mark outbox {} as failed: {}", outboxId, e.getMessage());
        }
    }
}
