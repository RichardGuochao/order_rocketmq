package com.example.order.controller;

import com.example.order.dto.ApiResponse;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDTO;
import com.example.order.dto.UpdateStatusRequest;
import com.example.order.enums.OrderStatus;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDTO order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order));
    }

    @PutMapping("/{orderNo}/status")
    public ResponseEntity<ApiResponse<OrderDTO>> updateStatus(
            @PathVariable String orderNo,
            @Valid @RequestBody UpdateStatusRequest request) {
        OrderDTO order = orderService.updateOrderStatus(orderNo, request);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<ApiResponse<OrderDTO>> getOrder(@PathVariable String orderNo) {
        OrderDTO order = orderService.getOrder(orderNo);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderDTO>>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        }
        Page<OrderDTO> orders = orderService.listOrders(orderStatus,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
