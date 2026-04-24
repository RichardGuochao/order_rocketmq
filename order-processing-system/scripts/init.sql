-- Order Processing System Database Schema

CREATE DATABASE IF NOT EXISTS `order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `order`;

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
