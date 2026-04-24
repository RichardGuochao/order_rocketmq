package com.example.order.repository;

import com.example.order.entity.OrderEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderEventLogRepository extends JpaRepository<OrderEventLog, Long> {

    boolean existsByEventId(String eventId);
}
