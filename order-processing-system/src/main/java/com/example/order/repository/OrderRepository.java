package com.example.order.repository;

import com.example.order.entity.Order;
import com.example.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    boolean existsByOrderNo(String orderNo);
}
