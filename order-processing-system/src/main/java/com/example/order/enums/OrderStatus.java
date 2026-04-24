package com.example.order.enums;

public enum OrderStatus {
    CREATED,
    PAID,
    PROCESSING,
    SHIPPED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return false;
        }
        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED;
            case PAID -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
