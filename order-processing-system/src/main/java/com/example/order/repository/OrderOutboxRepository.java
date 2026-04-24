package com.example.order.repository;

import com.example.order.entity.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {

    @Query("SELECT o FROM OrderOutbox o WHERE o.status IN :statuses AND o.createdAt < :threshold ORDER BY o.createdAt ASC")
    List<OrderOutbox> findPendingOutboxes(
            @Param("statuses") List<String> statuses,
            @Param("threshold") LocalDateTime threshold
    );

    @Modifying
    @Query("UPDATE OrderOutbox o SET o.status = :status, o.publishedAt = :publishedAt WHERE o.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("publishedAt") LocalDateTime publishedAt);
}
