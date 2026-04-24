package com.example.order.dto;

import com.example.order.entity.Order;
import com.example.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;
    private String orderNo;
    private OrderStatus status;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderDTO fromEntity(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .amount(order.getAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
