package com.example.order.exception;

import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {

    private final int code;
    private final String message;

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public static ServiceException notFound(String message) {
        return new ServiceException(404, message);
    }

    public static ServiceException badRequest(String message) {
        return new ServiceException(400, message);
    }

    public static ServiceException conflict(String message) {
        return new ServiceException(409, message);
    }

    public static ServiceException internal(String message) {
        return new ServiceException(500, message);
    }
}
