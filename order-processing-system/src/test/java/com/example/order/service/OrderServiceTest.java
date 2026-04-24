package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDTO;
import com.example.order.dto.UpdateStatusRequest;
import com.example.order.entity.Order;
import com.example.order.entity.OrderOutbox;
import com.example.order.enums.OrderStatus;
import com.example.order.exception.ServiceException;
import com.example.order.repository.OrderOutboxRepository;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    private OrderService orderService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        orderService = new OrderService(
                orderRepository,
                orderOutboxRepository,
                rocketMQTemplate,
                orderOutboxPublisher,
                objectMapper
        );
        ReflectionTestUtils.setField(orderService, "orderTopic", "order-topic");
    }

    @Test
    void createOrder_shouldPersistOrderAndOutbox() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNo("ORD001")
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.existsByOrderNo("ORD001")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderOutboxRepository.save(any(OrderOutbox.class))).thenAnswer(invocation -> {
            OrderOutbox o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        TransactionSynchronizationManager.initSynchronization();

        OrderDTO result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals("ORD001", result.getOrderNo());
        assertEquals(OrderStatus.CREATED, result.getStatus());
        assertEquals(new BigDecimal("99.99"), result.getAmount());

        verify(orderRepository).save(any(Order.class));
        verify(orderOutboxRepository).save(any(OrderOutbox.class));

        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void createOrder_shouldThrowConflict_whenOrderExists() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNo("ORD001")
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.existsByOrderNo("ORD001")).thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.createOrder(request));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("ORD001"));
    }

    @Test
    void updateOrderStatus_shouldTransitionCorrectly() {
        Order existingOrder = Order.builder()
                .id(1L)
                .orderNo("ORD001")
                .status(OrderStatus.CREATED)
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.findByOrderNo("ORD001")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);
        when(orderOutboxRepository.save(any(OrderOutbox.class))).thenAnswer(inv -> {
            OrderOutbox o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        TransactionSynchronizationManager.initSynchronization();

        UpdateStatusRequest request = new UpdateStatusRequest("PAID");
        OrderDTO result = orderService.updateOrderStatus("ORD001", request);

        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());

        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void updateOrderStatus_shouldRejectInvalidTransition() {
        Order existingOrder = Order.builder()
                .id(1L)
                .orderNo("ORD001")
                .status(OrderStatus.COMPLETED)
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.findByOrderNo("ORD001")).thenReturn(Optional.of(existingOrder));

        UpdateStatusRequest request = new UpdateStatusRequest("PAID");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.updateOrderStatus("ORD001", request));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("Invalid status transition"));
    }

    @Test
    void getOrder_shouldReturnOrder_whenExists() {
        Order order = Order.builder()
                .id(1L)
                .orderNo("ORD001")
                .status(OrderStatus.CREATED)
                .amount(new BigDecimal("99.99"))
                .build();

        when(orderRepository.findByOrderNo("ORD001")).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrder("ORD001");

        assertNotNull(result);
        assertEquals("ORD001", result.getOrderNo());
    }

    @Test
    void getOrder_shouldThrowNotFound_whenNotExists() {
        when(orderRepository.findByOrderNo("ORD999")).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> orderService.getOrder("ORD999"));

        assertEquals(404, exception.getCode());
    }
}
